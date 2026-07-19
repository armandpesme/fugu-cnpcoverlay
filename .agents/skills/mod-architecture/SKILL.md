---
name: mod-architecture
id: forge.mod-architecture
description: Procedure d'analyse et de modification de l'architecture d'un mod Forge 1.20.1.
---

# Skill: mod-architecture

Utiliser ce skill avant tout changement structurel dans un mod Forge.

## Procedure
1. Identifier le mod id, les packages racine, la separation client/common/server et les dependances Gradle.
2. Lire les fichiers concernes avant edition.
3. Chercher les usages avec `rg` avant de renommer, de deplacer ou de changer une signature.
4. Verifier le cote d'execution: client-only, server-only ou common.
5. Garder les changements limites au besoin fonctionnel.
6. Prevoir la verification minimale: compile, runClient, runData ou test cible selon le changement.

## Regles Forge
- Registries via `DeferredRegister` sauf pattern local deja different et valide.
- Events Forge sur le bus adapte: mod event bus ou Forge event bus.
- Client setup isole derriere `Dist.CLIENT` ou classe client dediee.
- Network explicite pour tout etat visible client/serveur.
- Assets et lang synchronises avec registry names.
