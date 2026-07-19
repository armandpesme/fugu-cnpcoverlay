---
name: forge-workspace-onboarding
description: Initialiser une session de travail pour ce workspace Minecraft Forge 1.20.1. Utiliser quand une tache commence, quand le contexte a ete compacte, quand un nouvel agent doit reprendre sans etat, ou avant une modification Forge; lire AGENTS.md, PLANS.md et les skills locaux, avec README.md seulement si le contexte utilisateur/projet le justifie.
---

# Skill: forge-workspace-onboarding

Utiliser ce skill au demarrage d'une tache Forge dans ce workspace.

## Lecture minimale

1. Lire `AGENTS.md` si les instructions du fil courant n'ont pas deja fourni son contenu.
2. Lire `PLANS.md` pour l'etat courant, les decisions actives, les risques et la prochaine action.
3. Lire `.codex/config.toml` et choisir les skills locaux adaptes a la tache.
4. Lire `README.md` uniquement si la tache concerne la presentation utilisateur, le contexte metier propre au projet ou la documentation publique.
5. Rester dans `project-gradle/` pour les changements metier sauf demande explicite de documentation, configuration Codex, datapack, resource pack, shader pack ou procedure.

## Cadre de travail

- Repondre en francais.
- GitHub, commits, branches et push sont autorises quand la demande humaine les vise explicitement; utiliser `git-publisher` pour ecrire et `explorer-github` pour lire.
- Ne jamais faire de force-push, reset, rebase, clean destructif ou saisie de secret sans demande humaine explicite.
- Ne pas ecraser de changements humains non compris.
- Preferer PowerShell, `rg`, les fichiers locaux et les patterns existants.
- Ne jamais inventer une API Forge/Minecraft; verifier localement puis via documentation fiable si necessaire.
- Garder en tete Forge `1.20.1-47.4.x`, Java 17, Curios, GeckoLib seulement si deja present ou explicitement requis.

## Sortie attendue

Avant d'agir, formuler l'hypothese de contexte en une phrase si elle influence la solution. Apres une reprise sans etat, citer la prochaine action concrete issue de `PLANS.md`.
