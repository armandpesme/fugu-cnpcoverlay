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

