---
name: py-complex-patch
description: Use Python for complex JSON, YAML, or multi-file transformations. Reliable for conditional logic, schema validation, cross-file references, and bulk Git patching. More token-expensive than jq-json-patch; activate only when jq becomes unwieldy. Use when the user asks for complex data migrations, multi-step transformations, or validation spanning multiple files.
---

# Skill: py-complex-patch

Utiliser ce skill quand les transformations JSON/YAML deviennent trop complexes pour `jq-json-patch`: logique conditionnelle profonde, modifications multi-fichiers avec regles differentes, validation de schema croise, ou transformations necessitant un etat intermediaire.

## Prerequis

- Python 3.8+ installe (verifier avec `python --version`).
- Modules stdlib uniquement: `json`, `pathlib`, `sys`, `re`, `glob`. Ne pas installer de dependances externes sauf demande explicite.
- Toujours utiliser le Python de l'environnement configure par `configure_python_environment`.

## Quand utiliser ce skill

- Transformation JSON avec plus de 3 niveaux de logique conditionnelle
- Modification de plusieurs fichiers JSON avec des regles differentes par fichier
- Validation de schema avec references croisees (`$ref`) entre fichiers
- Migration de donnees: renommage de cles, restructuration de tableaux, fusion de sources
- Generation de JSON a partir de donnees non-JSON (templates, CSV, logs)
- Application d'un patch complexe sur un arbre de fichiers
- Quand le script `jq` depasserait 5 lignes ou necessiterait des variables intermediaires
- Besoin de `print()` intermediaires pour debugger la transformation

## Quand NE PAS utiliser

- Modification d'un champ unique → `jq-json-patch` (plus rapide, moins de tokens)
- Extraction simple → `jq-json-patch`
- Fusion basique de 2 JSON → `jq-json-patch`
- Fichiers YAML/XML/INI/CSV sans JSON → `multi-format-patch` peut suffire

## Pattern standard

```python
import json
from pathlib import Path

TARGET = Path("chemin/vers/fichier.json")

data = json.loads(TARGET.read_text(encoding="utf-8"))

# Transformation
data["nouvelle_cle"] = "valeur"

TARGET.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print(f"OK: {TARGET}")
```

## Validation incluse

Toujours ajouter une validation apres ecriture:

```python
# Re-lecture de verification
verify = json.loads(TARGET.read_text(encoding="utf-8"))
assert "nouvelle_cle" in verify, "Cle manquante apres ecriture"
print("Validation OK")
```

## Pattern multi-fichiers

```python
from pathlib import Path
import json

BASE = Path("src/main/resources/data")

for json_file in BASE.rglob("*.json"):
    data = json.loads(json_file.read_text(encoding="utf-8"))
    if data.get("type") == "minecraft:crafting_shaped":
        data["conditions"] = [{"type": "forge:mod_loaded", "modid": "examplemod"}]
        json_file.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"Patched: {json_file}")
print("Done")
```

## Procedure

1. **Lire** un echantillon des fichiers cibles avec `read_file` pour comprendre la structure.
2. **Ecrire** le script Python directement dans le prompt (pas de fichier temporaire sauf si > 20 lignes).
3. **Executer** avec `mcp_provides_tool_pylanceRunCodeSnippet` ou `run_in_terminal` selon la taille.
4. **Verifier** le resultat: relire un fichier modifie pour confirmer.
5. **Nettoyer**: le script est dans le prompt, pas de fichier residuel.

## Contraintes Forge 1.20.1

- Preserver `modid` coherent dans tous les fichiers.
- Ne pas casser la structure `assets/<modid>/` ni `data/<modid>/`.
- Les recipes, loot tables, tags doivent rester valides apres transformation.
- Apres modification, lancer `call gradlew.bat build` depuis `project-gradle/`.

## Interdits

- Ne pas installer `pyyaml`, `lxml` ou autre dependance sans demande explicite.
- Ne pas modifier des fichiers sans avoir lu leur structure avant.
- Ne pas laisser de fichier `.py` temporaire dans le workspace sans le nettoyer.
- Ne pas utiliser Python pour une modification qu'un simple `jq` peut faire en une ligne.
