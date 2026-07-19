---
applyTo: "**/build.gradle,**/settings.gradle,**/gradle.properties,**/mods.toml,**/pack.mcmeta"
description: "Règles build & metadata Forge 1.20.1."
---

# Build & metadata Forge 1.20.1

- Minecraft `1.20.1`, Forge `47.4.x` (cible `47.4.20`), Java `17`, Gradle `8`.
- Mappings : Mojang + Parchment **uniquement si le projet les utilise déjà**.
- Ne pas ajouter de plugin Gradle inutile.
- Ne pas migrer vers NeoForge / Fabric / Loom.
- `mods.toml` : `modId`, `version`, `loaderVersion`, `dependencies` cohérents.
- GeckoLib `4.8.3` autorisé uniquement si déjà présent ou demandé explicitement.
- Cycle de livraison : `.\gradlew.bat build` puis, sur validation, `.\gradlew.bat clean build`, et **fournir un lien cliquable vers le JAR** produit dans `build/libs/`.
