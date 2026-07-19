---
name: forge-mixin-safety
description: Gerer les mixins critiques d'un mod Forge 1.20.1 pendant developpement et avant release. Utiliser quand une tache touche Mixin, refmap, accessors, Access Transformers, compatibilite Curios/Epic Fight, runClient/runServer ou preparation clean build afin de garder les mixins stricts en dev et de retirer ce garde-fou seulement juste avant release.
---

# Skill: forge-mixin-safety

Utiliser ce skill pour tout changement Mixin ou lancement impacte par Mixin.

## Mode developpement

- Configurer les mixins critiques en mode strict pendant le developpement.
- En cas d'echec Mixin, laisser le jeu crasher pour obtenir un log exploitable.
- Identifier le mixin responsable par le log avant de modifier du code.
- Verifier refmap, mappings, target class, method descriptor et version Forge/Minecraft locale.

## Choix technique

- Preferer Access Transformer si le besoin est un acces stable a un membre vanilla sans interception comportementale.
- Preferer Mixin si le besoin est une interception ou une adaptation de comportement.
- Ne pas ajouter de Mixin pour contourner une API Forge existante et verifiee.

## Avant release

Retirer ou assouplir le garde-fou strict uniquement juste avant clean build/release, apres validation. Noter ce changement dans `PLANS.md` si un build de livraison reussit.
