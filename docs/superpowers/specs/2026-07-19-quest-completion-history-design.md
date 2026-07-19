# Historique des quêtes terminées — Spécification

## Objectif

Ajouter à CNPCoverlay un onglet `Historique` qui affiche exclusivement les quêtes remises et validées définitivement par CustomNPCs. Une quête abandonnée, encore en cours ou simplement arrivée à 100 % sans remise n'apparaît pas. Chaque nouvelle validation d'une quête répétable produit une occurrence indépendante.

Le jalon cible est `3.0.3-test`. Le modpack étant encore en développement sans données joueur à migrer, la première observation crée une baseline vide d'événements : l'historique exact commence avec cette version et aucun ancien état CustomNPCs n'est importé.

## Contraintes globales

- Forge `1.20.1`, Forge `47.4.20+` et Java `17`.
- Mod entièrement client-side : aucun paquet CNPCoverlay, capability ou tick serveur.
- CustomNPCs et JourneyMap restent des intégrations optionnelles ; l'overlay ne doit pas provoquer de crash si une API réfléchie manque.
- Aucun accès disque, scan réfléchi, tri, filtrage ou découpage de texte dans la boucle de rendu.
- Aucun Mixin ou Access Transformer pour intercepter CustomNPCs.
- Les modifications fonctionnelles restent sous `project-gradle/`.
- Les changements humains déjà présents dans le worktree sont conservés et intégrés sans écrasement.

## Sémantique de terminaison

CustomNPCs distingue deux états :

- `isQuestCompleted` signifie que les objectifs d'une quête active sont achevés ; la quête peut encore attendre sa remise.
- `finishedQuests` signifie que la quête a été effectivement remise et retirée des quêtes actives.

L'historique se fonde uniquement sur le miroir client `PlayerData.questData.finishedQuests`, représenté comme une association `questId -> sourceStamp`.

Les règles de détection sont :

1. La première lecture d'un contexte initialise la baseline des stamps sans créer d'événement.
2. L'ajout d'un ID ou la modification de son stamp crée exactement une occurrence.
3. La suppression d'un ID ne crée jamais d'occurrence.
4. La disparition d'une quête active sans stamp nouveau ou modifié est un abandon et reste ignorée.
5. Une quête répétable validée plusieurs fois modifie son stamp à chaque remise ; chaque modification ajoute une occurrence.
6. Les stamps CustomNPCs ne servent qu'à la déduplication. Ils ne servent pas au tri, car CustomNPCs mélange temps réel en millisecondes et temps de monde en ticks selon le type de répétition.

L'ordre canonique repose sur une séquence locale strictement croissante. La date affichée est l'instant local d'observation de l'événement. Deux événements observés dans la même milliseconde restent ordonnés par leur séquence.

## Source CustomNPCs et compatibilité

L'intégration réfléchie découvre et met en cache les accès une seule fois :

- `PlayerData.get(Player)`;
- le champ `PlayerData.questData`;
- les méthodes d'accès à la collection terminée lorsqu'elles existent ;
- en fallback, le champ déclaré `PlayerQuestData.finishedQuests`.

La variante du JAR modpack expose directement la map publique. Les sources CustomNPCs plus récentes utilisent un champ protégé et des accesseurs ; les deux contrats doivent être tolérés.

L'observateur compare l'identité de la map à chaque fin de tick client, opération O(1). Une copie immuable et un diff O(n) ne sont effectués que lorsque le miroir CustomNPCs change. Un contrôle complet de secours toutes les 20 ticks couvre les variantes qui réutilisent la même instance de map en la vidant puis en la remplissant.

Si la découverte ou la lecture est indisponible, la fonctionnalité d'historique se désactive proprement pour ce rafraîchissement, journalise une cause seulement lorsqu'elle change et laisse les quêtes actives ainsi que le reste de l'overlay fonctionner.

## Modèle et état

### `QuestHistoryEntry`

Une occurrence immuable contient :

- `occurrenceId`, dérivé du contexte, du joueur et de la séquence ;
- `questId`;
- `category`;
- `title`;
- `displayLogText`, déjà débarrassé des lignes techniques commençant par `#`;
- la copie immuable des objectifs terminaux connus ;
- `observedCompletedAtEpochMillis`;
- `sourceFinishedStamp`;
- `sequence`.

Les métadonnées visuelles proviennent en priorité du dernier `QuestSnapshot` actif mis en cache. Si le snapshot n'est plus disponible, l'intégration peut lire la définition de quête dans le catalogue client CustomNPCs. Une définition introuvable produit un libellé stable `Quête <id>` au lieu d'annuler l'événement.

### `QuestHistoryState`

Ce service client :

- charge une seule fois l'historique et la baseline au changement de contexte ;
- garde une vue immuable des entrées triées par séquence décroissante ;
- conserve les derniers snapshots actifs pour enrichir une validation ;
- observe les stamps et crée les nouvelles occurrences ;
- réinitialise uniquement l'état de session à la déconnexion, sans effacer le stockage ;
- expose à l'écran une liste en lecture seule.

La détection et l'ajout d'une occurrence sont calculés hors de l'écran. L'écran ne modifie jamais l'historique.

## Persistance

Le stockage utilise un fichier dédié :

`<gameDirectory>/cnpcoverlay/quest_history.json`

Le schéma racine est indexé par la même clé de contexte que le suivi (`multiplayer:<host>:<port>` ou `singleplayer:<levelId>`), puis par UUID joueur. Pour chaque joueur, il conserve :

- `nextSequence`;
- `lastFinishedStamps`;
- `entries`.

Une sauvegarde atomique écrit un fichier temporaire puis le remplace. L'écriture n'a lieu qu'après l'ajout réel d'au moins une occurrence ou une modification nécessaire de baseline. Les lectures ont lieu au changement de contexte, jamais par frame.

Un fichier absent produit un historique vide. Un fichier illisible est déplacé vers un fichier `.broken-<timestamp>` et l'historique repart vide sans interrompre le client.

La liste n'est pas tronquée arbitrairement : seules les lignes visibles sont matérialisées côté écran, ce qui garde le coût de rendu constant même si le fichier contient plusieurs milliers d'occurrences.

## Interface

Le panneau reste centré et conserve sa taille `380 x 260`, ses couleurs sombres, son accent cyan et le composant `FlatButton`.

Deux onglets sont intégrés au bandeau :

- `En cours`, vue existante ;
- `Historique`, nouvelle vue.

L'onglet actif est mémorisé entre les reconstructions de l'écran. Le champ de recherche reste disponible dans les deux vues.

### Vue Historique

- Colonne gauche : `Toutes`, puis les catégories présentes dans l'historique filtré.
- Colonne droite : occurrences de la catégorie sélectionnée, de la plus récente à la plus ancienne.
- Une occurrence répétable apparaît autant de fois qu'elle a été validée depuis la baseline.
- Une ligne affiche une coche verte, le titre et une date/heure compacte.
- Le panneau de détails affiche le titre en vert, la catégorie, la date de validation, la description filtrée et les objectifs mémorisés.
- Aucun bouton de suivi ou changement de quête active n'est présent.
- L'état vide affiche `Aucune quête terminée enregistrée`.

La navigation à la molette, les scrollbars, la recherche avec délai de 175 ms et les états de sélection suivent les conventions de la vue existante. Les listes filtrées, catégories, textes découpés et détails sont préparés pendant `init()` ou lors d'une reconstruction, pas dans `render()`. Le nombre de widgets créés dépend uniquement du nombre maximal de lignes visibles.

## Flux de données

```text
Miroir client CustomNPCs finishedQuests
        |
        | identité modifiée ou contrôle 1 Hz
        v
copie immuable des stamps
        |
        v
QuestHistoryState : diff avec baseline
        |
        +-- stamp absent/supprimé --------> aucune entrée
        |
        +-- stamp ajouté/modifié ---------> QuestHistoryEntry
                                              |
                                              +--> sauvegarde JSON atomique
                                              |
                                              +--> vue immuable pour l'onglet
```

## Performance

- Coût habituel par tick client : comparaison d'identité et compteur, sans allocation.
- Coût lors d'une synchronisation ou du contrôle 1 Hz : O(n) sur les quêtes terminées.
- Écriture disque : seulement lors d'une nouvelle validation ou de l'initialisation indispensable de la baseline.
- Rendu : O(v) où `v` est le nombre fixe de lignes visibles.
- Charge serveur ajoutée : zéro tick, zéro paquet et zéro stockage.
- Aucun appel à CustomNPCs ni accès disque depuis le HUD ou `CnpcOverlayScreen.render`.

## Tests et validation

Les tests purs couvrent :

- baseline initiale sans fausse occurrence ;
- abandon sans entrée ;
- première validation, exactement une entrée ;
- rafraîchissements identiques sans doublon ;
- répétable validée plusieurs fois, une entrée par nouveau stamp ;
- répétable abandonnée après une ancienne réussite, aucune nouvelle entrée ;
- suppression d'un stamp, aucune entrée ;
- ordre stable par séquence lorsque les dates sont égales ;
- isolation contexte et joueur ;
- reconnexion et rechargement sans duplication ;
- fichier absent, fichier corrompu et écriture atomique ;
- filtrage, catégories, clamp des scrolls et liste de plusieurs milliers d'entrées ;
- compatibilité réfléchie avec le JAR modpack et la variante source récente.

La vérification finale comprend :

1. tests ciblés ;
2. `.\gradlew.bat build` depuis `project-gradle/` ;
3. `runClient` parce que l'écran et le comportement runtime changent ;
4. contrôle manuel : quête en cours, 100 % non remise, abandon, validation, deux validations répétables et reconnexion ;
5. `gitnexus detect-changes` pour confirmer les symboles et flux affectés ;
6. verdict final `qa-release`.

## Risques connus

- Deux validations du même ID répétable reçues entre deux observations du miroir peuvent être condensées en une par CustomNPCs. Ce cas extrême est indétectable sans intercepter un paquet ou ajouter une logique serveur, solutions rejetées pour préserver la compatibilité modpack.
- GitNexus classe l'évolution de `CnpcOverlayScreen` en risque élevé, car elle touche l'initialisation, le rendu, la molette, le tick et la reconstruction. L'implémentation doit rester ciblée et la validation `runClient` est obligatoire.
- Le provider repose sur de la réflexion afin de conserver CustomNPCs optionnel. La découverte doit rester isolée, mise en cache et couverte par le JAR runtime exact.
