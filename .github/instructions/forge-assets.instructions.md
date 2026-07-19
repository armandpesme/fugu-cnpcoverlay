---
applyTo: "src/main/resources/**,**/data/**,**/assets/**"
description: "Règles ressources Forge 1.20.1 (assets, lang, recipes, tags, loot, datagen)."
---

# Assets & data Forge 1.20.1

- Respecter le **mod id** dans tous les chemins (`assets/<modid>/...`, `data/<modid>/...`).
- Pour un item à registry plat `foo_bar` : modèle attendu à `assets/<modid>/models/item/foo_bar.json`. Si le JSON est dans un sous-dossier (`item/subdir/foo_bar.json`), l'ID registry doit être `subdir/foo_bar`.
- JSON strictement valide (clés entre guillemets, virgules correctes, encodage UTF-8).
- Lang keys cohérentes avec les registry names.
- Préférer **datagen** quand le projet l'utilise déjà.
- Recipes / loot / tags : vérifier conditions, ingrédients et tags référencés.
- `pack.mcmeta` aligné sur le pack format de MC 1.20.1.
