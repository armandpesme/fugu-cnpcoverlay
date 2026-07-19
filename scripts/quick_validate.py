#!/usr/bin/env python3
"""Fast local validation for this Forge workspace.

No external dependency. Default mode avoids Gradle so it stays quick.
Use --gradle-test or --gradle-build when compile verification is needed.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

try:
    import tomllib
except ModuleNotFoundError:  # Python < 3.11
    tomllib = None


IGNORED_DIRS = {
    ".git",
    ".gradle",
    ".tmp",
    "build",
    "node_modules",
    "run",
}

OBSOLETE_REFERENCES = (
    "re" + "nemy",
    "back-" + "up-github",
    "end-" + "off.md",
    "HAND" + "OFF.md",
)

PSEUDO_AGENT_FIELDS = {
    "invoke_" + "when",
    "allowed_" + "skills",
    "allowed_" + "tools",
    "can_" + "edit",
    "reasoning_" + "effort",
}

WORKSPACE_MODELS = {
    "gpt-5.6-sol",
    "gpt-5.6-terra",
    "gpt-5.6-luna",
}

CRITICAL_PATHS = (
    "AGENTS.md",
    "README.md",
    "PLANS.md",
    ".codex/config.toml",
    ".vscode/settings.json",
    "docs/GITNEXUS_SETUP.md",
    "docs/runtime-paths.md",
    "scripts/quick_validate.py",
)


class Report:
    def __init__(self) -> None:
        self.errors = 0
        self.warnings = 0

    def ok(self, message: str) -> None:
        print(f"OK    {message}")

    def warn(self, message: str) -> None:
        self.warnings += 1
        print(f"WARN  {message}")

    def fail(self, message: str) -> None:
        self.errors += 1
        print(f"FAIL  {message}")


def run_command(args: list[str], cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        cwd=str(cwd),
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        shell=False,
    )


def iter_files(root: Path, suffixes: tuple[str, ...]) -> list[Path]:
    if not root.exists():
        return []

    files: list[Path] = []
    for current_root, dirs, names in os.walk(root):
        dirs[:] = [name for name in dirs if name not in IGNORED_DIRS]
        base = Path(current_root)
        for name in names:
            path = base / name
            if path.suffix.lower() in suffixes:
                files.append(path)
    return files


def check_required_paths(report: Report, root: Path) -> None:
    required = [
        "AGENTS.md",
        "README.md",
        "PLANS.md",
        ".codex/config.toml",
        ".codex/agents",
        ".agents/skills",
        "docs/GITNEXUS_SETUP.md",
        "docs/runtime-paths.md",
        "scripts/quick_validate.py",
        "project-gradle/build.gradle",
        "project-gradle/gradlew.bat",
        "project-gradle/settings.gradle",
        "project-gradle/src/main/resources/META-INF/mods.toml",
    ]

    for relative in required:
        path = root / relative
        if path.exists():
            report.ok(f"present: {relative}")
        else:
            report.fail(f"missing: {relative}")


def parse_skill_metadata(path: Path) -> dict[str, str]:
    text = path.read_text(encoding="utf-8-sig")
    if not text.startswith("---"):
        return {}

    parts = text.split("---", 2)
    if len(parts) < 3:
        return {}

    metadata: dict[str, str] = {}
    for line in parts[1].splitlines():
        if not line or line.startswith(" ") or ":" not in line:
            continue
        key, value = line.split(":", 1)
        key = key.strip()
        raw_value = value.strip()
        if (
            key in {"name", "description"}
            and raw_value
            and not raw_value.startswith(("\"", "'", ">", "|"))
            and ": " in raw_value
        ):
            metadata["__error__"] = f"unquoted colon in {key}"
        metadata[key] = raw_value.strip('"').strip("'")
    return metadata


def extract_route_targets(text: str) -> set[str]:
    targets: set[str] = set()
    for line in text.splitlines():
        lower = line.lower()
        if "description =" in lower or not re.search(r"\b(?:route|router)\b", lower):
            continue
        targets.update(
            match.lower()
            for match in re.findall(r"\b(?:to|vers)\s+`?([a-z][a-z0-9-]*)`?", lower)
        )
    return targets


def check_removed_references(report: Report, root: Path) -> None:
    roots = [
        root / ".codex",
        root / ".agents",
        root / "docs",
        root / "scripts",
    ]
    files = [root / "AGENTS.md", root / "README.md", root / "PLANS.md"]
    for check_root in roots:
        files.extend(iter_files(check_root, (".md", ".toml", ".json", ".yaml", ".yml", ".py")))

    found = 0
    validator = Path(__file__).resolve()
    for path in sorted(set(files)):
        if not path.is_file() or path.resolve() == validator:
            continue
        try:
            text = path.read_text(encoding="utf-8-sig")
        except UnicodeDecodeError:
            continue
        for obsolete in OBSOLETE_REFERENCES:
            if obsolete.lower() in text.lower():
                report.fail(f"obsolete reference `{obsolete}`: {path.relative_to(root)}")
                found += 1

    if found == 0:
        report.ok("no obsolete Codex concepts referenced")


def check_git_critical_paths(report: Report, root: Path) -> None:
    if not (root / ".git").exists() or not shutil.which("git"):
        report.warn("Git critical-path checks skipped")
        return

    paths = list(CRITICAL_PATHS)
    paths.extend(
        path.relative_to(root).as_posix()
        for path in sorted((root / ".codex" / "agents").glob("*.toml"))
    )
    paths.extend(
        path.relative_to(root).as_posix()
        for path in sorted((root / ".agents" / "skills").glob("*/SKILL.md"))
    )

    failures_before = report.errors
    for relative in paths:
        path = root / relative
        if not path.exists():
            continue

        ignored = run_command(["git", "check-ignore", "--no-index", "-q", "--", relative], root)
        if ignored.returncode == 0:
            report.fail(f"critical file ignored by Git: {relative}")

        tracked = run_command(["git", "ls-files", "--error-unmatch", "--", relative], root)
        if tracked.returncode != 0:
            report.fail(f"critical file not tracked by Git: {relative}")

    if report.errors == failures_before:
        report.ok(f"Git critical files tracked and not ignored: {len(paths)}")


def validate_codex_stack(report: Report, root: Path) -> None:
    if tomllib is None:
        report.warn("Codex TOML parse skipped; Python 3.11+ required")
        return

    config = root / ".codex" / "config.toml"
    if not config.exists():
        report.fail("missing: .codex/config.toml")
    else:
        try:
            config_data = tomllib.loads(config.read_text(encoding="utf-8-sig"))
            report.ok("Codex config parsed: .codex/config.toml")
            for name, server in sorted(config_data.get("mcp_servers", {}).items()):
                command = server.get("command")
                if command and shutil.which(command):
                    report.ok(f"MCP command on PATH: {name} -> {command}")
                elif command:
                    report.warn(f"MCP command not found on PATH: {name} -> {command}")
        except Exception as exc:
            report.fail(f"invalid Codex config: .codex/config.toml: {exc}")

    skill_root = root / ".agents" / "skills"
    skills_by_dir: set[str] = set()
    skills_by_name: dict[str, Path] = {}
    if not skill_root.exists():
        report.fail("missing: .agents/skills")
    else:
        for directory in sorted(path for path in skill_root.iterdir() if path.is_dir()):
            skill_file = directory / "SKILL.md"
            if not skill_file.exists():
                if any(directory.iterdir()):
                    report.fail(f"missing SKILL.md: {directory.relative_to(root)}")
                continue

            skills_by_dir.add(directory.name)

            metadata = parse_skill_metadata(skill_file)
            name = metadata.get("name")
            description = metadata.get("description")
            if metadata.get("__error__"):
                report.fail(
                    f"invalid skill frontmatter ({metadata['__error__']}): "
                    f"{skill_file.relative_to(root)}"
                )
                continue
            if not name or not description:
                report.fail(f"skill missing name/description: {skill_file.relative_to(root)}")
                continue

            if len(name) > 64 or not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", name):
                report.fail(f"invalid skill name `{name}`: {skill_file.relative_to(root)}")
                continue

            if name in skills_by_name:
                first = skills_by_name[name].relative_to(root)
                report.fail(f"duplicate skill name `{name}`: {first} and {skill_file.relative_to(root)}")
            else:
                skills_by_name[name] = skill_file

        report.ok(f"Codex repo skills checked: {len(skills_by_dir)}")

    agent_root = root / ".codex" / "agents"
    if not agent_root.exists():
        report.fail("missing: .codex/agents")
    else:
        agents_by_name: dict[str, Path] = {}
        agent_data: dict[Path, dict[str, object]] = {}
        for agent_file in sorted(agent_root.glob("*.toml")):
            try:
                data = tomllib.loads(agent_file.read_text(encoding="utf-8-sig"))
            except Exception as exc:
                report.fail(f"invalid agent TOML: {agent_file.relative_to(root)}: {exc}")
                continue

            agent_data[agent_file] = data
            missing = [
                field
                for field in ("name", "description", "developer_instructions")
                if not isinstance(data.get(field), str) or not str(data.get(field)).strip()
            ]
            if missing:
                report.fail(
                    f"agent missing {', '.join(missing)}: {agent_file.relative_to(root)}"
                )

            name = data.get("name")
            if isinstance(name, str) and name:
                if name in agents_by_name:
                    first = agents_by_name[name].relative_to(root)
                    report.fail(
                        f"duplicate agent name `{name}`: {first} and {agent_file.relative_to(root)}"
                    )
                else:
                    agents_by_name[name] = agent_file

            pseudo = sorted(PSEUDO_AGENT_FIELDS.intersection(data))
            if pseudo:
                report.fail(
                    f"agent uses obsolete pseudo-field(s) {', '.join(pseudo)}: "
                    f"{agent_file.relative_to(root)}"
                )

            model = data.get("model")
            if model not in WORKSPACE_MODELS:
                report.fail(
                    f"agent model outside workspace GPT-5.6 matrix `{model}`: "
                    f"{agent_file.relative_to(root)}"
                )

        active_agents = set(agents_by_name)
        agents_doc = root / "AGENTS.md"
        announced_agents: set[str] = set()
        if agents_doc.exists():
            for line in agents_doc.read_text(encoding="utf-8-sig").splitlines():
                if line.startswith("Agents actifs:"):
                    announced_agents.update(re.findall(r"`([a-z][a-z0-9-]*)`", line))
                    break
        if announced_agents != active_agents:
            report.fail(
                "AGENTS.md active-agent list mismatch: "
                f"files-only={sorted(active_agents - announced_agents) or 'none'}, "
                f"docs-only={sorted(announced_agents - active_agents) or 'none'}"
            )
        else:
            report.ok(f"AGENTS.md agent list aligned: {len(active_agents)} agents")

        maestro_files = [
            agent_root / "maestro.toml",
            skill_root / "maestro" / "SKILL.md",
        ]
        route_sets: list[tuple[Path, set[str]]] = []
        for path in maestro_files:
            if not path.exists():
                report.fail(f"missing maestro source: {path.relative_to(root)}")
                continue
            targets = extract_route_targets(path.read_text(encoding="utf-8-sig"))
            route_sets.append((path, targets))
            for target in sorted(targets - active_agents):
                report.fail(
                    f"maestro references missing agent `{target}`: {path.relative_to(root)}"
                )

        if len(route_sets) == 2 and route_sets[0][1] != route_sets[1][1]:
            left_only = sorted(route_sets[0][1] - route_sets[1][1])
            right_only = sorted(route_sets[1][1] - route_sets[0][1])
            report.fail(
                "maestro routing mismatch: "
                f"agent-only={left_only or 'none'}, skill-only={right_only or 'none'}"
            )
        elif len(route_sets) == 2:
            report.ok(f"maestro routing aligned: {len(route_sets[0][1])} targets")
            expected_targets = active_agents - {"maestro"}
            if route_sets[0][1] != expected_targets:
                report.fail(
                    "maestro does not route every active specialist: "
                    f"missing={sorted(expected_targets - route_sets[0][1]) or 'none'}"
                )

        report.ok(f"Codex agents checked: {len(agent_data)}")

    check_removed_references(report, root)
    check_git_critical_paths(report, root)


def check_python(report: Report) -> None:
    version = sys.version.split()[0]
    report.ok(f"python executable: {sys.executable}")
    report.ok(f"python version: {version}")

    python_on_path = shutil.which("python")
    py_launcher = shutil.which("py")

    if python_on_path:
        report.ok(f"python on PATH: {python_on_path}")
        if "WindowsApps" in python_on_path:
            report.warn("python PATH points to WindowsApps alias")
    else:
        report.warn("python not found on PATH")

    if py_launcher:
        report.ok(f"py launcher: {py_launcher}")
    else:
        report.warn("py launcher not found on PATH")


def check_java(report: Report, root: Path) -> None:
    java = shutil.which("java")
    if not java:
        report.fail("java not found on PATH")
        return

    result = run_command([java, "-version"], root)
    text = "\n".join(part for part in [result.stderr, result.stdout] if part).strip()
    first_line = text.splitlines()[0] if text else "unknown version"
    report.ok(f"java: {first_line}")

    if '"17' not in first_line and " 17" not in first_line:
        report.warn("expected Java 17 for Forge 1.20.1")


def check_gradle_wrapper(report: Report, root: Path, mode: str | None) -> None:
    project = root / "project-gradle"
    wrapper = project / "gradlew.bat"
    if not wrapper.exists():
        report.fail("Gradle wrapper missing: project-gradle/gradlew.bat")
        return

    report.ok("Gradle wrapper present")

    if not mode:
        report.warn("Gradle not run; pass --gradle-test or --gradle-build")
        return

    task = "test" if mode == "test" else "build"
    result = run_command([str(wrapper), task], project)
    if result.returncode == 0:
        report.ok(f"Gradle {task} passed")
    else:
        report.fail(f"Gradle {task} failed with exit code {result.returncode}")
        if result.stderr.strip():
            print(result.stderr.strip())
        if result.stdout.strip():
            print(result.stdout.strip())


def validate_json(report: Report, root: Path) -> None:
    roots = [
        root / "project-gradle" / "src" / "main" / "resources",
        root / "datapacks",
        root / "resourcepacks",
    ]

    files: list[Path] = []
    for check_root in roots:
        files.extend(iter_files(check_root, (".json", ".mcmeta")))

    for path in files:
        relative = path.relative_to(root)
        try:
            with path.open("r", encoding="utf-8-sig") as handle:
                json.load(handle)
        except Exception as exc:
            report.fail(f"invalid JSON: {relative}: {exc}")

    if files:
        report.ok(f"JSON/MCMeta parsed: {len(files)} files")
    else:
        report.warn("no JSON/MCMeta files found")


def validate_toml(report: Report, root: Path) -> None:
    files = iter_files(root / ".codex", (".toml",))
    files.extend(iter_files(root / "project-gradle", (".toml",)))
    if not files:
        report.warn("no TOML files found")
        return

    if tomllib is None:
        report.warn("TOML parse skipped; Python 3.11+ required")
        return

    failures_before = report.errors
    for path in files:
        relative = path.relative_to(root)
        try:
            data = path.read_text(encoding="utf-8-sig")
            data = re.sub(r"(?<=[.\[])\$\{([A-Za-z0-9_]+)\}", r"\1", data)
            tomllib.loads(data)
        except Exception as exc:
            report.fail(f"invalid TOML: {relative}: {exc}")

    if report.errors == failures_before:
        report.ok(f"TOML parsed: {len(files)} files")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Quick validation for the workspace.")
    parser.add_argument(
        "--root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="workspace root; default is parent of scripts/",
    )
    gradle = parser.add_mutually_exclusive_group()
    gradle.add_argument("--gradle-test", action="store_true", help="run project-gradle/gradlew.bat test")
    gradle.add_argument("--gradle-build", action="store_true", help="run project-gradle/gradlew.bat build")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = args.root.resolve()
    report = Report()

    print(f"Workspace: {root}")
    check_required_paths(report, root)
    validate_codex_stack(report, root)
    check_python(report)
    check_java(report, root)
    validate_json(report, root)
    validate_toml(report, root)

    gradle_mode = None
    if args.gradle_test:
        gradle_mode = "test"
    elif args.gradle_build:
        gradle_mode = "build"
    check_gradle_wrapper(report, root, gradle_mode)

    print("")
    print(f"Result: {report.errors} error(s), {report.warnings} warning(s)")
    return 1 if report.errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
