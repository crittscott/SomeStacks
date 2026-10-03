# Changelog

## Unreleased — Minecraft 1.21.3 port

1.21.3 for Fabric, Forge, and Neoforge.

### Platform

- Forge **53.x** (accepts `[53.1.12,54)`), NeoForge **21.3.x** (accepts `[21.3.97,22)`).
- Fabric Loader **≥ 0.19.5**, Fabric API **≥ 0.114.1+1.21.3**.
- Mappings updated to Mojang official + Parchment **2024.12.07-1.21.3**.

### Removed

- **FTB Chunks integration.** No 1.21.3 build of FTB Chunks exists. On NeoForge, automated growth answers to the ordinary block-place event alone.

### Changed

- **Fabric automated growth checks Common Protection API.** When a claim mod that implements Patbox's Common Protection API is installed, it can refuse growth of a stack into its claims.
- **Stack contents from 1.21.1 migrate automatically.** Saved stack blocks now record their data version. The first time a 1.21.1 block entity loads, its stored items are run through vanilla's DataFixerUpper so item component changes made in 1.21.2 apply. No player action needed.
- **Unreadable stored items are kept, not dropped.** An item that cannot be loaded, such as one from a removed mod, is set aside in the block's saved data with a log warning, and returns on a later load if the item becomes readable again.

## 0.8.2 — Minecraft 1.21.1 port 

1.21.1 for Fabric, Forge, and Neoforge.

### Platform

- Forge **52.x** (dev pin `1.21.1-52.1.16`, accepts `[52.0.0,53)`); was Forge 47.x.
- Fabric Loader **≥ 0.16.0**, Fabric API **≥ 0.102.0+1.21.1**.
- Mappings updated to Mojang official + Parchment **2024.11.17-1.21.1**.
- Build toolchain: Gradle wrapper **9.5.1**, Architectury Loom **1.17.491**, Shadow **9.4.3**.

### Breaking — worlds and data packs

- **Server policy key renamed.** `compatibility.ingot_tags` is now `compatibility.ingots` in `<world>/serverconfig/somestacks-server.json`. Existing files must be edited by hand: rename the key and prefix every tag-pattern entry with `#` (see below).
- **Bundled ingot tag moved to the 1.21 path** `data/<namespace>/tags/item/` and `pack.mcmeta` now declares data pack format 48 (supported range 34–48). Data packs that extended `somestacks:ingots` under the old `tags/items/` path must move their files.

### Removed

- **Open Parties and Claims integration.** No 1.21.1 build of OPAC exists. FTB Chunks protection remains and now also covers NeoForge.
- **JUnit test scaffolding.** All automated testing is now the per-loader GameTest suites (`:<loader>:runGameTestServer`), none of which run as part of `build`.
- **Architectury API runtime dependency.** Architectury is build-time only now; no loader ships an Architectury API runtime dependency.

### Changed

- **Stack contents from 1.20.1 migrate automatically.** The first time a pre-1.21 block entity loads, its stored items are upgraded from the old NBT item format to Data Components using vanilla's DataFixerUpper, then rewritten to disk in the new format on the next save. No player action needed.
- **Bar Stack ingot list accepts bare item ids.** Each `compatibility.ingots` entry is either a `#`-prefixed item-tag pattern (`*` still allowed) or a bare item id that contributes just that one item. `/ss ingot add` registry-checks a bare id and passes `#` entries through untyped. Defaults are `#forge:ingots*` / `#c:ingots*` plus `#somestacks:ingots`.
- **Loader minimum versions lowered** as far as they can go to widen modpack compatibility.
- **Bundled render overrides refreshed** for several furniture and decoration mods (Another Furniture, Better End, MCW Stairs railings now render 3-D, and others).
- **Startup and configuration logging expanded** — loader initialization, config provenance, incompatible Fabric clients, and active FTB Chunks protection are logged; routine model-bake and tint-analysis noise is reduced.
