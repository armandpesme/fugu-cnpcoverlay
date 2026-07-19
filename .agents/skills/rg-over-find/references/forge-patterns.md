# Patterns rg pour Forge 1.20.1

Ce workspace est un mod Minecraft Forge 1.20.1 dans `project-gradle/`. Les patterns ci-dessous utilisent `rg` (ripgrep) en remplacement de `find ... -exec grep`. Toujours ajouter `-n` (numero de ligne) sauf cas particulier.

## Conventions locales

- Mod principal: lire `project-gradle/src/main/java` et `project-gradle/src/main/resources`.
- Ne **jamais** scanner `project-gradle/build/`, `project-gradle/.gradle/`, `project-gradle/run/` en mode ignoré: exclure avec `-g '!build/**' -g '!.gradle/**' -g '!run/**'`.
- Toujours limiter le scope a `project-gradle/` sauf demande explicite de scanner le workspace complet.

## Registry IDs, modid, namespaces

```bash
# Toutes les references a la classe principale du mod
rg -n -t java "FuguReviveMe\\.FuguReviveMe" project-gradle/src

# Tous les DeferredRegister et leurs types
rg -n -t java "DeferredRegister\\.create\\(" project-gradle/src

# Toutes les cles de registre (ResourceKey, RegistryObject)
rg -n -t java "RegistryObject<|ResourceKey<" project-gradle/src

# Recettes / loot tables / tags mentionnant le namespace
rg -n -t json "modid|fugu" project-gradle/src/main/resources

# Detection de namespace incoherent (deux modid differents dans les assets)
rg -n -t json '"namespace"' project-gradle/src/main/resources | rg -o '"namespace":\\s*"[a-z_]+"' | rg -o '[a-z_]+$' | Sort-Object -Unique
```

## Mixins

```bash
# Toutes les declarations @Mixin
rg -n -t java "^@Mixin\\(|^public abstract class .*Mixin" project-gradle/src

# Tous les points d'injection (@At)
rg -n -t java -C 2 "@At\\(" project-gradle/src

# Mixins qui ciblent un namespace donne
rg -n -t java "value\\s*=\\s*[A-Z][A-Za-z0-9_.]+\\.class" project-gradle/src

# Verifier que le refmap est coherent avec les mixins presents
rg -n -t java "refMap\\s*=\\s*\"" project-gradle/src
```

## Items, blocs, armures

```bash
# Toutes les classes d'items enregistrees
rg -n -t java "extends (Item|Block|ArmorItem|ArmorMaterial|SwordItem)\\{" project-gradle/src

# Variantes d'armures (GeckoLib)
rg -n -t java "GeoItem|GeoArmor|GeoBlock|registerModel" project-gradle/src

# Toutes les textures referencees par le mod
rg -n "textures/" project-gradle/src/main/resources/assets/modid
```

## Lang, modeles, animations

```bash
# Lang keys manquantes ou dupliquees
rg -n -t properties "^[a-z_]+\\.[a-z_]+=" project-gradle/src/main/resources/assets/modid/lang | rg -o '^[a-z_]+\\.[a-z_]+' | Sort-Object | Get-Unique -AsString

# Clefs de traduction referencees dans le code
rg -n -t java "Component\\.translatable\\(\"[^\"]+\"\\)" project-gradle/src | rg -o '"[^"]+"'

# Animations GeckoLib
rg -n -t json "\"animation\"" project-gradle/src/main/resources/assets/modid/animations
```

## Capabilities et reseau

```bash
# Capability providers
rg -n -t java "CapabilityManager|CapabilityToken|ICapabilityProvider" project-gradle/src

# Channels de packets
rg -n -t java "SimpleChannel|NetworkRegistry|registerMessage" project-gradle/src

# Verifier que chaque handler cote serveur a un handler cote client
rg -n -t java "::encode|::decode|::handle" project-gradle/src
```

## Tags (Curios, Forge, Minecraft)

```bash
# Tags definis par le mod
rg -n -t json "\"values\"" project-gradle/src/main/resources/data/modid/tags

# Verifier la coherence d'un tag (fichier + usages)
rg -n -t json "\"modid:.*\"" project-gradle/src/main/resources/data/modid/tags
rg -n -t java "TagKey|ITag\\." project-gradle/src
```

## Datagen et providers

```bash
# Tous les providers
rg -n -t java "extends (RecipeProvider|LootTableProvider|BlockTagsProvider|ItemTagsProvider|DataProvider)" project-gradle/src

# Verification rapide: chaque provider declare @SubscribeEvent
rg -n -B 2 -t java "extends DataProvider" project-gradle/src
```

## Build, Gradle, metadata

```bash
# Liste des dependances
rg -n "implementation |runtimeOnly |compileOnly " project-gradle/build.gradle

# Verifier la version de Minecraft / Forge
rg -n "minecraft_version|forge_version|minecraft_version_range" project-gradle/gradle.properties

# mods.toml sanity check
rg -n -t toml "modId|version|name|description|authors" project-gradle/src/main/resources/META-INF/mods.toml
```

## Logs et crash reports

Pour les patterns de diagnostic (erreurs, mixins, mods suspects, JVM), voir `minecraft-log-tools`. Quelques raccourcis:

```bash
# Chercher toutes les exceptions dans les logs du mod
rg -n -M 200 -m 50 "Exception|Caused by|FATAL" project-gradle/run/logs project-gradle/run/crash-reports

# Mixin failures uniquement
rg -n -C 5 "InvalidMixinException|MixinApplyError|InjectionError" project-gradle/run/logs

# Mods suspectes
rg -n -C 4 "Suspected Mods|Failure message|Mod Loading Error" project-gradle/run/logs
```

## Refactor et impact

Avant tout rename ou refactor, combiner `rg` avec `gitnexus-impact-analysis` (si GitNexus est disponible) pour evaluer le blast radius:

```bash
# Lister toutes les references directes avant un rename
rg -n "\\bOldClassName\\b" project-gradle

# Verifier qu'aucun JSON d'asset ne reference l'ancien chemin
rg -n -t json "old_name|old_path" project-gradle/src/main/resources
```

Pour un rename structure, preferer l'outil `gitnexus rename` (comprend le call graph) plutot qu'un `rg ... | sed` maison.
