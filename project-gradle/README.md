# Stat Overlay

- **Mod ID:** cnpcoverlay
- **Mod name:** CNPC Overlay
- **Author:** FuguTeams
- **Mod group:** com.cnpcoverlay.cnpcoverlaymod

## Description

"mod-forge" (cnpcoverlay) est un projet Gradle conçu pour le développement d'un mod Minecraft sous Forge.

## Spécifications techniques

Version Minecraft : 1.20.1, Forge : 47.4.16, Java : 17.0.17, IDE recommandé : VS Code (avec l'extension Gradle for Java).

## Fonctionnalités

Le mod a pour objectif de créer un mod permettant d’afficher, via une commande accessible à tous les joueurs, une interface GUI/HUD/UI. Cette interface reprend les informations du GUI de suivi des quêtes de Custom NPC, avec un rendu visuel amélioré et des fonctionnalités supplémentaires. Le mod est entièrement client-side, conçu pour être hautement compatible avec les autres mods, et fonctionne correctement sur les serveurs multijoueurs

## Points clés

- **Contrôle :** activation et désactivation via une commande dédiée.
- **Design :** interface plus large, police de caractères plus fine et esthétique supérieure au menu par défaut Custom NPC.
- **Lecture seule :** le mod récupère les données sans modifier le comportement des mods sources, garantissant une compatibilité totale au sein d'un modpack.

## Tools

S'aider du workspace/dossier "tools-workspace" qui contient le mods CustomNPCs-1.20.1-GBPort-Unofficial-20251031.jar en jar, en dossier dézipé et en dossier décompilé avec cfr, afin de fork/hook, etc.

## Source installation information for modders

This code follows the Minecraft Forge installation methodology. It will apply some small patches to the vanilla MCP source code, giving you and it access to some of the data and functions you need to build a successful mod.

## Note

Also that the patches are built against "un-renamed" MCP source code (aka SRG Names) - this means that you will not be able to read them directly against normal code.

## Setup process

Step 1: Open your command-line and browse to the folder where you extracted the zip file.

Step 2: You're left with a choice.

If you prefer to use Eclipse:

1. Run the following command: `./gradlew genEclipseRuns`
2. Open Eclipse, Import > Existing Gradle Project > Select Folder
   or run `gradlew eclipse` to generate the project.

If you prefer to use IntelliJ:

1. Open IDEA, and import project.
2. Select your build.gradle file and have it import.
3. Run the following command: `./gradlew genIntellijRuns`
4. Refresh the Gradle Project in IDEA if required.

If at any point you are missing libraries in your IDE, or you've run into problems you can
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything
(this does not affect your code) and then start the process again.

## Mapping names

By default, the MDK is configured to use the official mapping names from Mojang for methods and fields
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license, if you do not agree with it you can change your mapping names to other crowdsourced names in your
build.gradle. For the latest license text, refer to the mapping file itself, or the reference copy here:
[Mojang mapping license (MCPConfig)](https://github.com/MinecraftForge/MCPConfig/blob/master/Mojang.md)

## Additional resources

- [Community Documentation](https://docs.minecraftforge.net/en/1.20.1/gettingstarted/)
- [LexManos' Install Video](https://youtu.be/8VEdtQLuLO0)
- [Forge Forums](https://forums.minecraftforge.net/)
- [Forge Discord](https://discord.minecraftforge.net/)
