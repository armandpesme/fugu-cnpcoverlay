---
name: explore-doc
id: forge.explore-doc
description: Recherche documentaire structuree pour Forge 1.20.1, Minecraft, GeckoLib, Epic Fight et APIs de mods sources.
---

# Skill: explore-doc

Utiliser ce skill quand une question d'API ou de comportement n'est pas resolue par le code local.

## Ordre de recherche
1. Chercher d'abord dans le workspace avec `rg` et les fichiers Gradle pour confirmer versions et dependances.
2. Chercher la documentation officielle Forge/Minecraft correspondant a Minecraft `1.20.1` et Forge `47.4.x`.
3. Utiliser Brave/Exa pour trouver docs et code source fiables si la recherche generale est trop vague.
4. Utiliser Context7 pour les bibliotheques publiques supportees lorsque la question porte sur une API documentee.

## Sources preferees
- Documentation Forge officielle.
- Sources Minecraft/Forge accessibles selon mappings du projet.
- Repositories officiels des bibliotheques utilisees par le mod.
- Exemples de mods reconnus uniquement si la version est compatible.

## Sortie attendue
- Reponse courte avec version ciblee.
- Liens ou fichiers sources consultes.
- Signature/classe/event exact si confirme.
- Incertitudes explicites si la source ne couvre pas Forge `47.4.x`.

## Interdits
- Ne pas inventer de methode, event ou registry.
- Ne pas melanger Fabric, NeoForge, Yarn ou versions pre-1.19 avec Forge `1.20.1`.
- Ne pas modifier le code depuis ce skill.
