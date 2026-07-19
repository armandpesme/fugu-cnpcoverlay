---
name: forge-armor-variant-port
description: Use when porting Forge 1.20.1 armor sets from source mods or resource packs into this workspace, especially GeckoLib armor variants, renamed item IDs, visual-only imports, lang/model/texture/animation resources, and optional Epic Fight compatibility without gameplay dependencies.
---

# Forge Armor Variant Port

## Workflow

1. Read `AGENTS.md`, `README.md`, `PLANS.md`, and the source mod registry/decompiled model classes.
2. Enumerate every source item and every model variant. If the source has alternative models that the user wants preserved, create distinct target items instead of a runtime toggle.
3. Choose target IDs before editing. Keep `hazennstuff` as namespace unless explicitly asked otherwise.
4. Copy only visual resources: item icons, item models, GeckoLib geo, armor textures, glowmasks, and animations.
5. Do not port gameplay: effects, attributes, keybinds, packets, recipes, loot tables, capabilities, tooltips describing powers, or source dependencies.
6. Use the existing armor registration and renderer patterns. Keep client-only renderer code isolated under client packages.
7. Verify with `rg`, JSON parsing, `gradlew.bat test`, `gradlew.bat build`, and `runClient` when runtime debug is requested.

## GeckoLib Checks

- Confirm `.geo.json` bones match an existing renderer family before adding a new renderer.
- Prefer a definitions table when suffixes are unusual or model variants exist.
- Keep Epic Fight optional: no direct imports from Epic Fight classes unless an existing reflection bridge is used.
- Use generic armor animations only when the source has no armor-specific animation or the existing mod pattern expects it.

## Output Notes

Record source paths, target IDs, model variants, validation commands, JAR path, and residual visual-validation risk in `PLANS.md`.
