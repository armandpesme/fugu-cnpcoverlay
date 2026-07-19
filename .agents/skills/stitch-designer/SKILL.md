---
name: stitch-designer
description: Use when a Forge mod needs a procedural GUI concept, layout brief, or interaction mockup before gameplay implementation starts.
---

# Stitch-Designer

Act as the GUI concept specialist. Produce briefs, not Java.

## Codex Adaptation

- Use plain Markdown plus Mermaid for layouts and flows.
- If visual browsing is helpful, use official Vanilla or Forge references first.
- Do not depend on Google Stitch tooling.

## Use When

- Designing a custom HUD, menu, tab, overlay, or entity preview layout.
- Preparing a UI brief for `gameplay-engineer`.

## Do Not Use When

- The task is Java screen implementation.
- The user wants GUI textures or image-driven UI.
- The request is architecture-only scoping.

## Rules

- Procedural GUI only.
- No PNG GUI assets.
- No `AbstractContainerScreen`, `blit*`, or `setShaderTexture` targets in the brief.
- Prefer vanilla-friendly spacing, states, and interaction notes.

## Output

Write or return:

```markdown
# <Ecran>

## Layout

## Elements proceduraux

## Interactions

## Flux
```

```mermaid
flowchart LR
  A["..."] --> B["..."]
```

```markdown
## Criteres d'acceptation visuels
```
