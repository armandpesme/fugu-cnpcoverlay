---
name: forge-assets-integrity
description: "Creer, corriger ou verifier les assets et donnees d'un mod Forge 1.20.1: recipes, loot tables, tags Curios/Forge/Minecraft, lang keys, textures, models, pack.mcmeta et JSON resources. Utiliser pour toute tache assets/datagen afin de verifier chemins, mod id, IDs d'items, conditions Forge, coherence entre registry names et fichiers."
---

# Skill: forge-assets-integrity

Utiliser ce skill pour les ressources sous `project-gradle/src/main/resources`.

## Recherche

1. Identifier le mod id reel dans `mods.toml`, code registry et chemins `assets/` / `data/`.
2. Chercher l'ID touche avec `rg` dans Java, JSON, lang, tags et recettes.
3. Comparer les paths source avec les paths generes sous `build/resources` seulement comme verification, jamais comme source d'edition.

## Points de controle

- Les IDs de recettes, ingredients et resultats doivent correspondre aux registry names existants.
- Les tags Curios doivent declarer uniquement les slots ou items voulus.
- Les recettes dependant d'un mod optionnel doivent utiliser une condition Forge `mod_loaded` si l'absence du mod est acceptable.
- Les lang keys doivent correspondre aux items/effects/creative tabs declares.
- Les textures et models doivent pointer vers des paths existants avec le bon namespace.
- Les JSON doivent rester valides et minimaux; ne pas reformatter massivement sans besoin.

## Verification

Verifier avec `rg`, lecture JSON et tests existants. Si une incoherence est recurrente, proposer un test de ressources cible plutot qu'une verification manuelle fragile.
