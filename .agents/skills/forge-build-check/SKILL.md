---
name: forge-build-check
description: Verifier un changement Forge 1.20.1 avec Gradle dans le client d’agents. Utiliser apres une modification Java, Gradle, resources, metadata, mixins ou assets; pour lancer .\\gradlew.bat test ou .\\gradlew.bat build depuis project-gradle; pour rapporter clairement les echecs; et pour declencher exee-plan apres un build ou clean build reussi.
---

# Skill: forge-build-check

Utiliser ce skill pour choisir et rapporter les commandes de verification.

## Commandes

Depuis `project-gradle/`:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

Ne lancer `clean build` que sur demande explicite ou finalisation.

## Choix rapide

- Changement Java: lancer au minimum `.\gradlew.bat test` si raisonnable.
- Changement build/dependances/mappings: lancer `.\gradlew.bat build`.
- Changement assets/datagen: lancer test/build si des tests couvrent les ressources; sinon verifier les JSON et chemins explicitement.
- Changement runtime ou crash: envisager `runClient` ou `runServer` seulement si necessaire et gerable.

## Rapport

En cas de succes:

- Donner la commande et le dossier d'execution.
- Pour `build` ou `clean build`, fournir le JAR produit dans `project-gradle/build/libs/`.
- Utiliser `exee-plan` avant la reponse finale apres un build ou clean build reussi.

En cas d'echec:

- Donner la commande.
- Resumer l'erreur principale.
- Identifier le fichier ou la cause probable.
- Donner la prochaine action concrete.
