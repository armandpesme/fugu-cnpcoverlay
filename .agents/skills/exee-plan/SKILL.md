---
name: exee-plan
id: workflow.exee-plan
description: Maintenir PLANS.md autosuffisant apres jalon, Gradle build reussi ou clean build reussi.
---

# Skill: exee-plan

Utiliser ce skill quand un jalon est atteint, et obligatoirement apres un Gradle build reussi ou un clean build reussi.

## Fichier de reprise

- Mettre a jour `PLANS.md`.
- Le fichier doit suffire a un agent sans etat: objectif, contexte utile, fichiers touches, commandes lancees, resultats, decisions, risques et prochaine action.
- Mettre a jour le plan avant la reponse finale qui annonce le succes du build.

## Declencheurs obligatoires

- Commande terminee avec code 0 et correspondant a `gradlew build`, `gradlew.bat build`, `gradle.bat build` ou variante prefixee par `./` / `.\`.
- Commande terminee avec code 0 et correspondant a `gradlew clean build`, `gradlew.bat clean build`, `gradle.bat clean build` ou variante prefixee par `./` / `.\`.
- Jalon de livraison ou de diagnostic important, meme hors build, si la reprise par un autre agent depend de ce contexte.

## Sections obligatoires du plan

1. `Progression`: ce qui est fait, ce qui est en cours, ce qu'il reste.
2. `Surprises et discovery`: imprevus, decouvertes, blocages ou hypotheses encore actives.
3. `Decision log`: decision, raison, impact, date ou jalon.
4. `Outcome et retrospective`: bilan du jalon, verification, resultat, risque restant.
5. `Reprise agent sans etat`: contexte minimal et prochaine action concrete.

## Procedure de mise a jour

1. Conserver les informations encore utiles; supprimer les details obsoletes ou faux.
2. Ajouter une entree datee pour le dernier jalon.
3. Pour un build reussi, noter la commande exacte, le dossier d'execution, le resultat, le type (`build` ou `clean build`) et le JAR produit si connu.
4. Mettre a jour `Progression` pour distinguer fait, en cours et reste.
5. Ajouter toute surprise, decision ou risque decouvert pendant le jalon.
6. Terminer par une prochaine action suffisamment precise pour qu'un nouvel agent puisse reprendre sans historique.

## Echec de build

- Un build echoue ne declenche pas l'obligation de succes, mais il peut justifier une entree `Surprises et discovery` si l'information est necessaire a la reprise.
- Ne jamais enregistrer de secret, jeton, cle API ou donnee sensible dans le plan.
