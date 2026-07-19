# Cheatsheet rg / fd

## rg — options essentielles

| Option                       | Effet                                                                   |
| ---------------------------- | ----------------------------------------------------------------------- |
| `-n`                         | Numero de ligne                                                         |
| `-l`                         | Liste des fichiers contenant un match (pas le contenu)                  |
| `-c`                         | Compte les matchs par fichier                                           |
| `-i`                         | Insensible a la casse                                                   |
| `-w`                         | Mot entier                                                               |
| `-F`                         | Pattern fixe (pas de regex)                                             |
| `-P`                         | PCRE2 (regex avancees: look-around, backrefs)                           |
| `-C N`                       | Contexte de N lignes avant et apres                                     |
| `-A N` / `-B N`              | N lignes apres / avant                                                  |
| `-m N`                       | Stop apres N matchs total                                                |
| `-M N`                       | Coupe les lignes de sortie a N caracteres                               |
| `--no-heading`               | Pas de bandeau "fichier:" entre les groupes                             |
| `--no-ignore`                | Desactive le respect de `.gitignore`                                    |
| `-uu`                        | Cherche aussi dans les fichiers ignores (deux fois = ignores caches)    |
| `-t java` / `--type java`    | Limite aux fichiers detectes comme Java                                 |
| `-g '*.java'`                | Filtre par glob (inclusif)                                              |
| `-g '!build/**'`             | Glob negatif (exclusion)                                                |
| `-I`                         | Ignore les fichiers binaires                                            |
| `--json`                     | Sortie JSON stable, un objet par ligne                                 |
| `--files`                    | Liste tous les fichiers que rg traiterait (sans regex)                  |
| `--stats`                    | Affiche stats (fichier scannes, matchs, temps)                         |
| `-0`                         | Separe les fichiers trouves par NUL (pour `xargs -0`)                  |

## Types integres utiles pour Forge

- `java`, `json`, `json5`, `jsonc`, `toml`, `yaml`, `xml`, `html`, `markdown`, `rust`, `kotlin`, `gradle`, `properties`.

Definir un type custom si besoin (rare):

```bash
rg --type-add 'mc:function=*.mcfunction' -t mc:function "give @" project-gradle/src
```

## rg — formes frequentes

```bash
# Tout fichier contenant un ID de registre du mod
rg -n -t java 'FuguReviveMe\.[A-Z][A-Za-z0-9_]+' project-gradle/src

# Recettes d'un mod donne
rg -n -t json "modid:" project-gradle/src/main/resources/data/minecraft/recipes

# Lignes trop longues (> 120) dans le code
rg -n --type java -e '.{121,}' project-gradle/src

# Recherche avec PCRE2 et lookbehind
rg -n -P '(?<=@Mixin\()\s*[A-Z][A-Za-z0-9_]+' project-gradle/src

# Mode fichier uniquement, avec NUL pour piping
rg -l -0 "ArmorMaterial" project-gradle/src | xargs -0 -n1 rg -n "initialize"

# Sortie JSON stable
rg --json "Exception" project-gradle/run/logs | jq -r 'select(.type=="match") | .data.path.text'

# Stats rapides
rg --stats "Caused by" project-gradle/run/logs
```

## fd — alternative moderne a find

| Option           | Effet                                                |
| ---------------- | ---------------------------------------------------- |
| `-t f` / `-t d`  | Type: fichier / dossier                              |
| `-e java`        | Extension unique (multi: `-e java -e kt`)            |
| `-g '*.json'`    | Glob (inclusif)                                      |
| `-g '!build/**'` | Glob negatif                                         |
| `-H`             | Toujours afficher le chemin (meme en recherche unique) |
| `-0`             | Separateur NUL pour `xargs -0`                        |
| `--max-depth N`  | Profondeur max                                       |
| `--hidden`       | Inclut les fichiers caches                           |
| `--no-ignore`    | Desactive `.gitignore`                               |
| `--changed-within 5m` | Fichiers modifies recemment                    |
| `--exec CMD`     | Execute une commande (comme `find -exec`)            |

```bash
# Tous les .java sous project-gradle (respecte .gitignore)
fd -e java . project-gradle

# Recents logs de 10 minutes
fd --changed-within 10m -e log . project-gradle/run/logs

# Compter les .java par dossier
fd -e java . project-gradle/src | Split-Path -Parent | Group-Object | Sort-Object Count -Descending
```

## Pipeline rg + fd

```bash
# Trouver les .java qui contiennent "Mixin" et un "@At" (verification stricte)
fd -e java . project-gradle/src | rg -l "Mixin" | xargs -0 rg -n "@At\\("
```

## Performance et hygiene

- Toujours preciser un repertoire de recherche (evite `.` si le dossier parent contient des milliards de fichiers).
- Utiliser `-g` ou `-t` des qu'on peut reduire le scope.
- Combiner `-M` et `-m` sur les logs pour eviter l'explosion de tokens.
- Preferer `-l` puis lecture ciblee si le contexte est gros.
- Pour les regex PCRE2, escape correctement (`\\(`, `\\.`, `\\b`).
