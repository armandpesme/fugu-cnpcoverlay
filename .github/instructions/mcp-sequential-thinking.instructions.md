---
name: Sequential Thinking
description: Sequential Thinking est le mode de raisonnement PAR DEFAUT pour toute tache non triviale. Usage systematique et obligatoire.
applyTo: "**"
---

# Regle absolue

**Sequential Thinking est le mode de raisonnement par defaut.**
Avant toute action sur le code, toute analyse, toute decision d'architecture ou de correction, lancer sequential-thinking en premier.
Ne pas le sauter sous prétexte que la tache "semble simple" — la simplicite apparente est souvent une illusion.

# Declenchement obligatoire

Sequential Thinking DOIT etre lance avant d'agir dans TOUS ces cas :

- Analyse d'une erreur, d'un crash ou d'un log
- Toute modification de fichier Java, JSON, TOML, Gradle ou ressource
- Choix entre plusieurs approches ou APIs
- Toute tache touchant plus d'un fichier
- Cadrage d'une feature, d'un refactor ou d'une integration
- Lecture d'un bug ou d'une regression non evidente
- Selection du bon MCP ou du bon outil pour une tache
- Preparation d'un build, d'un run ou d'une validation Gradle
- Toute question d'architecture, de side-safety ou de compatibilite
- Toute intervention sur la GUI, la commande VFX, GeckoLib ou les presets

# Discipline d'execution

- Commencer avec une hypothese concrete, pas avec un brainstorming vague.
- Ajuster `totalThoughts` en cours de route — ne pas bloquer sur une estimation initiale.
- Marquer `isRevision: true` des que le raisonnement change de direction.
- Chaque etape doit pointer vers une action concrete : lecture, edit, validation ou decision justifiee.
- La derniere etape doit toujours etre une conclusion operationnelle claire.

# Seules exceptions admises

Sequential Thinking peut etre omis UNIQUEMENT pour :

- Une commande terminal pure sans ambiguite (ex: `Test-Path`, `Get-Content` d'un seul fichier connu)
- Une reponse factuelle immediate a une question fermee sans impact sur le code
- Un rename ou une correction de typo isolee et sans dependance
