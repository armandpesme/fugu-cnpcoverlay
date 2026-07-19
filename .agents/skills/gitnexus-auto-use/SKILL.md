---
name: gitnexus-auto-use
description: Use when Codex works in this workspace and must autonomously decide whether to use GitNexus MCP, GitNexus resources, or GitNexus CLI for architecture discovery, dependency tracing, symbol impact, debugging, refactoring safety, or pre-commit change detection. Trigger before modifying Java symbols, analyzing Forge mod flows, reviewing risky changes, debugging call chains, or committing project changes.
---

# GitNexus Auto Use

## Role

Use this skill as the project-level routing rule for GitNexus. It decides when to invoke GitNexus and which GitNexus capability to prefer before falling back to normal source search.

Keep task work scoped to the user request. GitNexus informs exploration and risk analysis; it does not replace reading the files being changed.

## Project Defaults

- Repository root: current workspace root.
- Main Forge project: `project-gradle/`.
- GitNexus index name: choose a local repository-specific name when indexing.
- Preferred index command from repository root:

```powershell
gitnexus analyze --index-only --name <index-name>
```

- Fallback runner from repository root:

```powershell
node .gitnexus/run.cjs analyze --index-only --name <index-name>
```

Use `--index-only`; do not let GitNexus inject provider-specific agent files into this workspace.

## Decision Flow

1. If the task asks how code works, where behavior lives, or how a Forge flow is wired, use GitNexus exploration first, then verify in source with `rg` and file reads.
2. If the task changes a Java symbol, registry path, event handler, capability, packet, mixin, renderer, or shared service, check impact before editing when the symbol is indexed.
3. If the task is debugging a runtime failure, use GitNexus to locate likely flows and call chains, then inspect logs and source directly.
4. If the task is refactoring, renaming, moving, or extracting code, use GitNexus to find callers, callees, imports, and related processes before patching.
5. Before committing or handing off a completed change, run `detect_changes()` or the closest available GitNexus equivalent when GitNexus MCP is available.
6. If GitNexus is unavailable, stale, or missing the target symbol, use `rg` and local build checks, and state the limitation in the final answer.

## MCP First

When GitNexus MCP tools or resources are available, prefer them over CLI for interactive analysis:

- Read indexed repository context before deep queries.
- Use query/search tools for concepts, flows, and feature areas.
- Use symbol context tools for callers, callees, and process membership.
- Use impact tools for blast radius before risky edits.
- Use change-detection tools before commit or final handoff.

If tool names are not loaded in the current session, discover them with the available MCP/tool discovery mechanism before assuming they are absent.

## CLI Fallback

Use CLI when MCP is unavailable, the index is missing, or context reports stale data:

```powershell
gitnexus status
gitnexus analyze --index-only --name <index-name>
gitnexus list
```

If the global command is unavailable, use:

```powershell
node .gitnexus/run.cjs analyze --index-only --name <index-name>
```

Do not run destructive cleanup such as `gitnexus clean` unless the user asked for index repair or corruption recovery.

## Forge Safety

For this Forge 1.20.1 workspace, treat these as high-impact surfaces:

- Registries, deferred registers, item/block/entity IDs, recipes, tags, and lang keys.
- Common/client/server boundary changes.
- Packets, capabilities, synchronization, saved data, and event handlers.
- Mixins, access transformers, renderers, GeckoLib integration, and runtime-only client code.
- Public Java methods and classes used outside their defining package.

For high-impact surfaces, combine GitNexus findings with source reads and the relevant Forge skill. If the symbol is not indexed, do not invent impact; report that analysis fell back to source search.

## Reporting

When GitNexus influenced a decision, summarize only the useful evidence:

- What was queried or analyzed.
- Whether the index was fresh, stale, missing, or unavailable.
- Direct dependents or affected flows that changed the implementation choice.
- Remaining risk and verification performed.

Avoid dumping full GitNexus output unless the user explicitly asks for raw results.
