# PLANS.md — CNPCoverlay : état canonique

> **Projet :** Fugu'Dreams_Online / FuguTeams  
> **Mod :** CNPCoverlay  
> **Cible :** Minecraft 1.20.1 — Forge 47.4.20+ — Java 17  
> **Signature :** `FuguTeams`  
> **Dernière mise à jour :** 2026-07-13

---

## 1. Décision principale

CNPCoverlay exploite des métadonnées techniques stockées dans les descriptions des quêtes CustomNPCs Unofficial pour :

- Afficher un HUD directionnel (losange/flèche + distance) dans le monde
- Générer des marqueurs JourneyMap (carte + mini-map)
- Persister les choix de suivi par contexte (serveur ou monde solo)

Règles fondamentales : voir `README.md → ## Contrainte`.

---

## 2. Audit de l'état actuel (2026-07-12)

### 2.1 Fichiers existants dans `project-gradle/src/main/java/`

```text
com/cnpcoverlay/cnpcoverlaymod/client/integration/journeymap/QuestMapMetadata.java
com/cnpcoverlay/cnpcoverlaymod/client/integration/journeymap/CnpcOverlayJourneyMapPlugin.java
com/cnpcoverlay/cnpcoverlaymod/client/integration/journeymap/JourneyMapMarkerManager.java
com/cnpcoverlay/cnpcoverlaymod/client/quest/QuestPersistenceManager.java
com/cnpcoverlay/cnpcoverlaymod/client/quest/QuestTrackerState.java
```

Le parser lit déjà `#!` et `#?`. La persistance stocke les IDs suivis en JSON local.

### 2.2 Problèmes critiques identifiés

| # | Problème | Impact | Priorité |
|---|----------|--------|----------|
| 1 | **Stubs JourneyMap** dans `journeymap/api/**` — signatures inventées, compilation contre une fausse API | Échec runtime avec les vraies classes | 🔴 Bloquant |
| 2 | **Dépendance Gradle non configurée** — `build.gradle` ne déclare pas la vraie API JourneyMap | Build non reproductible | 🔴 Bloquant |
| 3 | `removeAllMarkers()` ne supprime rien — log uniquement | Marqueurs fantômes après déconnexion | 🔴 Bloquant |
| 4 | Suppression par boucle `1..20` — IDs devinés au lieu d'enregistrés | Suppression incomplète ou collision | 🔴 Bloquant |
| 5 | Objectifs terminés non gérés individuellement — `List<String> objectives` + `float progress` insuffisants | Tous les marqueurs `#!` restent jusqu'à 100% | 🔴 Bloquant |
| 6 | `persistenceLoaded` chargé une seule fois par session | Pas de reset au changement de serveur/monde | 🔴 Bloquant |
| 7 | Sauvegarde vide non appliquée — ne vide la mémoire que si IDs ≠ vide | Ancien état conservé après reset | 🔴 Bloquant |
| 8 | Clé solo `(singleplayer)` fusionne tous les mondes | Collision entre sauvegardes solo | 🟠 Majeur |
| 9 | Rendu Java-only non vérifié — le symbole JourneyMap prédéfini peut dépendre d'une texture | Contrainte potentiellement violée | 🟠 Majeur |
| 10 | Tooltips non implémentés | Spécification non couverte | 🟡 Mineur |

---

## 3. Architecture cible

Les noms peuvent être adaptés, les responsabilités doivent rester séparées.

| Composant | Responsabilité | Dépend de |
|-----------|---------------|-----------|
| `QuestDescriptionView` | Conserver texte brut, produire version filtrée (lignes `#` masquées) | Rien |
| `QuestMapMetadataParser` | Parser `#!` et `#?`, supporter négatifs, entrées collées, validation | Rien |
| `QuestObjectiveSnapshot` | Record : index, texte, progression, completed (1-based) | Rien |
| `QuestSnapshot` | Record : id, category, title, rawLogText, objectives[], progress, completed | `QuestObjectiveSnapshot` |
| `QuestTrackingStore` | Persistance JSON par contexte (serveur IP:port / monde solo), écriture atomique, migration | Rien |
| `QuestMarkerPlanner` | Produire `desiredMarkers` depuis état + métadonnées — testable sans JourneyMap | `QuestSnapshot`, `QuestMapMetadata` |
| `JourneyMapClientBridge` | Création/update/suppression via vraie API JourneyMap, registre local, diff | API JourneyMap réelle |
| `JourneyMapJavaRenderer` | Dessiner symboles, chiffres, losanges, cercles, flèches — 100% Java, aucun PNG | `GuiGraphics`, API JourneyMap |
| `HudDirectionalRenderer` | Losange visible / flèche hors champ + distance dans le HUD monde | `QuestMarkerPlanner`, caméra Minecraft |
| `QuestMarkerCoordinator` | Écouter changements (suivi, progression, contexte), orchestrer planner+bridge | Tous les composants |

### 3.1 Pipeline de données

```text
CustomNPCs Quest.logText (lecture seule)
    │
    ├── QuestDescriptionView → texte filtré (affichage CNPCoverlay)
    │
    └── QuestMapMetadataParser → QuestMapMetadata
            │
            └── QuestMarkerPlanner + QuestTrackingStore
                    │
                    ├── JourneyMapClientBridge → marqueurs map/mini-map
                    └── HudDirectionalRenderer → losange/flèche HUD
```

---

## 4. Protocole de métadonnées v1

### 4.1 Formats

```
#!<index>-<x>,<z>,<y>,<rayon>     → objectif incomplet
#?-<x>,<z>,<y>                     → quête à rendre
```

Ordre des coordonnées : **X, Z, Y** (conversion explicite vers X, Y, Z pour les API Minecraft).

### 4.2 Coordonnées négatives

```
#!1--1269,2534,105,10   → x=-1269, z=2534, y=105, rayon=10
#!2--1200,-4500,64,15   → x=-1200, z=-4500, y=64, rayon=15
#?--1269,2534,105       → x=-1269, z=2534, y=105
```

### 4.3 Référence de parsing

Voir `PLAN_CNPCoverlay_JourneyMap_HUD_v2.md` sections 6.5 à 6.12.

---

## 5. Clé de contexte (persistance)

```
multiplayer:<host>:<port>    → serveur multijoueur
singleplayer:<levelId>       → monde solo (identifiant stable du dossier de sauvegarde)
```

Format JSON : voir `PLAN_CNPCoverlay_JourneyMap_HUD_v2.md` section 8.4.

---

## 6. IDs des éléments JourneyMap

```
cnpcoverlay:<contextHash>:<playerUuid>:<questId>:objective:<index>:marker
cnpcoverlay:<contextHash>:<playerUuid>:<questId>:objective:<index>:radius
cnpcoverlay:<contextHash>:<playerUuid>:<questId>:turnin:marker
```

Registre local : `Map<MarkerId, RenderedMarkerHandle> activeMarkers`.

Mise à jour par diff : `desiredMarkers Δ activeMarkers → create / update / remove`.

---

## 7. Couleurs par défaut

| Type | Couleur | Hex |
|------|---------|-----|
| Objectif incomplet | Violet | `#9B30FF` |
| Quête à rendre | Orange | `#FF8C00` |
| Quête disponible (futur) | Jaune | `#FFD700` |

---

## 8. Phases d'implémentation

| Phase | Description | Statut |
|-------|-------------|--------|
| 0 | Sauvegarde Git, scan GitNexus | ✅ Fait |
| 1 | Audit réel des API CustomNPCs + JourneyMap | ✅ Fait |
| 2 | Corriger la persistance (contexte, reset, sauvegarde vide) | ✅ Fait — couverture complémentaire à renforcer |
| 3 | Enrichir le modèle des objectifs (complétion individuelle) | 🔄 Correctif runtime construit — re-test utilisateur requis |
| 4 | Stabiliser le parser (négatifs, regex, validation) | ✅ Fait |
| 5 | Planner pur (testable sans Minecraft) | ✅ Fait |
| 6 | Vraie intégration JourneyMap (supprimer stubs, build contre API réelle) | ✅ Fait |
| 7 | Renderer HUD/JourneyMap (symboles, textures validées, losanges, cercles, transparence) | ✅ Implémenté — contrôle visuel runtime restant |
| 8 | Tooltips carte complète | ✅ Implémenté — contrôle visuel runtime restant |
| 9 | Runtime, performances, runClient | ❌ NO-GO utilisateur : lancement stable mais mirroring corrigé à revalider |
| 10 | Finalisation (PLANS.md, version, clean build, JAR, commit) | 🔄 Version 3.0.0 construite ; publication Git laissée à l'utilisateur |

---

## 9. Points différés

| Point | Raison |
|-------|--------|
| Marqueur jaune « quête disponible » | Pas de source fiable côté client identifiée |
| Dimension dans les métadonnées | Format non défini |
| Configuration utilisateur avancée (couleurs, opacité, taille) | Post-jalon, après validation du cœur |
| Regroupement de marqueurs | À traiter si chevauchements avérés en jeu |

---

## 10. Prochaine action concrète

1. Lancer `runClient` et inspecter le démarrage sans erreur de classloading.
2. Vérifier visuellement les deux variantes : sans JourneyMap puis avec le JAR local JourneyMap.
3. Terminer la QA, lancer `clean build`, inspecter le JAR et mettre à jour la finalisation.

---

## 12. Progression — jalon d'implémentation du 2026-07-12

### Fait

- Audit direct des sources locales CustomNPCs, JourneyMap v2 et TCRCore. GitNexus n'a pas fourni d'index exploitable (erreur LadybugDB/FTS) ; les décisions sont donc étayées par les sources locales.
- Extraction de l'API JourneyMap v2 locale dans `docs/mods/jar/journeymap-api-forge-1.20.1-2.0.0.jar`, dépendance Gradle `compileOnly` et JourneyMap réel uniquement en `runtimeOnly fg.deobf`.
- Modèles `QuestObjectiveSnapshot` / `QuestSnapshot`, parser validé et dédoublonné, planner pur testé, persistance par contexte et pont JourneyMap v2 sans référence API dans le cœur.
- Rendu JourneyMap/HUD avec les textures validées du dossier `docs/decisions/` et HUD directionnel caméra avec flèche stable derrière la caméra, distance et hystérésis. Cette décision remplace le prototype Java-only précédent.

### Surprises et discovery

- L'ancienne dépendance `blank:journeymap-1.20.1-5.10.3-forge` était absente et toutes ses signatures API v1 étaient incompatibles avec le JAR local JourneyMap 6 beta / API 2.0.0.
- CustomNPCs fournit l'état objectif par `IQuestObjective#isCompleted()` et le champ du PNJ de remise est `completer`, non `completerNpc`.

### Decision log

- Le cœur publie des `DesiredMarker` sans importer JourneyMap ; seul le plugin v2 installe le bridge. Cela préserve le lancement sans JourneyMap.
- Les cercles JourneyMap sont des polygones de 16 sommets et les icônes sont créées en mémoire, afin de respecter la contrainte Java-only / sans PNG.

### Outcome et retrospective

- `project-gradle`: `./gradlew.bat test`, `./gradlew.bat clean build` puis `./gradlew.bat build` réussis le 2026-07-12 sous Java 17.0.19 / Gradle 8.8 (40 tests verts).
- JAR produit : `project-gradle/build/libs/cnpcoverlay-2.2.0.jar`; inspection : aucune entrée `journeymap/api/**` ni image PNG/JPG/JPEG/GIF/WebP.
- `runClient` sans JourneyMap a atteint le thread de rendu, l'audio et les atlases sans erreur CNPCoverlay ; le processus de smoke test a été arrêté ensuite.
- Avec `-PwithJourneyMap`, le JAR local `journeymap-forge-1.20.1-6.0.0-beta.4.jar` échoue avant le chargement de CNPCoverlay : son Mixin cible `Minecraft.m_91156_(ClientLevel)` alors que ce run Forge officiel expose `setLevel(ClientLevel)`. Le même échec apparaît avec et sans remappage ForgeGradle : incompatibilité de l'artefact bêta/environnement, non du bridge CNPCoverlay.

### Reprise agent sans état

Le code compile et les tests sont verts. Pour le contrôle JourneyMap complet, fournir un JAR JourneyMap Forge 1.20.1 compatible avec les mappings officiels/Forge 47.4.20 (ou un environnement de run compatible avec cet artefact), puis lancer `./gradlew.bat runClient -PwithJourneyMap` et valider visuellement les marqueurs. La publication Git est laissée à l'utilisateur conformément à la configuration de ce fil.

---

## 14. Passage en version 3.0.0 — 2026-07-13

### Progression

- Version Forge incrémentée de `2.2.0` à `3.0.0` dans `project-gradle/gradle.properties`.
- `./gradlew.bat build` réussi depuis `project-gradle/` le 2026-07-13 ; `mods.toml` embarque bien `version="3.0.0"`.
- JAR final produit : `project-gradle/build/libs/cnpcoverlay-3.0.0.jar`.
- `./gradlew.bat clean build` réussi depuis `project-gradle/` le 2026-07-13 ; l'ancien artefact 2.2.0 est supprimé du dossier `build/libs`.
- Les finitions restantes sont conservées comme backlog post-3.0.0 dans `TASK.md` : réglages HUD, scénarios runtime complets, validation JourneyMap et durcissement des tests.

### Decision log

- `3.0.0` est traité comme un jalon majeur fonctionnel : le numéro est propagé par `mods.toml` via `${mod_version}` et par le nom du JAR Gradle.

### Reprise agent sans état

Depuis `project-gradle/`, vérifier `build/libs/cnpcoverlay-3.0.0.jar` et son `mods.toml`. Ne pas réintroduire les anciennes références `2.2.0` dans les métadonnées générées.

---

## 13. Régression mirroring CustomNPCs — 2026-07-13

### Progression

- Retour utilisateur : jeu stable, mais écran CNPCoverlay vide malgré plusieurs quêtes actives ; verdict NO-GO.
- Cause reproduite dans le vrai log Modrinth FuguDreams et correctif implémenté dans `CustomNpcsQuestProvider`.
- Trois tests ciblés couvrent le contrat legacy, l'absence de champ completer et le JAR CustomNPCs réellement distribué dans `docs/mods/jar/`.
- Reste : installer le nouveau JAR CNPCoverlay et confirmer visuellement que les quêtes sont de nouveau listées.

### Surprises et discovery

- Le bytecode CustomNPCs réellement chargé (`1.20.1.20260711`) diverge des sources locales : il expose `Quest.completerNpc`, pas `Quest.completer`.
- Ce JAR n'expose pas `Quest.getObjectives(Player)` ; le chemin fonctionnel historique est `Quest.questInterface.getObjectives(Player)`.
- Le provider 2.2.0 rendait ces deux variantes obligatoires pendant une découverte atomique. Le premier `NoSuchFieldException: completer` posait `discoveryFailed=true` définitivement ; `QuestTrackerState` recevait ensuite `List.of()` et vidait l'interface chaque seconde.

### Decision log

- Source de vérité runtime : le JAR exact du modpack, vérifié avec `javap` et par test de découverte, avant les sources locales divergentes.
- La découverte ne dépend plus d'une métadonnée de remise : `completerNpc`, puis `completer`, sinon valeur absente.
- Les objectifs sont lus par `questInterface.getObjectives(Player)`, comme dans la version 2.1.0 fonctionnelle. Une quête défaillante est isolée au lieu d'annuler toute la liste, et la découverte peut réessayer après cinq secondes.

### Outcome et retrospective

- Test rouge initial : `CustomNpcsQuestProviderTest` échouait sur l'absence de `Access.discover` et le contrat runtime non supporté.
- Après correction : `./gradlew.bat test` puis `./gradlew.bat build` réussis depuis `project-gradle/` sous Java 17.0.19 / Gradle 8.8.
- JAR produit : `project-gradle/build/libs/cnpcoverlay-2.2.0.jar`.
- Risque restant : le mirroring doit être confirmé dans le profil FuguDreams connecté, car le run de développement local ne contient pas de données de quêtes serveur.

### Reprise agent sans état

Installer `project-gradle/build/libs/cnpcoverlay-2.2.0.jar` dans le profil de test, se connecter au serveur avec des quêtes actives et ouvrir CNPCoverlay. Le log attendu contient `Intégration CustomNPCs active (completerNpc)` et ne doit plus contenir `NoSuchFieldException: completer`. Si la liste reste vide, relever les nouvelles lignes `Quête CustomNPCs ignorée` ou `Lecture des quêtes CustomNPCs impossible`, désormais visibles au niveau WARN.

---

## 15. Régression HUD de remise de quête — 2026-07-13

### Progression

- Correctif appliqué au HUD de suivi : à 100 % avec un PNJ de remise configuré dans CustomNPCs, les objectifs sont remplacés par `Aller voir <nom du PNJ>`.
- La liste normale des objectifs reste affichée tant que la progression est inférieure à 100 % ou qu'aucun PNJ de remise n'est configuré.

### Surprises et discovery

- Le provider récupérait encore `completerNpc` / `completer`, mais le refactoring de `QuestEntry` avait abandonné cet argument ; le HUD ne pouvait donc plus produire l'instruction de remise.
- Le statut CustomNPCs `isQuestCompleted` n'est pas utilisé pour cette instruction : une quête active doit déjà orienter le joueur dès que ses objectifs atteignent 100 %.

### Decision log

- `QuestEntry.turnInInstruction()` porte la règle pure et testable ; `CnpcOverlayHud` l'emploie pour choisir entre la remise et les objectifs.

### Outcome et retrospective

- Test de régression ajouté dans `QuestEntryTest` : instruction visible à 100 %, absente à 99 %.
- `./gradlew.bat test --tests com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestEntryTest` puis `./gradlew.bat build` ont réussi depuis `project-gradle/` le 2026-07-13.
- JAR produit : `project-gradle/build/libs/cnpcoverlay-3.0.0.jar`.
- Smoke `./gradlew.bat runClient` : le client a atteint le thread de rendu et l'initialisation CNPCoverlay sans erreur, puis a été arrêté après le démarrage. Les données de quête serveur ne sont pas présentes dans cet environnement.

### Reprise agent sans état

Installer `project-gradle/build/libs/cnpcoverlay-3.0.0.jar` dans le profil de test, suivre une quête à 100 % avec un `completerNpc` défini et confirmer que le HUD affiche `Aller voir <PNJ>` à la place des objectifs validés.

---

## 16. Artefact de correctif 3.0.0-fix — 2026-07-13

### Progression

- La version de distribution est maintenant `3.0.0-fix` dans `project-gradle/gradle.properties`; `mods.toml` la reçoit via `${mod_version}`.

### Surprises et discovery

- Aucune dépendance, mapping ni configuration de run n'a été modifié ; le suffixe de version ne change que les métadonnées et le nom de l'artefact.

### Decision log

- Le suffixe `-fix` identifie explicitement le JAR contenant le correctif de remise de quête, sans changer l'identifiant de mod.

### Outcome et retrospective

- `./gradlew.bat build` a réussi depuis `project-gradle/` le 2026-07-13 sous Java 17.0.19 / Gradle 8.8.
- JAR produit : `project-gradle/build/libs/cnpcoverlay-3.0.0-fix.jar`.

### Reprise agent sans état

Distribuer `project-gradle/build/libs/cnpcoverlay-3.0.0-fix.jar`, puis effectuer la validation serveur de la remise de quête documentée au jalon 15.

## 17. Décision assets et parsing — 2026-07-13

### Progression

- Les assets validés dans `docs/decisions/` sont désormais empaquetés dans le mod sous `assets/cnpcoverlay/textures/markers/` : `quest_arrow.png`, `quest_icon.png` et `side_quest_1.png`.
- Le HUD utilise `quest_arrow.png` pour toute flèche hors champ ; les cibles visibles utilisent `quest_icon.png` pour `#?` et `side_quest_1.png` pour `#!`.
- JourneyMap utilise les mêmes textures via `MapImage(ResourceLocation, 16, 16)` ; le générateur `NativeImage` procédural a été retiré.
- Le parser accepte `#?--1235,...` ainsi que la variante tolérée `#?1--1235,...`, sans modifier le format canonique `#?-1235,...`.

### Vérification

- `./gradlew.bat test` réussi le 2026-07-13.
- `./gradlew.bat build` réussi le 2026-07-13 ; JAR : `project-gradle/build/libs/cnpcoverlay-3.0.0-fix.jar`.
- `./gradlew.bat clean build` réussi le 2026-07-13 ; l'artefact ci-dessus provient d'une arborescence reconstruite intégralement.
- `runClient` a atteint le thread de rendu et l'initialisation de CNPCoverlay sans crash ; le processus a été arrêté après le smoke test.
- Le JAR contient bien les trois PNG sous `assets/cnpcoverlay/textures/markers/` et aucun stub `journeymap/api/**`.

### Reprise agent sans état

Installer le JAR dans le profil FuguDreams, ouvrir une quête contenant `#!1--...` et `#?--...`, puis confirmer visuellement les icônes et la flèche. Si un format réel diffère encore, conserver la ligne brute du journal et ajouter un cas de test au parser.

## 18. Correctif projection HUD selon l'altitude — 2026-07-19

### Progression

- Le HUD directionnel projette désormais chaque cible dans la base complète de la caméra (avant, droite, haut) : l'altitude et le pitch participent au test de visibilité comme au positionnement vertical de l'icône.
- Un test de régression couvre les cibles au-dessus et au-dessous, ainsi que le regard vers le haut et le bas.
- Reste : contrôle visuel dans le profil FuguDreams sur une quête dont la cible partage les coordonnées X/Z du joueur, mais pas son altitude.

### Surprises et discovery

- La source appliquait la composante Y de la cible, mais aplatissait auparavant le vecteur caméra sur le plan X/Z. Le pitch était donc ignoré lors de la projection HUD.
- Le format de métadonnées `X, Z, Y` et sa conversion existante vers les API Minecraft ne sont pas concernés.

### Decision log

- Correction limitée à `HudDirectionalRenderer` et à son test unitaire : aucun état serveur, paquet, synchronisation, parser ou marker JourneyMap n'est modifié.
- L'hystérésis de visibilité et le comportement de la flèche pour une cible derrière le joueur sont conservés.

### Outcome et retrospective

- `./gradlew.bat build` a réussi depuis `project-gradle/` le 2026-07-19 sous Java 17.0.19 / Gradle 8.8, tests inclus. JAR produit : `project-gradle/build/libs/cnpcoverlay-3.0.0-fix.jar`.
- `./gradlew.bat runClient` a réussi : monde solo, handshake Forge et arrêt propre, sans erreur CNPCoverlay. Le contrôle n'incluait pas de données de quête réelles.
- GitNexus : impact initial LOW (un consommateur direct ; aucun processus indexé). La détection finale isole le flux de rendu `Render → DrawIcon/DrawArrow`; les changements humains indépendants dans `AGENTS.md` et `README.md` restent hors périmètre.

### Reprise agent sans état

Installer `project-gradle/build/libs/cnpcoverlay-3.0.0-fix.jar` dans le profil FuguDreams et vérifier, sur une cible au même X/Z mais au-dessus puis au-dessous du joueur, que l'icône n'apparaît à l'écran que dans la direction correspondant au regard vertical. Relever une capture ou le log si le comportement diffère.

---

## 19. Icônes JourneyMap 64×64 — 2026-07-19

### Progression

- Les marqueurs d'objectif et de remise de quête utilisent maintenant `side_quest_1-64x.png` et `quest_icon-64x.png` exclusivement dans `JourneyMapMarkerManager`.
- Leur taille de texture et d'affichage JourneyMap est fixée à 64×64 px ; le HUD 3D conserve les références PNG 16×16 existantes.
- Un test unitaire verrouille les deux chemins de ressources JourneyMap et la constante de taille 64.

### Surprises et discovery

- L'API JourneyMap v2 est déclarée `compileOnly`, donc absente du classpath d'exécution JUnit par défaut. Elle est désormais ajoutée via `testRuntimeOnly` pour exécuter le test du pont sans l'embarquer dans le JAR du mod.
- Les tests unitaires n'initialisent pas le bootstrap des registres Minecraft : le test couvre les constantes du pont, tandis que la validation visuelle reste à faire dans un client ayant JourneyMap.

### Decision log

- Les PNG haute définition sont consommés uniquement par `JourneyMapMarkerManager`; aucun changement n'est apporté à `HudDirectionalRenderer`. Cela limite l'effet aux vues JourneyMap (mini-carte et carte plein écran) et préserve le rendu monde/HUD.
- `MapImage` reçoit 64×64, ce qui définit également sa taille d'affichage par défaut dans l'API JourneyMap v2.

### Outcome et retrospective

- Test ciblé réussi : `./gradlew.bat test --tests com.cnpcoverlay.cnpcoverlaymod.client.integration.journeymap.JourneyMapMarkerManagerTest` depuis `project-gradle/`.
- Build réussi : `./gradlew.bat build` depuis `project-gradle/`; JAR produit : `project-gradle/build/libs/cnpcoverlay-3.0.1-alpha.jar`.
- Inspection du JAR : les quatre icônes `quest_icon*.png` et `side_quest_1*.png`, dont les deux variantes `-64x`, sont présentes.
- Risque restant : contrôle visuel requis avec un JAR JourneyMap compatible ; l'environnement de développement local reste bloqué par l'incompatibilité du JAR JourneyMap bêta documentée au jalon 12.

### Reprise agent sans état

Installer `project-gradle/build/libs/cnpcoverlay-3.0.1-alpha.jar` dans un profil avec JourneyMap compatible, puis ouvrir la mini-carte et la carte plein écran pour vérifier les deux marqueurs à 64×64 sans modification du HUD 3D.

## 20. Préparation de distribution 3.0.1 — 2026-07-19

### Progression

- La version de distribution est passée de `3.0.1-alpha` à `3.0.1` dans `project-gradle/gradle.properties`.
- `./gradlew.bat build` a réussi depuis `project-gradle/` ; il compile, exécute les tests et produit le JAR stable.
- Le scan GitNexus incrémental a été rafraîchi après le build.

### Surprises et discovery

- GitNexus fonctionne sans son extension FTS : l'index de symboles reste à jour, mais la recherche plein texte/BM25 est indisponible.
- Un fichier non suivi `quest_arrow-64x.png` mesure 16×16 et n'est pas référencé ; son inclusion dans le commit requiert une décision explicite.

### Decision log

- Le suffixe `-alpha` est retiré pour identifier le JAR de distribution comme `3.0.1` sans modifier l'identifiant du mod ni ses dépendances.

### Outcome et retrospective

- JAR vérifié : `project-gradle/build/libs/cnpcoverlay-3.0.1.jar` ; son `META-INF/mods.toml` contient `version="3.0.1"` et les icônes JourneyMap 64×64 attendues.
- Risque restant : validation visuelle avec un JAR JourneyMap compatible, inchangée depuis le jalon 19.

### Reprise agent sans état

Décider du traitement de `quest_arrow-64x.png`, puis effectuer la détection GitNexus des changements et publier le commit de distribution 3.0.1 sur `master`.

## 11. Références

| Ressource | Chemin/URL |
|-----------|------------|
| Spécification complète | `PLAN_CNPCoverlay_JourneyMap_HUD_v2.md` |
| Projet Gradle | `project-gradle/` |
| Sources CustomNPCs | `docs/mods/sources/CustomNPCs-Unofficial-1.20.1/` |
| API JourneyMap | `docs/mods/api/journeymap-api-1.20.1_2.0/` |
| TCRCore (référence HUD) | `docs/mods/sources/TCRCore-master/` |
| Tâches actionnables | `TASK.md` |
| Dépôt GitHub | `https://github.com/armandpesme/fugu-cnpcoverlay_chatgpt-codex_2.0.0` |

## 21. Suivi activé par défaut pour une nouvelle quête — 2026-07-19

### Progression

- Une quête qui apparaît après l'initialisation du contexte joueur est ajoutée automatiquement au suivi local.
- Le joueur peut ensuite la décocher normalement ; ce choix est conservé après un rafraîchissement ou une reconnexion.
- Les quêtes déjà actives lors de la première synchronisation ne sont pas cochées en masse : elles constituent la baseline initiale.

### Surprises et discovery

- La persistance ne pouvait auparavant pas distinguer une quête nouvelle d'une quête volontairement décochée. Comparer uniquement aux IDs suivis aurait donc recoché les choix manuels.
- La migration des fichiers JSON existants est implicite : l'absence de `seenQuestIds` crée une baseline au premier rafraîchissement, sans modifier les choix de suivi déjà sauvegardés.

### Decision log

- `QuestTrackerState` mémorise les IDs vus par contexte et ne coche automatiquement que les IDs absents de cet ensemble.
- `QuestPersistenceManager` sauvegarde `seenQuestIds` séparément de `followedQuestIds` afin que le décochage manuel reste prioritaire.

### Outcome et retrospective

- Test rouge vérifié : les nouveaux contrats de persistance et de détection n'existaient pas encore.
- Tests ciblés réussis : `./gradlew.bat test --tests com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestPersistenceManagerTest --tests com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestTrackerStateTest`.
- Build réussi : `./gradlew.bat build` depuis `project-gradle/`, avec le JAR `project-gradle/build/libs/cnpcoverlay-3.0.1.jar`.
- Risque restant : la prise de quête doit être confirmée en jeu avec le profil FuguDreams ; aucune synchronisation serveur ou paquet n'est impliqué.

### Reprise agent sans état

Installer le JAR 3.0.1 dans le profil de test, accepter une quête après connexion, vérifier que sa case est cochée, la décocher, puis rouvrir l'overlay ou se reconnecter pour confirmer qu'elle reste décochée.

## 22. Passage en version 3.0.2 — 2026-07-19

### Progression

- `project-gradle/gradle.properties` indique désormais `mod_version=3.0.2`.
- Le clean build complet est réussi, tests inclus.

### Decision log

- Le correctif de suivi automatique des nouvelles quêtes est distribué sous le numéro `3.0.2`, sans changement d'identifiant de mod, de dépendances ou de mappings.

### Outcome et retrospective

- Commande : `./gradlew.bat clean build` depuis `project-gradle/`.
- Résultat : `BUILD SUCCESSFUL` ; `META-INF/mods.toml` embarque `version="3.0.2"`.
- JAR produit : `project-gradle/build/libs/cnpcoverlay-3.0.2.jar`.
- Avertissements restants : dépréciations `ResourceLocation(String, String)` déjà présentes, sans échec de compilation.

### Reprise agent sans état

Installer `project-gradle/build/libs/cnpcoverlay-3.0.2.jar` dans le profil de test et valider en jeu la case cochée à la prise d'une nouvelle quête, puis la conservation d'un décochage manuel.

## 23. Historique des quêtes terminées — 2026-07-19

### Progression

- La version cible est `3.0.3-test`; le changement de version a été publié séparément sur `origin/master` par le commit `84e127e`.
- L'overlay possède deux onglets intégrés, `En cours` et `Historique`. L'historique affiche uniquement les nouveaux stamps `PlayerData.questData.finishedQuests`, donc les quêtes réellement remises à CustomNPCs.
- Les quêtes en cours, abandonnées ou seulement à 100 % ne créent aucune occurrence. Un changement de stamp d'une quête répétable ajoute une nouvelle occurrence.
- La baseline initiale n'importe aucune ancienne validation. Elle est persistée même vide et reste monotone face aux maps transitoirement vidées par CustomNPCs.
- Le stockage dédié `cnpcoverlay/quest_history.json` est atomique, isolé par contexte et UUID, et alimenté par un worker client mono-thread avec coalescence. Le chargement de contexte est également asynchrone.
- La vue est virtualisée à quatre occurrences visibles, conserve recherche/sélection/scroll par onglet et adapte le panneau virtuel `380 x 260` aux petites surfaces GUI.
- Reste : validation visuelle et fonctionnelle dans le profil modpack qui charge réellement CustomNPCs.

### Surprises et discovery

- `isQuestCompleted` indique seulement que les objectifs actifs sont achevés; le signal fiable de remise définitive est le changement de `finishedQuests`.
- Le JAR CustomNPCs du modpack expose une map publique, tandis que des sources plus récentes utilisent un champ protégé et des accesseurs. L'intégration réfléchie couvre les deux contrats.
- Les stamps mélangent temps de monde et temps réel selon le type de répétition; ils servent uniquement à la déduplication. L'ordre affiché repose sur une séquence locale croissante.
- Une sauvegarde JSON synchrone depuis le tick client aurait pu produire des à-coups avec un historique volumineux. Les lectures/écritures ont été sorties du tick et les snapshots mémoire les plus récents restent autoritaires après un échec disque.
- Le smoke `runClient` ne chargeait que `minecraft`, `forge` et `cnpcoverlay`; il ne pouvait donc pas valider visuellement la source CustomNPCs.
- GitNexus fonctionne sans FTS/BM25. L'index de graphes est néanmoins à jour à 1 229 nœuds, 3 019 relations et 105 flux.

### Decision log

- Fonction entièrement client-side : aucun paquet, capability, tick ou stockage serveur CNPCoverlay; aucun Mixin ou Access Transformer.
- Aucun historique antérieur n'est reconstruit, puisque le modpack est encore en développement sans données joueur à migrer.
- L'identité de la map est comparée chaque tick client en O(1); le diff complet est limité aux changements d'identité et au contrôle de secours toutes les 20 ticks.
- Les occurrences ne sont jamais tronquées arbitrairement. L'écran ne matérialise que la fenêtre visible et ne réalise ni I/O, réflexion, tri, filtre, découpage ni formatage de date dans `render()`.
- Un hook JVM unique accorde au writer un budget total de deux secondes à l'arrêt, avec une seconde tentative unique si le premier flush échoue. Un arrêt brutal de processus reste, comme toute persistance locale, hors garantie.

### Outcome et retrospective

- Après la revue globale et le retry borné du dernier flush, `.\gradlew.bat build` a réussi depuis `project-gradle/` sous Java 17.0.19 / Gradle 8.8 : 114 tests, zéro échec et zéro erreur.
- Le build d'acceptation a été relancé avec la même commande immédiatement avant publication : `BUILD SUCCESSFUL` en 10 s.
- JAR produit et inspecté : `project-gradle/build/libs/cnpcoverlay-3.0.3-test.jar` (174 678 octets, SHA-256 `226BDFCFBD710C07568D4A5646605622B62322BEB2C7942E04F00E20056A5CF9`). Il contient les classes `client/quest/history/**`, le ViewModel et l'écran mis à jour, sans embarquer `journeymap/api/**`.
- `.\gradlew.bat runClient --console=plain --no-daemon` a réussi en 54 s : client Forge, render thread, monde solo, connexion du joueur `Dev`, sauvegarde et arrêt propres; aucun `ERROR` ou `FATAL` dans `project-gradle/run/logs/latest.log`.
- Les revues indépendantes des lots détecteur, stockage, intégration CustomNPCs, UI et I/O asynchrone ont approuvé les corrections après leurs cycles R2. La revue globale a ensuite imposé le découplage de la découverte active/historique, la confirmation des suppressions sur trois contrôles forcés et un arrêt borné du writer par hook JVM.
- `node .gitnexus/run.cjs analyze` a actualisé l'index. `detect-changes --scope unstaged` classe le worktree combiné `CRITICAL` (215 symboles, 105 flux), car il agrège l'écran à impact HIGH, `QuestHistoryState` à impact CRITICAL, les nouveaux fichiers et les changements préexistants de suivi automatique; les impacts isolés du provider, du stockage et des helpers restent LOW à MEDIUM.
- L'utilisateur a validé le JAR de test et autorisé la clôture du lot le 2026-07-19.

### Reprise agent sans état

Lot accepté. Vérifier que le commit fonctionnel et le commit de spécification sont présents sur `origin/master`, puis utiliser `project-gradle/build/libs/cnpcoverlay-3.0.3-test.jar` comme artefact de référence.
## 24. Autorité serveur de l’historique — 2026-07-19

- Version `3.0.4-test` publiée avant migration (`c065bc5`).
- Suivi actif/UI conservés côté client; historique des remises validées déplacé côté serveur.
- `SavedData` global Overworld indexé par UUID, paquet S2C borné, commande MJ `/cnpcoverlay history <player>` permission 2.
- Listener CustomNPCs `QuestTurnedInEvent` enregistré dynamiquement; aucune dépendance de compilation ni mixin.
- `clientSideOnly=false`, dépendances JourneyMap `>=6.0` et CustomNPCs `>=1.20.1` déclarées facultatives et ordonnées.
- `gradlew build` réussi; runServer atteint le chargement puis s’est arrêté sur EULA non accepté.

## 25. Compatibilité JourneyMap bêta — 2026-07-19

- Corrige la contrainte Forge JourneyMap de `[6.0,)`, qui excluait les préversions `1.20.1-6.0.0-beta.*`.
- Nouvelle borne : `[1.20.1-6.0.0-beta.0,)`, acceptant la bêta actuelle, les releases 6.x et les versions futures.
- Progression : correctif publié (`257f958`), JAR test prêt ; reste à le poser dans l’instance et confirmer l’écran de dépendances en jeu.
- Surprises et discovery : Forge compare les préversions `beta.*` avant la release `6.0`; `[6.0,)` les excluait.
- Decision log : borne `JourneyMap` fixée à `[1.20.1-6.0.0-beta.0,)` pour couvrir la bêta actuelle et les versions ultérieures.
- Outcome et retrospective : depuis `project-gradle/`, `./gradlew.bat --refresh-dependencies build` réussi, JAR `project-gradle/build/libs/cnpcoverlay-3.0.5-test.jar` (193787 octets, SHA-256 `B0A3438DD7B2668E994956AE152A429935C62D601DAAC370CB9755E1E7D181C8`).
- Reprise agent sans état : installer ce JAR côté client et serveur, puis valider que JourneyMap `1.20.1-6.0.0-beta.6` passe l’écran de connexion.

## 26. Libellé de catégorie CustomNPCs — 2026-07-19

- Progression : conversion et bootstrap corrigés ; JAR `3.0.7-test` prêt.
- Surprises et discovery : `IQuest#getCategory()` retourne un objet `IQuestCategory`; sa conversion texte Java produisait `QuestCategory@…`.
- Decision log : lire explicitement `IQuestCategory#getName()` par la passerelle de réflexion CustomNPCs, avec repli vide plutôt qu’un identifiant d’objet instable.
- Outcome et retrospective : test de régression d’abord rouge (méthode d’extraction absente), puis vert ; `gradlew build` réussi. Smoke client final réussi (chargement puis arrêt normal, aucun `safe referent`), et démarrage dédié atteint le lancement Forge sans erreur de séparation avant l’arrêt EULA attendu.
- Reprise agent sans état : installer `3.0.7-test` sur client et serveur puis vérifier en jeu une nouvelle remise de quête avec une catégorie nommée ; les entrées déjà enregistrées gardent leur ancien libellé.
