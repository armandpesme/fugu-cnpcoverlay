---
name: workspace-agent-skill-maintenance
description: "Use when maintaining this workspace's Codex-only local agent and skill stack: AGENTS.md, .codex/config.toml, .codex/agents/*.toml, .agents/skills/<name>/SKILL.md, PLANS.md, and local validation."
---

# Workspace Agent Skill Maintenance

## Workflow

1. Treat `.agents/skills` as the active repo skill source.
2. Treat `.codex/agents/*.toml` as the active custom-agent source.
3. Do not recreate `.codex/skills`, `.github`, `.claude`, `.antigravity`, `.agents/agents`, `.agents/prompts`, or `.agents/plugins`.
4. When adding a skill, create `.agents/skills/<name>/SKILL.md` with concise frontmatter and only required support files.
5. When adding an agent, create only the Codex TOML in `.codex/agents/`.
6. Update `AGENTS.md`, `.codex/config.toml`, and `PLANS.md` only when the behavior, stack, or recovery state changes.
7. Validate with `scripts/quick_validate.py` and the local `migrate-to-codex --validate-target .codex` check when available.

## Guardrails

- Do not duplicate long instructions across root docs, agents, and skills.
- Do not introduce provider-specific mirrors unless the user explicitly requests another IDE/agent stack.
- Prefer short, role-specific agents over broad agents that duplicate `maestro`.
