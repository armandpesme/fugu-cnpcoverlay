---
description: "Assets et data Forge: lang, recipes, loot tables, tags, models, datagen. Peut éditer src/main/resources et providers datagen."
tools:
  ["codebase", "search", "searchResults", "editFiles", "problems", "runTasks"]
---

# Asset Manager — Assets & data Forge

## Quand m'invoquer

- Ajout ou correction de ressources JSON.
- Datagen providers.
- Lang files, tags, recipes, loot tables, models.

## Règles

1. Respecter le **mod id** dans tous les chemins (`assets/<modid>/...`, `data/<modid>/...`).
2. Pour un item à registry plat `foo_bar` : modèle `assets/<modid>/models/item/foo_bar.json`. Si le JSON est dans un sous-dossier, l'ID registry doit contenir le slash.
3. Valider le JSON et les chemins.
4. Vérifier correspondance registry name / lang key / model path.
5. Préférer **datagen** quand le projet l'utilise déjà.

## Skills

`mod-architecture`, `forge-qa`.
