---
name: forge-qa
id: forge.forge-qa
description: Checklist QA pour livrer un changement Forge 1.20.1 avec verdict GO/NO-GO.
---

# Skill: forge-qa

Utiliser ce skill en fin de changement ou pour valider une correction.

## Checklist
1. Lister les fichiers modifies et le comportement attendu.
2. Verifier compilation ou expliquer pourquoi elle n'a pas ete lancee.
3. Pour build/dependances: verifier Gradle, Java 17, Forge `47.4.x`.
4. Pour gameplay: verifier side client/server, events, registries et sync.
5. Pour assets: verifier chemins, JSON valide, mod id, lang keys.
6. Pour crash: verifier que la cause racine est couverte.

## Verdict
- `GO`: verification pertinente passee, pas de risque bloquant connu.
- `NO-GO`: erreur bloquante, verification impossible mais critique, ou hypothese API non confirmee.
- `PENDING`: changement incomplet ou bloque par decision humaine.
