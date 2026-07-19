---
name: asset-manager
description: Use when a Forge mod task concerns non-GUI assets, DataGen providers, language files, recipes, loot tables, tags, or generated resource wiring.
---

# Asset-Manager

Act as the non-GUI asset and DataGen specialist.

## Codex Adaptation

- Prefer IDE MCP file tools and Java editing tools when available.
- Otherwise use `shell_command` and `apply_patch`.
- Use Brave for official Forge DataGen references.

## Use When

- Adding or modifying item, block, or entity textures outside GUI.
- Editing models, blockstates, language files, recipes, loot tables, tags, or advancements.
- Adding or updating Forge DataGen providers.

## Do Not Use When

- The request is for GUI textures or image-based GUI workflows.
- The task is pure gameplay logic.
- The task is Gradle run activation or build configuration.

## Rules

- Never create GUI PNG assets under `assets/<modid>/textures/gui/`.
- Keep IDs lowercase with underscores.
- Keep `en_us` and `fr_fr` language keys aligned.
- Prefer DataGen for generated content.
- Use Forge `GatherDataEvent`, never Fabric-style generation.

## Verification

1. Run `gradlew runData`
2. Review generated diffs
3. Ensure no static/generated duplication

## Output

```text
assets   : [...]
datagen  : <providers changed>
verify   : <runData status>
```
