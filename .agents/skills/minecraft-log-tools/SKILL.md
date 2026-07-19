---
name: minecraft-log-tools
id: forge.minecraft-log-tools
description: Outils et commandes de diagnostic pour logs Minecraft/Forge (rg, fd, lnav, tail, mclo.gs). Reference concrete de commandes pretes a l'emploi pour analyser crash-reports, latest.log, debug.log, launcher_log.txt et hs_err_pid. A utiliser des qu'un crash ou une erreur runtime survient.
---

# Skill: minecraft-log-tools

Reference outillee de diagnostic pour logs Minecraft/Forge 1.20.1. Ce skill fournit les commandes exactes a executer, pas la procedure de diagnostic (voir `forge-runtime-debug` pour la procedure).

## Outils disponibles

| Outil                        | Usage                                                            | Commande de verification                  |
| ---------------------------- | ---------------------------------------------------------------- | ----------------------------------------- |
| `rg` (ripgrep)               | Recherche rapide dans les logs, line-oriented, ignore .gitignore | `rg --version`                            |
| `fd`                         | Trouver rapidement les fichiers de log                           | `fd --version`                            |
| `lnav`                       | Analyse interactive multi-fichiers, fusion, filtrage             | `lnav --version`                          |
| `tail` / `Get-Content -Tail` | Lire la fin d'un fichier sans le charger entierement             | `tail --version` (Git Bash) ou PowerShell |
| `Select-String`              | Fallback PowerShell pour recherche sans rg                       | PowerShell integre                        |
| `mclo.gs`                    | Service web d'analyse de logs Minecraft (API: `/1/analyse`)      | `https://mclo.gs`                         |
| `jq`                         | Manipulation JSON (logs structures, rapports)                    | `jq --version`                            |

## Ordre de diagnostic Minecraft/Forge

Priorite des fichiers a examiner :

1. `crash-reports/crash-*.txt` — crash report complet
2. `logs/latest.log` — dernier log de session
3. `logs/debug.log` — log detaille (si active)
4. `launcher_log.txt` — log du launcher (utile si pas de crash report)
5. `hs_err_pid*.log` — crash JVM natif

Dans un workspace mod Forge, les logs sont typiquement dans :

- `project-gradle/run/logs/latest.log`
- `project-gradle/run/logs/debug.log`
- `project-gradle/run/crash-reports/`
- `logs/` (a la racine du workspace ou dans `project-gradle/run/`)

## Etape 0 — Trouver les fichiers de log avec fd

```bash
# Lister tous les logs et crash reports du workspace
fd -t f "latest|debug|launcher_log|crash-|hs_err_pid" .

# Lister uniquement les crash reports
fd -t f "crash-" .

# Trouver les logs modifies recemment (les 3 plus recents)
fd -t f "latest|debug|launcher_log|crash-" . --exec ls -lt {} \; 2>$null | Select-Object -First 3
```

## Etape 1 — Chercher les causes directes (exceptions, erreurs fatales)

```bash
# Commande principale : erreurs fortes avec contexte
rg -n --no-heading -C 8 "Caused by:|Suppressed:|Exception in thread|FATAL|ERROR|Mixin apply failed|InjectionError|InvalidMixinException|NoClassDefFoundError|ClassNotFoundException" logs crash-reports .

# Version economique en tokens (limite matchs et lignes longues)
rg -n --no-heading -M 500 -m 50 -C 3 "Caused by|Exception|ERROR|FATAL" logs crash-reports .

# Trouver uniquement les fichiers concernes (sans le contenu)
rg -l "ERROR|FATAL|Exception|Caused by|Mixin|Crash|Failed|NoClassDefFoundError|ClassNotFoundException" logs crash-reports .
```

## Etape 2 — Chercher les mods suspects

```bash
rg -n --no-heading -C 4 "Suspected Mods|Mod File|Failure message|Mod Loading Error|Missing or unsupported mandatory dependencies|requires.*version|DuplicateModsFoundException" logs crash-reports .
```

## Etape 3 — Chercher les problemes Mixin

```bash
rg -n --no-heading -C 8 "org.spongepowered.asm.mixin|MixinApplyError|InvalidMixinException|InjectionError|Mixin target|refmap" logs crash-reports .
```

## Etape 4 — Verifier Java / memoire / natives / GPU

```bash
rg -n --no-heading -C 5 "java.version|OpenJDK|HotSpot|OutOfMemoryError|Metaspace|Access violation|EXCEPTION_ACCESS_VIOLATION|lwjgl|OpenGL|GLFW|NVIDIA|AMD|Intel" logs crash-reports .
```

## Etape 5 — Lecture ciblee de la fin d'un log

```bash
# PowerShell : derniere 250 lignes de latest.log
Get-Content .\logs\latest.log -Tail 250

# Git Bash / Linux : derniere 250 lignes
tail -n 250 logs/latest.log

# Extraire autour d'une ligne specifique (ex: ligne 18420)
sed -n '18380,18480p' logs/latest.log
```

## Etape 6 — Analyse interactive avec lnav

```bash
# Ouvrir tous les logs du dossier en mode interactif
lnav logs/

# Mode headless : afficher les erreurs et warnings
lnav -n -c ':filter-in ERROR|FATAL|WARN|Exception|Caused by' logs/

# Mode headless avec export JSON (pour traitement automatise)
lnav -n -c ':write-json-to /tmp/export.json' logs/
```

## Etape 7 — Combiner fd et rg (trouver + chercher)

```bash
# Trouver tous les fichiers de log et chercher les erreurs dedans
rg -n --no-heading -C 5 "Caused by|Exception|ERROR|FATAL|Mixin" $(fd -t f "latest|debug|launcher_log|crash-|hs_err_pid" .)
```

## Patterns de recherche rapide par symptome

### Crash au demarrage (avant menu principal)

```bash
rg -n --no-heading -C 5 "Registry|Loading|Initialization|PreInit|ModLoadingException|MissingModsException" logs crash-reports .
```

### Crash en jeu (milieu de partie)

```bash
rg -n --no-heading -C 5 "Tick|Entity|World|Chunk|Packet|Network" logs crash-reports .
```

### Crash au chargement d'un monde

```bash
rg -n --no-heading -C 5 "Dimension|Level|Chunk|Region|SaveData|PlayerData" logs crash-reports .
```

### Ecran noir / crash GPU

```bash
rg -n --no-heading -C 5 "OpenGL|GLFW|Framebuffer|Shader|Texture|Render" logs crash-reports .
```

## Utilisation de mclo.gs

```bash
# Partager un log (cree un lien public)
# Limite : 10 MiB / 25 000 lignes
# API analyse sans sauvegarde :
curl -X POST https://api.mclo.gs/1/analyse -F "content=@logs/latest.log"
```

**Attention** : ne pas envoyer de tokens, chemins prives, IP, pseudos, secrets ou logs serveur sensibles a mclo.gs.

## Fallback Windows sans outils Unix

Si `rg` n'est pas disponible, utiliser `Select-String` :

```powershell
# Equivalent de rg pour chercher des erreurs
Get-ChildItem -Recurse -Include "*.log","*.txt" -Path "logs","crash-reports" |
  Select-String -Pattern "ERROR|FATAL|Exception|Caused by|Mixin" -Context 4,4

# Limiter a un fichier specifique
Select-String -Path "logs\latest.log" -Pattern "Caused by" -Context 5,5
```

## Anti-patterns (a eviter absolument)

- `cat logs/latest.log` — sauf si le fichier fait < 50 lignes
- `tail -f` dans un agent — ne termine jamais
- `rg` sans `-m` ou `-M` sur un log de 50 MiB — explosion de tokens
- Envoyer un log complet a mclo.gs sans verifier sa taille
- Lire un fichier binaire ou un world save avec `rg` sans `-I` (pas de fichier binaire)
