---
name: forge-edit-scope
description: Encadrer une modification ciblee dans project-gradle pour un mod Forge 1.20.1. Utiliser avant toute edition Java, Gradle, TOML, JSON, ressource, config ou metadata afin de lire les fichiers concernes, chercher les usages avec rg, proteger les changements humains, conserver les IDs existants et eviter les refactors inutiles.
---

# Skill: forge-edit-scope

Utiliser ce skill juste avant une modification locale.

## Avant edition

1. Identifier les fichiers probablement concernes.
2. Lire les fichiers avant de les modifier.
3. Chercher les usages avec `rg` pour les classes, methodes, registry names, tags, lang keys, paths JSON et IDs.
4. Verifier les changements deja presents dans les fichiers touches si le worktree est sale.
5. Confirmer que la modification reste dans `project-gradle/` sauf demande explicite.

## Regles de modification

- Respecter les IDs existants; ne changer une version que si la tache le demande ou si le jalon l'impose.
- Preferer les patterns du mod aux abstractions nouvelles.
- Garder les changements petits, lisibles et lies au besoin.
- Ne pas alterer les armures ou contenus existants non concernes.
- Ne pas introduire Fabric, Loom, NeoForge, Yarn, anciennes registries ou APIs non verifiees.
- Utiliser `apply_patch` pour les edits manuels.

## Verification locale

Choisir la verification minimale selon le risque:

- Java: compiler ou lancer `.\gradlew.bat test` depuis `project-gradle/` si raisonnable.
- Build/dependances: lancer une tache Gradle pertinente.
- Assets/datagen: verifier chemins, mod id, JSON et references.
- Si aucune verification n'est lancee, le dire clairement avec la raison.
