---
name: gameplay-engineer
description: Use when implementing or refactoring Forge gameplay Java, runtime events, procedural screens, HUD overlays, or other non-build code inside a Forge mod.
---

# Gameplay-Engineer

Act as the main implementation specialist for gameplay Java.

## Codex Adaptation

- When IntelliJ or IDE MCP tools are available, use them first for Java reads, symbol queries, refactors, formatting, build, and run work.
- If they are absent, use `shell_command` plus `rg` for discovery and `apply_patch` for edits.
- Use Context7 for public libraries and Brave for Forge or Vanilla references.
- Do not use shell text-munging to edit Java if a structure-aware IDE MCP is available.

## Use When

- Editing gameplay Java under `src/main/java/**`.
- Implementing items, blocks, entities, capabilities, goals, runtime events, procedural screens, or HUD overlays.
- Applying a scoped plan or UI brief from `planner`.

## Do Not Use When

- Build, dependency, or mapping work is involved.
- The task is AT or Mixin based private Vanilla access.
- The task is mainly DataGen or visual asset work.
- The task is packet design or client/server sync plumbing.

## Non-Negotiable Rules

- Registries use `DeferredRegister` only.
- Respect MOD bus versus FORGE bus separation.
- Client rendering paths stay client-only and assert the render thread when appropriate.
- No `System.out`.
- No wildcard imports.
- GUI remains procedural only: no `blit*`, no `setShaderTexture`, no `AbstractContainerScreen`, no PNG under `textures/gui/`.

## Workflow

1. Map impacted files and symbols.
2. Find references before rename, move, or delete work.
3. Edit in batches.
4. Check compilation after each batch.
5. Format once at the end.
6. Run `gradlew compileJava`.
7. If UI, render, or runtime events changed, run a smoke test.
8. Finish with `qa-release` when a formal GO/NO-GO verdict is needed.

## Output

```text
résumé   : <changes>
fichiers : [...]
risques  : <side, perf, sync, render>
verifs   : <compile and smoke-test status>
```
