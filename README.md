# Fugu cnpcoverlay — workspace local

Workspace modèle local pour configurer un environnement d’agents autour d’un projet Minecraft Forge 1.20.1.

L'index s'appelle cnpcoverlay-api-audit dans GitNexus (pas fugu-cnpcoverlay). Toutes les requêtes MCP doivent utiliser repo: "cnpcoverlay-api-audit" pour ce workspace.

gitnexus analyze --index-only --name cnpcoverlay-api-audit

## Environnement

- Java 17 JDK
- Forge `1.20.1-47.4.20+`
- Minecraft `1.20.1`
- GeckoLib Forge `1.20.1-4.8.3+`
- journeymap-forge `journeymap-forge-1.20.1-6.0.0-beta.5` (API v2)`https://github.com/TeamJM/journeymap-api/tree/1.20.1_2.0`
docs:`https://teamjm.github.io/journeymap-docs/latest/` `https://journeymap.readthedocs.io/en/latest/client/basic-usage/`
- CustomNPCs-1.20.1-GBPort-Unofficial `1.20.1.20260711+` `https://github.com/BetaZavr/CustomNPCs-Unofficial.git`
docs API CNPC:`https://goodbird-git.github.io/CNPC-Unofficial-1.20.1-ScriptingDoc/`
- Projet Gradle: `project-gradle/`
-
TCRcore (fork des losange et fleche)(https://github.com/P1neapplell0/TCRCore.git)


| Ressource             | Valeur                                           |
| --------------------- | ------------------------------------------------ |
| notre Depot GitHub    | `https://github.com/armandpesme/fugu-cnpcoverlay` |
| Schema GitNexus local | <http://localhost:4747/>                         |

## Contexte

Ce mod a pour objectif de  permettant d’afficher, via une commande accessible à tous les joueurs, une interface GUI/HUD/UI. Cette interface reprend les informations du GUI de suivi des quêtes de Custom NPC, avec un rendu visuel amélioré et des fonctionnalités supplémentaires. Le mod est entièrement client-side, conçu pour être hautement compatible avec les autres mods, et fonctionne correctement sur les serveurs multijoueurs
Le mod **CNPCoverlay** est un mod entièrement client-side pour Minecraft 1.20.1 Forge.
Il récupère les informations des quêtes du mod **CustomNPCs Unofficial** afin de les afficher dans une interface HUD/GUI personnalisée, accessible aux joueurs via une commande.
La description d’une quête CustomNPCs peut contenir des lignes techniques ou des métadonnées commençant par le caractère `#`.
Exemle :
```text
#!1-1000.2000.100.30 #!2-1500.2500.101.10 #!3-2500.1200.60.40
#?-110.1600.120
Aller secourir les Riri de la forêt, vite !

## Objectif

CNPCoverlay est un mod **client-side** Forge 1.20.1 qui enrichit le suivi des quêtes CustomNPCs Unofficial :

1. **HUD de suivi** : interface GUI listant les quêtes, avec cases à cocher individuelles pour choisir les quêtes affichées.
2. **Persistance du suivi** : les quêtes cochées sont sauvegardées localement par contexte (serveur IP:port ou monde solo) et restaurées après reconnexion.
3. **Marqueurs JourneyMap** : les quêtes suivies contenant des métadonnées cachées (`#!` objectif, `#?` rendu) génèrent des marqueurs visibles sur la carte complète et la mini-map.
4. **HUD directionnel en jeu** : un losange (cible visible) ou une flèche (cible hors champ) indique la direction et la distance des objectifs suivis, directement dans le monde.

Tous les rendus (symboles, losanges, cercles, flèches) sont générés dynamiquement en Java, sans aucun asset PNG.

## Contrainte

| # | Règle | Portée |
|---|-------|--------|
| 1 | Les lignes `#` sont conservées intactes dans CustomNPCs — CNPCoverlay les masque uniquement à l'affichage | Données |
| 2 | Aucune écriture, modification ou normalisation des descriptions CustomNPCs | Données |
| 3 | Rendu 100 % Java : symboles, losanges, cercles, flèches, chiffres, transparence — **aucun PNG, JPG, GIF, WebP** | Rendu |
| 4 | JourneyMap = dépendance **optionnelle** client-side — CNPCoverlay fonctionne sans, aucun crash si absent | Compatibilité |
| 5 | Aucune classe, méthode ou API JourneyMap/CustomNPCs inventée — se référer aux sources locales `docs/mods/` | API |
| 6 | Client-side uniquement — aucun paquet serveur, aucun impact dédié | Architecture |
| 7 | Séparation stricte `client` / `server` / `common` — toute synchro passe par packet ou capability explicite | Architecture |
| 8 | Forge 1.20.1, Java 17 — pas de Fabric, NeoForge, Loom, Yarn | Build |
| 9 | Pas de parsing, I/O disque ou réflexion CustomNPCs dans la boucle de rendu | Performance |
| 10 | Les IDs registry sont plats (`foo_bar`) → modèle item dans `assets/<modid>/models/item/foo_bar.json` | Assets |



## Sources du workspace

| Chemin               | Role                                            |
| -------------------- | ----------------------------------------------- |
| `AGENTS.md`          | Instructions operationnelles chargees par Codex |
| `.codex/config.toml` | Configuration projet Codex                      |
| `.codex/agents/`     | Agents personnalises Codex                      |
| `.agents/skills/`    | Skills de depot Codex                           |
| `PLANS.md`           | Etat, decisions et reprise du travail           |
| `project-gradle/`    | Projet Gradle du mod                            |
| `datapacks/`         | Datapacks crees ou modifies                     |
| `resourcepacks/`     | Resource packs crees ou modifies                |

| Chemin                                          | Role                                                 |
| ----------------------------------------------- | ---------------------------------------------------- |
| `docs/mods/api/journeymap-api-1.20.1_2.0`       | Api du mods Journeymap                               |
| `docs/mods/sources/TCRCore-master`              | Mods sources de tcr core, pour fork des fonctionalité|
| `docs/mods/sources/CustomNPCs-Unofficial-1.20.1`| Mods sources de cnpc                                 |
|`docs\mods\archive\cnpcoverlay-2.1.0`            | Version fonctionnel du mod avant evolution démarrer  |

`README.md` presente le workspace aux humains. Codex charge automatiquement `AGENTS.md`; il ne traite pas ce README comme instruction de projet sauf configuration explicite d'un fallback.

## Utilisation avec un agent

1. Ouvrir le dépôt dans l’outil d’agents choisi.
2. Vérifier que le projet est approuvé afin que `.codex/config.toml` soit chargé.
3. Utiliser la copie locale pour le travail direct ou un worktree pour une tâche parallèle isolée.
4. Conserver l'etat de reprise dans `PLANS.md` uniquement.
5. Executer `scripts/quick_validate.py` apres une modification de la stack Codex.

## Documentation officielle

- [Models](https://learn.chatgpt.com/docs/models)
- [Configuration Reference](https://learn.chatgpt.com/docs/config-file/config-reference)
- [Subagents](https://learn.chatgpt.com/docs/agent-configuration/subagents)
- [AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md)
- [Build skills](https://learn.chatgpt.com/docs/build-skills)
- [Git worktrees](https://learn.chatgpt.com/docs/environments/git-worktrees)
- [Agent approvals and security](https://learn.chatgpt.com/docs/agent-approvals-security)

## Conditions d'acceptation

1. Objectif du workspace atteint ou blocage documente.
2. Erreurs detectees corrigees ou signalees.
3. Code compile lorsqu'un changement Java ou Gradle est effectue.
4. `runClient` reussit si le comportement en jeu, le rendu ou les Mixins changent.
5. Options de debug ou de garde strictes retirees avant une release finale.
6. `PLANS.md` reflete l'etat utile a la reprise.
7. Le JAR produit est lie apres un build reussi.

Si retour utilisateur positif faire Conditions de release finale.


## Conditions de release finale

1. Conditions d'acceptation validees.
2. Build de developpement genere et valide.
3. JAR sources versionne genere, par exemple `[nom-du-mod]-[version]-sources.jar`.
4. Ne jamais creer de fichier litteral `mod-source.jar`.
5. Version incrementee selon la politique du projet lorsqu'elle sera definie.
6. `.\gradlew.bat clean build` reussi.
7. Test manuel final positif si requis.
