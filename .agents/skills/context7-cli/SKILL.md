---
name: context7-cli
description: Utiliser Context7 CLI (ctx7) pour obtenir la documentation actuelle d'une bibliotheque, API, dependance ou framework. Alternative au MCP — utilise le CLI Node.js installe globalement. Activer quand les fichiers locaux ne suffisent pas et que la question porte sur une API publique documentee.
---

# Skill: context7-cli

Utiliser Context7 CLI (package npm `ctx7`) quand une question porte sur une bibliotheque, un framework, une reference API, du setup, de la configuration ou des exemples de code.

## Pre-requis

- Node.js 18+ (disponible : v24.13.0)
- CLI installe globalement : `npm install -g ctx7`
- API key configuree via `ctx7 setup --api-key VOTRE_CLE`

## Commandes

### Resoudre une bibliotheque

```powershell
ctx7 library <nom>
ctx7 library <nom> "contexte"
ctx7 library <nom> --json   # sortie JSON
```

Exemples :
```powershell
ctx7 library react
ctx7 library nextjs "app router"
ctx7 library forge
```

### Obtenir de la documentation

```powershell
ctx7 docs <id-bibliotheque> "question"
ctx7 docs <id-bibliotheque> "question" --json
```

Exemples :
```powershell
ctx7 docs /facebook/react "useEffect cleanup"
ctx7 docs /vercel/next.js "middleware"
```

## Workflow

1. Resoudre d'abord la bibliotheque avec `ctx7 library`.
2. Choisir le resultat officiel ou primaire le plus proche, en preferant la version demandee si elle existe.
3. Interroger la documentation avec `ctx7 docs` et la question concrete de l'utilisateur.
4. Repondre depuis la documentation recuperee et mentionner la bibliotheque/version quand c'est utile.

## Notes

- Garder les requetes specifiques et inclure la question complete quand possible.
- Preferer les packages officiels et les repositories primaires maintenus aux forks.
- **Pour Forge/Minecraft 1.20.1** : verifier d'abord le workspace et utiliser `explore-doc` si la question exige des sources Forge/Minecraft ciblees. Context7 peut ne pas avoir de documentation specifique Forge — dans ce cas, basculer vers les fichiers locaux ou `explore-doc`.
- **Pour GeckoLib / Epic Fight** : utiliser `ctx7 library` pour tenter une resolution, mais privilegier les sources locales et `explore-doc` en priorite.
- Si Context7 est indisponible (timeout, erreur), le dire et basculer vers les fichiers locaux, la documentation officielle ou `explore-doc`.

## Alternatives

- Skill `context7-mcp` : instruction de declenchement (QUAND utiliser Context7). Ce skill-ci est le complement execution (COMMANDES).
- Skill `explore-doc` : recherche documentaire structuree pour Forge/Minecraft/GeckoLib/Epic Fight.
