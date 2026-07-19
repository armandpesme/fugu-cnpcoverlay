---
applyTo: "**/*.java"
description: "Règles Java Forge 1.20.1 pour ce workspace."
---

# Java Forge 1.20.1

- **Java 17** uniquement. Pas de feature Java > 17.
- Pas d'API Fabric / NeoForge / Yarn / registry legacy.
- `DeferredRegister` pour les registries, sauf pattern local existant déjà valide.
- Events sur le bon bus : **mod event bus** (`FMLCommonSetupEvent`, `RegisterEvent`, etc.) vs **Forge event bus** (`PlayerEvent`, `LivingEvent`, etc.).
- Code client isolé derrière `Dist.CLIENT` ou une classe client dédiée référencée via `DistExecutor` / `@OnlyIn`.
- Toute synchro client/serveur passe par packet (`SimpleChannel`) ou capability explicite.
- Vérifier imports et package avant de finaliser un patch.
- Ne pas inventer de classe, méthode ou event Forge/Minecraft : sourcer si incertain.
