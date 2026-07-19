---
name: doubt-qcm-pause
description: Utiliser dans le client d’agents lorsqu'un doute materiel, un choix de design, une ambiguite de perimetre ou une action irreversible exige une decision humaine avant de continuer.
---

# Doubt QCM Pause

Mettre le travail en pause uniquement lorsqu'une decision non deduisible peut modifier le design, le perimetre, la qualite ou la securite du livrable.

## Avant la question

1. Lire les fichiers concernes, `AGENTS.md`, `README.md` et `PLANS.md`.
2. Chercher localement la reponse et verifier la documentation si le doute porte sur une API.
3. Ne pas questionner un choix deja tranche ni une decision locale reversible.
4. Construire 2 ou 3 options concretes et recommander la plus sure.

## Question dans le client d’agents

- Utiliser `request_user_input` lorsqu'il est disponible.
- Poser une seule question par appel.
- Fournir un header court, 2 ou 3 options et placer la recommandation en premier.
- Expliquer en une phrase l'impact de chaque option.
- Ne pas ajouter manuellement l'option de reponse libre si le client la fournit.
- Si l'outil est absent, signaler la limitation et utiliser un QCM texte seulement avec l'accord de l'utilisateur.

## Quand s'arreter

- Choix de design ou de gameplay non tranche.
- Critere d'acceptation ou perimetre ambigu.
- Conflit de versions ou API critique encore incertaine apres recherche.
- Suppression, rename global, refactor large, rebase, force push ou autre action difficilement reversible.
- Contradiction entre la demande et une decision active du workspace.

Ne pas s'arreter pour une convention deja documentee, une correction locale evidente ou une question resoluble par recherche.

## Apres la reponse

1. Reformuler la decision en une phrase.
2. Mettre a jour `PLANS.md` si la reprise future depend de cette decision.
3. Reprendre avec l'option validee; ne jamais choisir silencieusement a la place de l'utilisateur.

`PLANS.md` est l'unique systeme de reprise et de journal des decisions de ce workspace.
