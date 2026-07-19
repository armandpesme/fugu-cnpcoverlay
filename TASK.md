# TASK.md — CNPCoverlay : checklist d'implémentation

> **Projet :** FuguTeams — CNPCoverlay  
> **Date :** 2026-07-12

> **Jalon courant :** version 3.0.0 — 2026-07-13

---

## Phase 0 — Sauvegarde et état Git

- [x] Lire `AGENTS.md` et `PLANS.md`
- [x] Scan GitNexus
- [x] Vérifier `git status`, pas de changements humains à risque
- [ ] Commit de sauvegarde si requis

## Phase 1 — Audit réel des API

- [x] Lister les classes réelles CustomNPCs : `Quest`, `QuestData`, `PlayerQuestData`, `getObjectives`, `isCompleted`
- [x] Lister les classes réelles JourneyMap : `IClientPlugin`, `IClientAPI`, `waypoint`, `overlay`, `renderer`, `layer`
- [x] Confirmer signatures, lifecycle, mécanisme de plugin client
- [x] Identifier les stubs à supprimer dans `project-gradle/src/main/java/journeymap/api/**`
- [x] Déterminer l'artifact et le repository exact pour la dépendance JourneyMap
- [x] Écrire les découvertes dans `PLANS.md`

**Critère de sortie :** aucune API JourneyMap supposée ; toutes les signatures vérifiées contre `docs/mods/api/journeymap-api-1.20.1_2.0/`.

## Phase 2 — Corriger la persistance

- [x] Introduire une clé de contexte `multiplayer:<host>:<port>` / `singleplayer:<levelId>`
- [ ] Distinguer les mondes solo par identifiant stable (dossier de sauvegarde, `levelId`)
- [x] Toujours vider l'état mémoire avant d'appliquer les données chargées
- [x] Reset complet au changement de serveur/monde (vider mémoire, marqueurs, clé)
- [x] Sauvegarde sur changement réel uniquement (pas d'I/O par tick)
- [x] Écriture atomique (fichier temporaire → rename)
- [x] Fallback si `ATOMIC_MOVE` non supporté
- [x] Gestion JSON corrompu : log warning, copie `.broken-<timestamp>`, état vide
- [ ] IDs obsolètes : ne pas supprimer immédiatement si erreur provider transitoire
- [ ] Tests unitaires (voir section Tests)

**Critère de sortie :** sélection exactement restaurée dans tous les contextes.

## Phase 3 — Enrichir le modèle des objectifs

- [x] Créer `QuestObjectiveSnapshot` : index (1-based), text, current, maximum, completed
- [x] Créer `QuestSnapshot` : id, category, title, rawLogText, objectives[], progress, completed, completerName
- [x] Identifier la source fiable de complétion individuelle dans CustomNPCs
- [x] Ne pas déduire `completed` de la couleur du texte ou de la chaîne affichée
- [ ] Différencier « aucun objectif » et « objectifs non disponibles »
- [ ] Tests unitaires

**Critère de sortie :** le planner sait quels objectifs sont réellement incomplets.

## Phase 4 — Stabiliser le parser

- [ ] Regex globale `#!<index>-<x>,<z>,<y>,<radius>` (avec support négatifs)
- [ ] Regex globale `#?-<x>,<z>,<y>` (avec support négatifs)
- [x] Variantes réelles `#?--<x>,<z>,<y>` et `#?<index>--<x>,<z>,<y>` couvertes
- [ ] Support entrées multiples sur une ligne (`#!1-... #!2-...`)
- [ ] Support zéros initiaux (`01`, `0063`, `00010`)
- [ ] Validation : index ≥ 1, rayon ≥ 0, pas de dépassement int
- [ ] Entrées invalides ignorées sans crash, log debug unique
- [ ] Déduplication (dernière entrée valide gagnante)
- [ ] Tests unitaires complets (25+ cas)

**Critère de sortie :** tous les exemples du protocole v1 passent.

## Phase 5 — Planner pur

- [x] Implémenter `QuestMarkerPlanner` sans aucune dépendance JourneyMap
- [ ] Entrées : `QuestSnapshot`, `QuestMapMetadata`, `followed`, dimension
- [ ] Sortie : liste de `desiredMarkers` (type, coordonnées, couleur, tooltip)
- [ ] Règles métier :
  - Non suivie → aucun marqueur
  - Suivie + objectif incomplet → `!` + cercle
  - Objectif terminé → disparaît individuellement
  - Quête 100% → seulement `?` (si `#?` existe)
  - Quête 100% sans `#?` → aucun marqueur
  - Index hors liste → ignoré, log debug
- [ ] Tests unitaires (10+ cas)

**Critère de sortie :** logique métier entièrement testable sans lancer Minecraft.

## Phase 6 — Vraie intégration JourneyMap

- [ ] Supprimer tous les stubs `project-gradle/src/main/java/journeymap/api/**`
- [x] Configurer la dépendance JourneyMap réelle dans `build.gradle`
- [x] Déclarer JourneyMap optionnel dans `mods.toml`
- [x] Implémenter `JourneyMapClientBridge`
- [ ] Registre local `Map<MarkerId, RenderedMarkerHandle>`
- [ ] Diff `desiredMarkers` Δ `activeMarkers` → create / update / remove
- [x] `removeAllMarkers()` supprime réellement tous les éléments
- [ ] Nettoyage à la déconnexion, changement de contexte, arrêt monde client
- [ ] Initialisation lazy si JourneyMap charge après CNPCoverlay
- [ ] Tous les appels sur le thread client

**Critère de sortie :** build réussi contre l'API JourneyMap réelle, sans stubs dans le JAR.

## Phase 7 — Renderer Java

- [ ] Symboles `!` et `?` dessinés en Java
- [ ] Numéros d'objectif (`1`, `2`, `3`) dessinés en Java
- [ ] Losanges dessinés en Java
- [ ] Cercles de rayon et remplissage transparent en Java
- [ ] Transparence : symbole 100%, contour losange 80-100%, contour cercle 60-80%, remplissage 15-30%
- [ ] Map complète ET mini-map
- [ ] Clamp de taille en pixels (zoom/dézoom)
- [x] Assets PNG de décision ajoutés explicitement : flèche, icône de quête et icône de quête secondaire
- [ ] Pas de dépendance à une texture JourneyMap prédéfinie
- [ ] Inspection du JAR : absence de `journeymap/api/**` et de nouveaux assets image

**Critère de sortie :** inspection du JAR et contrôle visuel validés.

## Phase 8 — Tooltips

- [ ] Hitbox cohérente avec le symbole sur la carte complète
- [ ] Contenu objectif : nom quête, texte objectif, progression, statut, coordonnées, rayon
- [ ] Contenu rendu : nom quête, statut, destination, coordonnées
- [ ] Pas de ligne brute de métadonnées affichée
- [ ] Mini-map : pas de tooltip exigé si API non adaptée
- [ ] Hitbox masquée quand le marqueur est masqué

## Phase 9 — Runtime et performances

- [x] `runClient` sans JourneyMap → tout fonctionne
- [ ] `runClient` avec JourneyMap → marqueurs actifs
- [ ] Scénarios fonctionnels A à F (voir section Scénarios)
- [ ] Aucun parsing par frame
- [ ] Aucune I/O disque par frame
- [ ] Aucune réflexion CustomNPCs par frame
- [ ] Pas d'allocation massive dans la boucle de rendu
- [ ] Fingerprint des marqueurs pour éviter recréation inutile

### Régression mirroring 2026-07-13

- [x] Lire le log réel du profil FuguDreams
- [x] Confirmer le JAR CustomNPCs réellement chargé avec `javap`
- [x] Reproduire le contrat 2.1.0 : `completerNpc` + `questInterface.getObjectives(Player)`
- [x] Ajouter un test de non-régression rouge puis vert
- [x] Tester directement la découverte contre le JAR CustomNPCs du workspace
- [x] `gradlew test` et `gradlew build` réussis
- [ ] Re-test utilisateur : les quêtes CustomNPCs apparaissent dans CNPCoverlay

## Phase 10 — Finalisation

- [x] Mise à jour `PLANS.md`
- [x] `clean build` réussi
- [x] JAR inspecté : pas de stubs, pas de PNG
- [x] `mods.toml` : JourneyMap optionnel
- [x] Tests unitaires tous verts
- [ ] Commit et push
- [ ] Scan GitNexus final

---

## HUD directionnel (losange/flèche)

### Implémentation

- [ ] Audit GitNexus de `docs/mods/sources/TCRCore-master/` → projection, flèche, distance
- [ ] Implémenter `HudDirectionalRenderer`
- [ ] Projection monde → écran via caméra de rendu réelle (matrice vue + projection + FOV + partialTick)
- [ ] Distance euclidienne 3D joueur → cible, affichée en mètres (`8 m`, `47 m`)
- [ ] Losange projeté quand cible visible (dans champ de vision)
- [ ] Flèche directionnelle quand cible hors champ
- [ ] Cible derrière la caméra : direction stable, pas d'oscillation autour de 180°
- [ ] Hystérésis au bord du champ pour éviter scintillement losange/flèche
- [ ] Zone sûre pour les flèches (tenir compte mini-map, HUD, hotbar, GUI scale)
- [ ] Première et troisième personne
- [ ] Masquage F1, écrans bloquants, JourneyMap plein écran
- [ ] Nettoyage à la déconnexion

### Critères HUD

- [ ] Losange à la position projetée quand cible visible
- [ ] Distance toujours affichée
- [ ] Losange → flèche hors champ, pointe correctement 360°
- [ ] `!`, `?` et numéro identifiables
- [ ] Aucun PNG
- [ ] Aucun indicateur pour quête non suivie
- [ ] Aucun indicateur fantôme après déconnexion

---

## Tests unitaires

### Parser (25+ cas)

- [ ] `null`
- [ ] Chaîne vide
- [ ] Description sans métadonnée
- [ ] Ligne `#` inconnue (ignorée)
- [ ] `#!1-7896,4566,4563,12` → objectif simple
- [ ] `#!1-7896,4566,4563,12#!2-7816,4576,4363,01` → plusieurs collés
- [ ] `#!1--1269,2534,105,10` → x négatif
- [ ] `#!1-1200,-4500,64,8` → z négatif
- [ ] `#!1-1200,4500,-32,4` → y négatif
- [ ] `#!1--1200,-4500,-32,15` → trois négatifs
- [ ] `#?--1269,2534,105` → turn-in x négatif
- [ ] `#!1-7896,4566,4563,01` → rayon zéro initial
- [ ] `#!1-00010,00020,00030,00005` → zéros initiaux
- [ ] Espaces autour des virgules
- [ ] Index 0 → invalide
- [ ] Rayon négatif → invalide
- [ ] Nombre invalide `abc` → ignoré
- [ ] Entrée tronquée `#!1-100,200` → ignorée
- [ ] Plusieurs entrées dont une invalide → valides conservées
- [ ] Texte avec `#` au milieu → conservé
- [ ] Ligne précédée d'espaces → masquée
- [ ] Doublon → dernière gagnante
- [ ] Dépassement entier
- [ ] `#!1-100,200,300,10` + `#?-400,500,600` dans même description

### Filtrage de description

- [ ] Aucun `#` → texte identique
- [ ] Ligne commençant par `#` → masquée
- [ ] Espaces puis `#` → masquée
- [ ] `#` au milieu → conservé
- [ ] Plusieurs lignes techniques → masquées
- [ ] Paragraphes conservés
- [ ] Description 100% technique → texte vide
- [ ] Codes de formatage conservés
- [ ] Pas d'espace vertical artificiel

### Persistance

- [ ] Sauvegarde + rechargement IDs
- [ ] Sauvegarde vide remplace ancien état
- [ ] Deux joueurs même serveur
- [ ] Un joueur deux serveurs
- [ ] Deux mondes solo distincts
- [ ] Fichier absent → état vide
- [ ] Fichier corrompu → état vide, log warning
- [ ] Migration de version
- [ ] Écriture atomique
- [ ] ID actif invalide géré
- [ ] Changement de contexte sans redémarrage

### Planner

- [ ] Non suivie → aucun marqueur
- [ ] Suivie + objectif incomplet → `!` + cercle
- [ ] Objectif terminé → aucun pour cet objectif
- [ ] Objectif 1 terminé, 2 incomplet → seulement objectif 2
- [ ] 100% → seulement `?`
- [ ] 100% sans `#?` → aucun marqueur
- [ ] Index inexistant → ignoré
- [ ] Dimension différente
- [ ] Doublon
- [ ] Désuivi → suppression

### Diff

- [ ] Création initiale
- [ ] Aucun changement → aucune opération
- [ ] Tooltip modifié → update
- [ ] Coordonnées modifiées → update
- [ ] Désuivi → remove
- [ ] Déconnexion → remove all
- [ ] Quête disparue → remove
- [ ] Contexte changé → suppression ancienne + création nouvelle

---

## Tests d'intégration

- [ ] **Sans JourneyMap** : lancement, GUI, HUD, checkbox, persistance OK, aucune erreur classloading
- [ ] **Avec JourneyMap** : plugin initialisé, marqueurs map + mini-map, symboles Java, tooltips, cercles, suppression

### Décision assets 2026-07-13

- `quest_arrow.png` remplace toutes les flèches directionnelles du HUD.
- `quest_icon.png` remplace l'icône de remise `#?`.
- `side_quest_1.png` remplace l'icône d'objectif `#!`.
- Les fichiers sont copiés depuis `docs/decisions/` vers `src/main/resources/assets/cnpcoverlay/textures/markers/` et vérifiés dans le JAR final.

### Scénarios fonctionnels

- [ ] **A — Objectif négatif** : `#!1--1269,2534,105,10` → marqueur violet `!1` à X=-1269 Y=105 Z=2534, cercle 10 blocs
- [ ] **B — Trois objectifs** : `#!1-...#!2-...#!3-...` → 3 marqueurs, disparition individuelle à progression
- [ ] **C — Quête à rendre** : `#?-4562,4457,2410` → `?` orange uniquement à 100%, supprimé après rendu
- [ ] **D — Persistance** : cocher A+C, déco, relancer, reco → A et C suivies, B non, marqueurs A+C restaurés
- [ ] **E — Changement serveur** : suivre quête serveur A → menu → serveur B → aucun marqueur de A, données B séparées
- [ ] **F — Deux mondes solo** : suivi distinct par sauvegarde

---

## Commandes de validation

Depuis `project-gradle/` :

```powershell
# Tests unitaires
.\gradlew.bat test

# Build
.\gradlew.bat build

# Runtime client
.\gradlew.bat runClient

# Build final (finalisation uniquement)
.\gradlew.bat clean build
```

Vérifications post-build :

- [x] `jar tf build/libs/*.jar` → exactement `quest_arrow.png`, `quest_icon.png`, `side_quest_1.png`
- [ ] `jar tf build/libs/*.jar | grep "journeymap/api"` → aucun résultat
- [ ] `mods.toml` : JourneyMap en dépendance optionnelle
- [ ] Test sans JourneyMap dans `mods/`
- [ ] Test avec JourneyMap dans `mods/`

---

## Compte rendu final (template)

À remplir en fin de jalon :

1. Résumé des changements :
2. Fichiers modifiés :
3. Classes CustomNPCs inspectées :
4. Classes JourneyMap réelles utilisées :
5. Suppression/remplacement des stubs :
6. Format exact des IDs JourneyMap :
7. Format exact de persistance :
8. Stratégie de changement de contexte :
9. Stratégie de diff :
10. Preuve coordonnées négatives fonctionnelles :
11. Preuve absence de PNG :
12. Preuve `removeAllMarkers` fonctionnel :
13. Tests exécutés :
14. Résultat `build` :
15. Résultat `runClient` :
16. Limites restantes :
17. Chemin du JAR :
18. Prochaine action :
