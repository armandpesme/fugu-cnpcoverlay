# Plan : Initialiser PLANS.md

## Résumé

`PLANS.md` est vide alors que le projet CNPCoverlay a déjà un jalon défini dans `README.md`. Il faut l'initialiser avec l'état canonique de progression, les décisions et la prochaine action concrète.

## Contexte projet (extrait de README.md)

- **Mod** : CNPCoverlay, Minecraft 1.20.1 Forge, entièrement client-side
- **Dépendances** : CustomNPCs Unofficial, JourneyMap API 1.20.1, GeckoLib 4.8.3+, Epic Fight
- **Workspace** : dépôt GitHub armandpesme/fugu-cnpcoverlay\_chatgpt-codex\_2.0.0
- **Indexation** : GitNexus local sur [http://localhost:4747/](http://localhost:4747/)

## Jalon actuel — Marqueurs JourneyMap depuis métadonnées cachées

Le mod doit lire des métadonnées encodées dans les descriptions de quêtes CustomNPCs (lignes `#...`) pour créer des marqueurs JourneyMap.

### Format des métadonnées

- `#!<index>-<x>,<z>,<y>,<radius>` → objectif incomplet (violets, symbole `!`)
- `#?-<x>,<z>,<y>` → quête à rendre (orange, symbole `?`, losange)
- Plusieurs entrées peuvent être collées sur une même ligne sans séparateur
- Les coordonnées sont au format **X,Z,Y** (attention : l'API JourneyMap attend probablement X,Y,Z)

### Règles d'affichage

- Quête non suivie → aucun marqueur
- Quête suivie non terminée → marqueurs `!` pour les objectifs incomplets seulement
- Quête suivie à 100% → marqueur `?` à la place des `!`
- Les marqueurs sont supprimés à la déconnexion/changement de serveur/monde

### Parser attendu

- Structure `QuestMapMetadata` avec `objectiveMarkers: List<ObjectiveMarker>` et `turnInMarker: Optional<TurnInMarker>`
- Ignorer les lignes non `#`, logger les entrées invalides sans crasher
- Ne jamais modifier la description originale CustomNPCs

### IDs de marqueurs JourneyMap

- Format : `cnpcoverlay:<serverKey>:<playerUuid>:<questId>:objective:<index>`
- Format turn-in : `cnpcoverlay:<serverKey>:<playerUuid>:<questId>:turnin`

### Cycle de mise à jour

Mettre à jour les marqueurs lors de : chargement des quêtes, changement de statut suivi, changement de progression, passage à 100%, changement de dimension, changement de serveur/monde, déconnexion.

## Travail déjà fait / à vérifier

- [ ] Vérifier si la persistance du suivi des quêtes (par joueur, serveur et quête) est déjà implémentée — c'est un prérequis
- [ ] Explorer l'API JourneyMap dans `docs/mods/api/journeymap-api-1.20.1_2.0/` via GitNexus
- [ ] Inspecter les sources CustomNPCs dans `docs/mods/sources/CustomNPCs-Unofficial-1.20.1/` pour comprendre comment lire les descriptions

## Prochaine action concrète

1. **Explorer l'API JourneyMap** avec GitNexus pour identifier les classes : `Waypoint`, `Marker`, `Overlay`, `Polygon`/`Circle`, et les méthodes de création/suppression dynamique
2. **Explorer les sources CustomNPCs** pour trouver comment accéder aux descriptions de quêtes depuis un mod client
3. **Vérifier l'état de la persistance** du suivi des quêtes
4. **Créer le parser** de métadonnées (`QuestMapMetadata` + `ObjectiveMarker` + `TurnInMarker`) dans le projet Gradle
5. **Implémenter le cycle de vie** des marqueurs JourneyMap
6. **Tester** : quêtes non suivies, objectifs multiples, passage à 100%, reconnexion, métadonnées invalides

## Risques

- **Ordre des coordonnées** : le format projet est X,Z,Y mais l'API JourneyMap attend probablement X,Y,Z — risque de bug si conversion non explicite
- **API JourneyMap client-only** : vérifier que l'API permet un rendu 100% client sans nécessiter de packet serveur
- **Doublons de marqueurs** : les IDs stables et la suppression avant refresh sont critiques
- **Persistance du suivi** : si elle n'existe pas encore, c'est un prérequis non trivial à implémenter d'abord

## Décisions enregistrées

- Les lignes `#` ne sont jamais supprimées ni modifiées dans CustomNPCs — lecture seule
- Le marqueur jaune `!` pour quêtes disponibles est préparé dans le modèle mais pas activé (pas de source fiable pour l'instant)
- Tooltips : format défini, mais ne pas bloquer le jalon si l'API JourneyMap ne supporte pas exactement le rendu demandé

