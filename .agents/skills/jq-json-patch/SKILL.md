---
name: jq-json-patch
description: Patch, query or transform JSON files with jq. Fast, token-efficient, and reliable for Git patching, single-field edits, array filtering, slicing, merging, and JSON validation. Use when the user asks to modify, extract, validate, or search JSON. Prefer over py-complex-patch for small to medium JSON changes.
---

# Skill: jq-json-patch

Utiliser ce skill pour toute modification, extraction, validation ou transformation de fichiers JSON via `jq`. C'est le mode par defaut pour les operations JSON simples a moyennes. Pour les transformations multi-fichiers, la logique conditionnelle complexe ou la validation de schema croise, basculer sur `py-complex-patch`.

## Prerequis

- `jq` doit etre installe (verifier avec `jq --version`). Si absent, suggerer `winget install jqlang.jq` (Windows) ou `apt install jq` (Linux).
- Toujours travailler sur une copie temporaire ou utiliser `--arg` / `--slurpfile` pour preserver l'original jusqu'a validation.

## Quand utiliser ce skill

- Modification d'un champ unique dans un JSON (ex: changer `version`, `modid`, une couleur)
- Extraction de valeurs avec filtre (ex: `.[] | select(.type == "minecraft:sword")`)
- Fusion de deux JSON avec `*` ou `+`
- Suppression de cles avec `del()`
- Ajout/insertion dans des arrays avec `+` ou `map()`
- Validation rapide: `jq empty file.json && echo VALID || echo INVALID`
- Formatage/indentation: `jq --indent 4 '.' file.json`
- Transformation par lot via `xargs` ou boucle shell

## Quand NE PAS utiliser

- Logique conditionnelle imbriquee sur plus de 3 niveaux → utiliser `py-complex-patch`
- Modification de plusieurs fichiers avec des regles differentes par fichier → `py-complex-patch`
- Validation de schema JSON croise (references `$ref` entre fichiers) → `py-complex-patch`
- Fichiers YAML, XML, INI, CSV → utiliser `multi-format-patch`
- Fichier JSON > 100 Mo → `jq` le gere en streaming, mais `py-complex-patch` peut etre plus sur pour les transformations complexes sur gros volumes

## Patterns essentiels

### Lire et modifier un champ

```bash
jq '.version = "1.2.0"' file.json > tmp.json && mv tmp.json file.json
```

### Supprimer une cle

```bash
jq 'del(.unwantedKey)' file.json > tmp.json && mv tmp.json file.json
```

### Filtrer un tableau

```bash
jq '.items |= map(select(.rarity == "epic"))' loot_table.json
```

### Ajouter un element a un tableau

```bash
jq '.entries += [{"type": "minecraft:item", "name": "modid:new_item"}]' file.json
```

### Fusionner deux JSON

```bash
jq -s '.[0] * .[1]' base.json override.json
```

### Valider sans modifier

```bash
jq empty file.json 2>&1 && echo "VALID" || echo "INVALID: $(jq empty file.json 2>&1)"
```

### Pretty-print avec indentation 2

```bash
jq --indent 2 '.' file.json > tmp.json && mv tmp.json file.json
```

### Extraire une sous-structure

```bash
jq '{name: .display.name, id: .registry_name}' file.json
```

## Procedure de modification securisee

1. **Lire** le JSON cible avec `read_file` pour comprendre la structure.
2. **Construire** la commande `jq` adaptee.
3. **Tester** sur stdout d'abord (sans `mv`) pour verifier le resultat.
4. **Appliquer** avec `> tmp.json && mv tmp.json file.json`.
5. **Valider** le fichier final avec `jq empty file.json`.
6. Si le fichier fait partie d'un projet Forge, verifier que le `modid` et les chemins restent coherents.

## Verifications post-modification

- `jq empty <fichier>.json` — pas d'erreur de syntaxe
- `jq '. | length' <fichier>.json` — le fichier n'est pas vide
- Pour les resources Forge: verifier `assets/<modid>/` dans les chemins
- Lancer un build Gradle si le JSON fait partie du mod

## Interdits

- Ne jamais utiliser `jq` sur du YAML, XML, INI ou CSV (ils cassent la syntaxe JSON).
- Ne pas ecrire de script `jq` de plus de 5 lignes dans le prompt — basculer sur `py-complex-patch`.
- Ne pas modifier un fichier JSON sans avoir lu sa structure avant.
