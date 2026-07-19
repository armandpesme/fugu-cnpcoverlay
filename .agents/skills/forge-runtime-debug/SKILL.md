---
name: forge-runtime-debug
id: forge.forge-runtime-debug
description: Diagnostiquer crashs, logs, echecs runClient/runServer, comportements anormaux ou regressions runtime dans un mod Forge 1.20.1. Utiliser avant de corriger un bug runtime afin de lire les logs, isoler la cause racine, distinguer client/server/common, verifier mixins/recipes/registries et eviter les corrections speculatives.
---

# Skill: forge-runtime-debug

Utiliser ce skill pour tout crash ou comportement runtime anormal.

Pour les commandes concretes de recherche dans les logs, utiliser le skill `minecraft-log-tools` qui fournit les commandes `rg`, `fd`, `lnav`, `tail` pretes a l'emploi.

## Procedure

1. Reproduire ou lire le log le plus proche de l'echec.
2. Chercher les erreurs fortes: exception racine, `Caused by`, mixin failed, registry missing, recipe parsing, classloading client sur serveur.
3. Chercher les classes, IDs ou paths cites dans le workspace avec `rg`.
4. Classer la cause probable: API incorrecte, side client/server, asset JSON, registry, mixin/refmap, dependance optionnelle, donnees sauvegardees.
5. Corriger seulement apres avoir une hypothese testable.

## Logs utiles

- `project-gradle/run/logs/latest.log`
- `project-gradle/run/logs/debug.log`
- `project-gradle/run/crash-reports/`
- `launcher_log.txt` (si pas de crash report)
- `hs_err_pid*.log` (crash JVM natif)
- sortie Gradle de `runClient`, `runServer`, `test` ou `build`

## Rapport

Donner la cause racine probable, la preuve locale, le fichier a corriger et la verification de non-regression. Si le log est insuffisant, demander ou lancer la reproduction la plus petite possible.
