---
name: multi-format-patch
description: Modify YAML, XML, INI, CSV, TSV, or mixed-format config files alongside JSON. Covers jq for JSON, yq for YAML/XML/TOML, xq for XML, and Python csv/json for tabular data. Use when touching non-JSON configs, or when a task spans multiple formats. Less specialized than jq-json-patch for pure JSON.
---

# Skill: multi-format-patch

Utiliser ce skill quand la tache touche des formats au-dela du JSON pur: YAML, TOML, XML, INI, CSV, TSV, ou une combinaison de formats. Ce skill est un complement generaliste; pour le JSON pur, preferer `jq-json-patch` (plus specialise, moins cher en tokens).

## Formats et outils

| Format   | Outil principal | Commande de base                                   | Fallback              |
| -------- | --------------- | --------------------------------------------------- | --------------------- |
| JSON     | `jq`            | `jq '.key = "val"' file.json`                       | `python -m json.tool` |
| YAML     | `yq`            | `yq eval '.key = "val"' file.yml`                   | Python `yaml` (stdlib)|
| TOML     | `yq`            | `yq eval -t '.key = "val"' file.toml`               | Python `tomllib`      |
| XML      | `xq`            | `xq '.root.child' file.xml`                         | Python `xml.etree`    |
| INI      | `crudini`       | `crudini --set file.ini section key value`          | Python `configparser` |
| CSV/TSV  | `csvkit`        | `csvcut -c col1,col2 file.csv`                      | Python `csv`          |

## Prerequis

Verifier la presence des outils avant usage:

```bash
jq --version      # JSON
yq --version      # YAML/TOML/XML
xq --version      # XML (inclus avec yq)
crudini --version # INI (Linux)
csvstat --version # CSV (csvkit)
```

Sur Windows, `yq` et `xq` sont disponibles via `winget install MikeFarah.yq`. `crudini` et `csvkit` peuvent necessiter WSL ou Python fallback.

## Quand utiliser ce skill

- Modification de `mods.toml`, `pack.mcmeta`, `build.gradle` (ni JSON pur)
- Tache touchant a la fois des `.json`, des `.toml` et des `.mcmeta`
- Conversion entre formats (ex: CSV → JSON pour datagen)
- Recherche/remplacement multi-format dans un arbre de projet
- Extraction de valeurs depuis des fichiers de config heterogenes

## Quand NE PAS utiliser

- Modification d'un seul fichier JSON → `jq-json-patch`
- Transformation complexe multi-fichiers mais tout en JSON → `py-complex-patch`
- Simple lecture de fichier non-JSON (un `read_file` suffit)

## Patterns par format

### JSON (delegue a jq)

```bash
jq '.field = "value"' file.json > tmp.json && mv tmp.json file.json
```

### YAML (yq)

```bash
yq eval '.key = "value"' -i file.yml
```

### TOML (yq avec flag TOML)

```bash
yq eval -t '.mods[0].modId = "newid"' -i mods.toml
```

### XML (xq)

```bash
xq '.root.element += {"@attr": "val"}' file.xml
```

### INI (crudini ou Python fallback)

```bash
# Linux avec crudini
crudini --set file.ini section key value
```

```python
# Fallback Python (Windows sans crudini)
import configparser
c = configparser.ConfigParser()
c.read("file.ini")
c.set("section", "key", "value")
with open("file.ini", "w") as f:
    c.write(f)
```

### CSV (csvkit ou Python fallback)

```bash
# Ajouter une colonne
csvcut -c col1,col2 file.csv | csvsql --query "SELECT *, 'newval' AS col3 FROM stdin" > out.csv
```

```python
# Fallback Python
import csv
rows = []
with open("file.csv", newline="") as f:
    for row in csv.DictReader(f):
        row["new_col"] = "value"
        rows.append(row)
with open("file.csv", "w", newline="") as f:
    w = csv.DictWriter(f, fieldnames=rows[0].keys())
    w.writeheader()
    w.writerows(rows)
```

## Procedure generale

1. **Identifier** tous les formats concernes par la tache.
2. **Pour chaque format**, choisir l'outil principal; si absent, utiliser le fallback Python.
3. **Pour le JSON**, deleguer a `jq` (une commande simple) ou `py-complex-patch` (transformation complexe).
4. **Traiter** les fichiers format par format, en commencant par le plus critique.
5. **Valider** chaque fichier modifie avec l'outil approprie (ex: `jq empty` pour JSON, `yq eval '.'` pour YAML).
6. **Pour les projets Forge**: verifier la coherence `modid`, lancer `call gradlew.bat build`.

## Interdits

- Ne pas utiliser `jq` sur du YAML, TOML, XML, INI, CSV — utiliser l'outil dedie.
- Ne pas inventer de syntaxe `yq` / `xq` — la syntaxe `yq eval` est differente de `jq`.
- Ne pas modifier un fichier sans avoir lu sa structure avant.
- Ne pas installer d'outil systeme sans demander confirmation.
