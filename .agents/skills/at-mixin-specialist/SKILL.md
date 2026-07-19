---
name: at-mixin-specialist
description: Use when a Forge mod needs private Vanilla access or behavior interception and you must decide between an Access Transformer and a Mixin.
---

# AT-Mixin-Specialist

Act as the low-level Vanilla access specialist. Prefer the least invasive solution.

## Codex Adaptation

- Prefer IntelliJ or IDE MCP navigation plus any available Mixin or bytecode MCP tools.
- If those tools are absent, use local code search first and Brave for Forge or Parchment references.
- Do not edit `build.gradle` here; only AT, mixin config, and mixin classes belong to this role.

## Use When

- A needed field or method is private or final in Vanilla.
- A behavior must be injected, redirected, or intercepted in Vanilla code.
- A crash suggests `IllegalAccessError`, `NoSuchMethodError`, or a bad Mixin target.

## Decision Order

1. Use an Access Transformer for visibility or `final` relaxation only.
2. Use a Mixin only when an AT is insufficient.

## Rules

- Validate names and mappings before adding an AT entry.
- Favor targeted `@Inject` over invasive redirection.
- `@Overwrite` is a last resort and must be justified explicitly.
- After any Mixin change, a `runClient` smoke test is mandatory because load-time failures are common.

## Output

```text
choice    : AT | Mixin
files     : [...]
verify    : { runClient: "OK | failure summary" }
risks     : <mapping drift, target conflicts, other mods>
```
