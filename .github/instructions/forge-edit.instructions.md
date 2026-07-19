---
applyTo: "**/*.java,**/*.gradle,**/*.toml,**/*.json,**/*.mcmeta"
description: "Discipline avant/après édition pour fichiers Forge 1.20.1."
---

# Discipline d'édition Forge 1.20.1

## Avant édition (before-edit)

- Vérifier l'état Git avant des modifications larges (pas écraser un changement humain non lu).
- Lire **d'abord** les fichiers concernés.
- Chercher les usages des symboles touchés avec `grep_search` / `usages`.
- Confirmer la version cible (Forge `47.4.x`, Java `17`, Minecraft `1.20.1`).

## Après édition (after-edit)

- Compiler ou proposer la vérification minimale selon le type de fichier :
  - Java : compile / `gradlew build`.
  - `build.gradle`, `gradle.properties`, `settings.gradle` : `gradlew tasks` ou build complet.
  - JSON / lang / recipes / loot / tags : valider JSON et chemins, regénérer datagen si utilisé.
  - `mods.toml`, `pack.mcmeta` : vérifier mod id, version, dépendances.
- Si la vérification n'a pas pu être lancée, le **dire explicitement**.
- À la fin d'un cycle build : fournir le lien cliquable vers le JAR de `build/libs/`.
