# AGENTS.md

## Sources de verite

- `AGENTS.md`: instructions operationnelles chargees automatiquement par Codex.
- `README.md`: presentation humaine du workspace; ce fichier n'est pas un fallback d'instructions Codex.
- `PLANS.md`: etat canonique de progression, decisions et reprise.
- `.codex/config.toml`: configuration projet Codex.
- `.codex/agents/*.toml`: agents personnalises actifs.
- `.agents/skills/*/SKILL.md`: skills de depot actifs.

## Regles permanentes

- Repondre en francais, de facon concise et pragmatique.
- Respecter d'abord l'instruction humaine du fil courant, puis la configuration projet et ce fichier.
- Lire les fichiers locaux avant de conclure; verifier les informations techniques importantes dans la documentation officielle actuelle.
- Garder les changements metier dans `project-gradle/` sauf demande explicite visant la stack, la documentation, les datapacks ou les resource packs.
- Ne pas ecraser de changements humains non compris et ne pas restaurer automatiquement un fichier supprime.
- Ne jamais utiliser de force push, reset destructif, clean Git ou rebase sans autorisation explicite.
- Ne jamais inventer une API Forge/Minecraft.
- Conserver les separations `client`, `server` et `common`; toute synchronisation client/serveur passe par packet ou capability explicite.
- Ne pas introduire Fabric, Loom, NeoForge, Yarn ni d'anciennes APIs de registry.
- Utiliser GeckoLib seulement s'il est deja present ou explicitement requis.

## Agents et delegation

Agents actifs: `asset-manager`, `at-mixin-specialist`, `debugger`, `explorer`, `explorer-github`, `forge-build-engineer`, `gameplay-engineer`, `geckolib-epicfight-specialist`, `git-publisher`, `maestro`, `network-agent`, `patch-engineer`, `performance-engineer`, `planner`, `qa-release`, `runner`, `server-authority-reviewer`, `test-engineer`.

- Utiliser `maestro` uniquement pour une demande complexe, ambigue ou multi-domaines.
- Choisir l'agent actif le plus specialise; agir directement lorsqu'une delegation n'apporte rien.
- Garder `agents.max_depth = 1` et ne jamais lancer plusieurs ecritures concurrentes sur le meme scope.
- Utiliser Sol pour les roles complexes ou critiques, Terra pour le travail quotidien et Luna pour les taches precises et repetables.
- Utiliser `git-publisher` pour les ecritures Git/GitHub, `explorer-github` pour la lecture historique et `qa-release` pour un verdict final.

## Forge et verification

Depuis `project-gradle/`:

```powershell
.\gradlew.bat build
```

- Lancer `test` separement seulement pour diagnostiquer un echec.
- Utiliser `clean build` uniquement sur demande explicite ou lors d'une finalisation.
- Lancer `runClient` si un changement touche le runtime, le rendu, les Mixins ou le comportement en jeu.
- Pendant le developpement, garder les Mixins critiques en mode strict; retirer ce garde-fou seulement avant le clean build ou la release.
- Valider les JSON, namespaces, chemins et registry IDs pour les assets et donnees.
- Pour un crash, lire `docs/runtime-paths.md`, puis utiliser les skills de diagnostic adaptes.
- Apres un build reussi, lier le JAR produit sous `project-gradle/build/libs/`.
- Si une verification raisonnable n'est pas lancee, expliquer pourquoi.

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **fugu-cnpcoverlay-3.0.1-alpha** (1296 symbols, 3140 relationships, 111 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> Index stale? Run `node .gitnexus/run.cjs analyze` from the project root — it auto-selects an available runner. No `.gitnexus/run.cjs` yet? `npx gitnexus analyze` (npm 11 crash → `npm i -g gitnexus`; #1939).

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows. For regression review, compare against the default branch: `detect_changes({scope: "compare", base_ref: "master"})`.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `query({search_query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `context({name: "symbolName"})`.
- For security review, `explain({target: "fileOrSymbol"})` lists taint findings (source→sink flows; needs `analyze --pdg`).

## Never Do

- NEVER edit a function, class, or method without first running `impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `rename` which understands the call graph.
- NEVER commit changes without running `detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/fugu-cnpcoverlay-3.0.1-alpha/context` | Codebase overview, check index freshness |
| `gitnexus://repo/fugu-cnpcoverlay-3.0.1-alpha/clusters` | All functional areas |
| `gitnexus://repo/fugu-cnpcoverlay-3.0.1-alpha/processes` | All execution flows |
| `gitnexus://repo/fugu-cnpcoverlay-3.0.1-alpha/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
