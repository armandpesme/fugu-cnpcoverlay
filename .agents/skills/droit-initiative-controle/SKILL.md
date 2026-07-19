---
name: droit-initiative-controle
description: Encadrer les decisions techniques autonomes dans ce workspace. Utiliser quand une correction hors perimetre, un doute de design, une derive de jalon ou une decision d'architecture/gameplay pourrait demander validation; tracer dans PLANS.md les decisions utiles a la reprise.
---

# Droit D'Initiative Controle

## Principe

Prendre des decisions techniques locales seulement quand cela termine le jalon sans modifier le design valide.

Autoriser une correction hors perimetre uniquement si elle est necessaire pour:

- faire compiler le projet;
- debloquer le jalon en cours;
- corriger une erreur directement causee par les modifications de l'agent;
- eviter un bug evident et limite.

## Arret Obligatoire

S'arreter et demander validation a Armand si la decision peut modifier:

- le design valide;
- le gameplay;
- l'architecture generale;
- les documents proteges;
- le perimetre du jalon.

Privilegier une question de validation des qu'il existe un risque de derive fonctionnelle.

## Format De Question

Utiliser une question courte, cadree et avec plusieurs choix:

```md
Question pour validation :

Contexte :
...

Choix recommandes :
A. ...
B. ...
C. ...

Recommandation de l'agent :
...

Impact si on choisit A :
...
Impact si on choisit B :
...
Impact si on choisit C :
...
```

## Si L'Agent Continue

Si l'agent continue sans poser de question et que la reprise future en depend, documenter la decision dans `PLANS.md`.

Inclure au minimum:

- la date;
- le contexte;
- la decision autonome;
- la justification;
- l'impact estime;
- les fichiers touches.
