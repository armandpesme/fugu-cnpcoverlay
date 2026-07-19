---
name: forge-client-server-boundaries
description: Controler la separation client, server et common dans un mod Minecraft Forge 1.20.1. Utiliser pour toute logique de gameplay, UI, HUD, rendu, Curios, attributs, capabilities, packets ou synchronisation afin d'eviter les classes client sur serveur dedie et d'exiger packet/capability explicite pour l'etat partage.
---

# Skill: forge-client-server-boundaries

Utiliser ce skill quand un changement touche le runtime.

## Classification

1. Classer chaque fichier touche: client-only, server-only ou common.
2. Verifier les imports client-only (`net.minecraft.client`, rendu, HUD, screens) et les isoler cote client.
3. Verifier que la logique serveur autoritaire reste cote serveur pour attributs, stats, inventaires, equipement, capabilities et effets.
4. Verifier que le common ne depend pas de classes client au chargement.

## Synchronisation

- Toute synchro client/serveur passe par packet ou capability explicite.
- Ne pas supposer qu'un NBT, attribut ou capability est visible client sans chemin de synchronisation confirme.
- Pour Curios, verifier le slot, l'entite cible, le moment d'equipement/desequipement et l'autorite serveur.
- Pour les valeurs visuelles seulement, garder le calcul client pur sans modifier l'etat serveur.

## Verification

Pour les risques de serveur dedie, privilegier `runServer` ou au minimum un build qui charge les classes. Rapporter explicitement les limites si seul `test` ou `build` a ete lance.
