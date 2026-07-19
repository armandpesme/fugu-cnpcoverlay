---
name: rg-over-find
description: Recherche code/assets/logs avec ripgrep (rg) au lieu de find/grep. Utiliser pour toute recherche de patterns dans le code source, assets, JSON, lang keys, registry IDs, mixin targets, datagen et logs d'un mod Forge 1.20.1. Plus rapide que find -exec grep, ignore .gitignore par defaut, gere UTF-8/PCRE2, et evite de casser sur des fichiers binaires. A declencher des qu'une tache implique "trouver les fichiers qui contiennent X", "lister les usages de Y", "chercher dans les logs" ou tout scan de contenu dans project-gradle/ et le workspace.
---

# Skill: rg-over-find

Reference pour remplacer `find ... -exec grep` et `find -name` par `rg` (ripgrep) et `fd` dans le workspace Forge 1.20.1.

## Principe

| Besoin                                                              | Outil a utiliser               |
| ------------------------------------------------------------------- | ------------------------------ |
| Chercher un pattern dans le contenu (texte, code, JSON, logs)        | `rg`                           |
| Lister/filtrer des fichiers par nom, type, date, taille, profondeur | `fd` (ou `Get-ChildItem` pwsh) |
| Action systeme sur les fichiers (chmod, mv, rm)                     | `find -exec` (ou PowerShell)   |

`find` n'est plus la valeur par defaut pour la recherche de contenu: `rg` est plus rapide, respecte `.gitignore`, gere les binaires, et sort du JSON exploitable via `--json`.

Verification rapide: `rg --version` et `fd --version`.

## Quand utiliser rg (contenu)

- Trouver toutes les references a une classe, methode, champ ou constante Java.
- Chercher un registry ID, lang key, texture path, tag name, recipe key.
- Scanner les logs (`latest.log`, `debug.log`, `crash-reports/`) pour erreurs, exceptions, mixins.
- Verifier qu'un ID / namespace / modid n'est pas utilise deux fois.
- Lister les fichiers qui contiennent un pattern donne (sans le contenu: `-l`).
- Compter les occurrences (`-c`).
- Mode strict PCRE2 pour les regex avancees (`-P`).
- Recherche dans JSON, JSON5, JSONC, TOML avec types integres (`--type json`, `-tjson`).

## Quand NE PAS utiliser rg (preferer find / fd / PowerShell)

- Recherche par **metadonnees** (taille, date `mtime`, permissions, proprietaire): `find` ou `Get-ChildItem`.
- Action sur fichiers (`chmod`, `mv`, `rm`, `tar`): `find -exec` ou boucle PowerShell.
- Recherche de fichiers par nom **uniquement**, sans contenu, sans `.gitignore` exige: `fd <pattern>` ou `Get-ChildItem -Recurse -Filter`.
- Arborescence de sortie de build / caches (`build/`, `.gradle/`, `node_modules/`): utiliser `fd` avec `--exclude` ou `--no-ignore`.

## Forme canonique des commandes

```bash
# Recherche avec numeros de ligne, sensible a la casse, limite aux types utils
rg -n -t java -t json "RegistryObject|registryName" project-gradle/src

# Insensible a la casse, mots entiers, contexte de 3 lignes
rg -n -i -w -C 3 "TODO|FIXME|XXX" project-gradle/src

# Liste de fichiers uniquement, aucun contenu
rg -l "Mixin\\(" project-gradle/src

# Compter par fichier
rg -c "FuguReviveMe" project-gradle/src

# Limiter le volume (anti explosion de tokens)
rg -n -M 200 -m 50 "Exception|Caused by" project-gradle/run/logs

# Sortie JSON stable pour traitement programmatique
rg --json "modid:fugu" project-gradle/src/main/resources > /tmp/hits.json

# Remplacer find -name par fd (respecte .gitignore, plus rapide)
fd -e java "ArmorItem" project-gradle/src
fd -t f -e json "recipe" project-gradle/src/main/resources
```

## Conventions Windows (PowerShell + Git Bash)

| Bash / rg                              | PowerShell equivalent                            |
| -------------------------------------- | ------------------------------------------------ |
| `rg "foo" .`                           | `rg "foo" .` (memes binaire, `rg` est portable)  |
| `find . -name "*.java"`                | `Get-ChildItem -Recurse -Filter *.java`          |
| `find . -name "*.java" -exec rg ...`   | `Get-ChildItem -Recurse -Filter *.java \| rg ...` |
| `rg ... \| xargs rg ...`               | `rg --files ... \| rg ...`                       |
| `cat file` (si < 50 lignes)            | `Get-Content file`                               |

Pour les longs flux, preferer `rg --json` ou `rg -l` afin d'eviter de saturer le contexte.

## Cas d'usage Forge / Minecraft

- **Registry IDs et modid**: `rg -n -t java "FuguReviveMe\." project-gradle/src` puis verifier la coherence avec les JSON d'assets.
- **Mixin targets**: `rg -n "@Mixin\\(|@Inject\\(|@At\\(" project-gradle/src/main/java`.
- **Lang keys**: `rg -n "translate" project-gradle/src/main/resources/assets/<modid>/lang`.
- **Recipes / loot tables / tags**: `rg -n -t json -l "<modid>:" project-gradle/src/main/resources`.
- **Capabilities / packets**: `rg -n "SimpleChannel|NetworkRegistry|registerMessage" project-gradle/src/main/java`.
- **Curios / Epic Fight compat**: `rg -n "ICurioItem|curios|EpicFightMod" project-gradle/src/main/java`.
- **Run logs / crash reports**: voir aussi `minecraft-log-tools` pour les patterns de diagnostic.
- **Analyse d'impact avant rename**: voir `gitnexus-impact-analysis` pour le blast radius automatique (le complement ideal de `rg`).

## Anti-patterns

- `find project-gradle -name "*.java" -exec grep -Hn "..." {} +` → `rg -n -t java "..." project-gradle`.
- `grep -r` sans filtre → `rg` (ignore .gitignore, gere binaires, plus rapide).
- `rg` sans `-M` / `-m` sur un log > 5 MiB → explosion de tokens. Toujours borner.
- `rg` sur `build/`, `.gradle/`, `run/` regenere en boucle → ajouter `--glob '!build/**' --glob '!.gradle/**'`.
- `rg -l` suivi d'un `cat` sur chaque fichier → utiliser `rg -n -C N` directement.
- Lire un world save ou un JAR avec `rg` sans `-I` (binaire) → utiliser `unzip -l` puis cibler les fichiers texte.

## References

- `references/cheatsheet.md` — aide-memoire des options `rg` et `fd` (regex, types, sortie JSON, performance).
- `references/forge-patterns.md` — patterns prêts a l'emploi pour ce workspace Forge 1.20.1 (IDs, mixins, assets, logs).
