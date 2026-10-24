# Manual install for the AniStream WASM stub

This repo includes a minimal generator that downloads the registry file from:

https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json

and writes a tiny WASM module plus a metadata file for manual placement.

## Create the files

From the repository root:

```bash
node scripts/generate-anistream-plugin.js
```

This produces:

- `anistream_store_plugin.wasm`
- `anistream_store_plugin.json`

## Manual placement

Because the app/admin panel cannot add the plugin for you, place the generated file manually in the AniStream plugin directory.

### Windows

```powershell
%APPDATA%\AniStream\plugins\
```

or:

```powershell
%LOCALAPPDATA%\AniStream\plugins\
```

Copy the `.wasm` file into that folder.

### macOS

```bash
~/Library/Application Support/AniStream/plugins/
```

Copy the `.wasm` file into that folder.

### Linux

```bash
~/.config/AniStream/plugins/
```

or:

```bash
~/.local/share/AniStream/plugins/
```

Copy the `.wasm` file into that folder.

## Restart and re-check

1. Close AniStream completely.
2. Re-open it.
3. Check the plugin list or extensions panel.
4. If it still does not show, look for a `pluginPath`, `pluginsFolder`, or `pluginDir` option in the app config and set it to the same folder you copied the `.wasm` file into.

## If the app still ignores the plugin

Some builds only accept plugins of a specific file naming pattern or only read subfolders under a configured directory. In that case:

- rename the file to match the app's expected plugin name
- place it in the exact directory the app checks for plugins
- restart the app again

## Notes

- This is a minimal WASM stub intended for manual placement; it is not a full in-app plugin implementation.
- The JSON registry URL is used as the source metadata for the generated bundle.
- If your AniStream build requires a different naming convention, adjust the generated file name before copying it into the plugin directory.
