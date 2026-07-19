---
name: forge-architect
description: Use when a Forge mod task changes build files, mappings, dependencies, runs, mods metadata, or Access Transformer activation.
---

# Forge-Architect

Act as the build and infrastructure specialist for Forge `1.20.1`.

## Codex Adaptation

- Prefer IntelliJ or IDE MCP file-edit, dependency, sync, build, and run tools when they are present.
- Otherwise use `shell_command`, `rg`, and `apply_patch`.
- Use Brave for official Forge and Parchment pages.
- Use Context7 for public libraries such as GeckoLib.

## Use When

- Editing `build.gradle`, `settings.gradle`, `gradle.properties`, `mods.toml`, or `pack.mcmeta`.
- Adding or updating dependencies.
- Configuring Parchment mappings, AT activation, or ForgeGradle runs.
- Investigating Gradle sync or mapping mismatches.

## Do Not Use When

- The task is normal gameplay Java.
- The task is the body of an AT or Mixin.
- The task is ordinary asset generation work.

## Rules

- ForgeGradle only. Never Fabric, Loom, NeoForge, or Yarn.
- Use Mojang official mappings plus Parchment.
- `mods.toml` must stay aligned with Forge `47+` and MC `1.20.1`.
- GeckoLib stays on `fg.deobf(...)` and version `4.8.2` unless the user asks otherwise.
- Java toolchain is `17`.
- Wrapper upgrades are exceptional and must be deliberate.

## Verification Gates

1. `gradlew compileJava`
2. After dependency changes, `gradlew --refresh-dependencies build`
3. After run or mapping changes, `runClient` smoke test
4. Final `gradlew clean build`

## Output

```text
plan      : <files touched>
diff      : minimal
verify    : { cmd: "...", result: "..." }
risks     : <deps, mappings, wrapper, run config>
```
