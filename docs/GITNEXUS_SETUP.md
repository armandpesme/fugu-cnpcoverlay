# GitNexus — configuration locale

GitNexus est utilisé via MCP pour l’intelligence locale du code. Cette
configuration reste indépendante de l’application ou de l’éditeur utilisé.

## Active mode

- Installation globale: `gitnexus@1.6.9`
- Setup ciblé: `gitnexus setup -c <client-config>`
- Commande MCP: `gitnexus mcp`
- Codex config: `.codex/config.toml`
- Local index: `.gitnexus/`
- Nom du dépôt GitNexus: choisir un nom local propre au dépôt

## Re-index

Run from workspace root:

```powershell
gitnexus analyze --index-only --name <index-name>
gitnexus list
gitnexus status
```

Fallback:

```powershell
npx -y gitnexus@latest analyze --index-only --name <index-name>
```

Le mode `--index-only` est obligatoire ici : il empêche GitNexus de générer des
fichiers de contexte ou des skills pour d’autres agents. Ne pas ajouter de copie
npm locale telle que `.tools/gitnexus/` quand l’installation globale fonctionne.

## Réinstallation propre

Depuis la racine du workspace:

```powershell
gitnexus setup -c <client-config>
gitnexus clean --force
gitnexus analyze --index-only --name <index-name>
gitnexus status
```
