# Changelog

## Unreleased — Minecraft 1.21.4 port

1.21.4 for Fabric, Forge, and NeoForge. Changes since 0.8.2 (1.21.1).

### Platform

- Forge **54.x** (accepts `[54.1.14,55)`), NeoForge **21.4.x** (accepts `[21.4.158,22)`).
- Fabric Loader **≥ 0.19.5**, Fabric API **≥ 0.119.4+1.21.4**. Common Protection API **1.0.0** is optional.
- Mappings updated to Mojang official + Parchment **2025.03.23-1.21.4**.
- Build toolchain: Architectury Loom **1.17.493**; Gradle **9.5.1** and Shadow **9.4.3** unchanged.
- Client and server must run the same loader build of the mod. Fabric checks this both ways when a player connects.

### Breaking — worlds, configs, and packs

- **Bar Stack contents come from the `somestacks:ingots` item tag alone.** The `compatibility.ingots` config setting and the `/ss ingot` command are gone. Change Bar contents with a data pack (see [Server administration](docs/server-admin.md#ingots)). On server start, any custom entries left in a 1.21.1 config's `ingots` list are logged so they can be moved into the tag.
- **Forge and NeoForge use a TOML config.** Server policy is now a loader-managed SERVER config, `<world>/serverconfig/somestacks-server.toml`, picked up when the file changes. The 1.21.1 `somestacks-server.json` is not read on these loaders; re-enter any custom settings in the TOML. Fabric keeps the JSON file, and `/ss reload` now rereads it.
- **Sounds are registered sound events.** Each type and action has its own `somestacks:block.<type>.<action>` sound with a subtitle, replaceable through an ordinary resource-pack `sounds.json`. The `data/<namespace>/somestacks_sounds/` data-pack format is gone.
- **Server render overrides are gone.** `config/somestacks/server_item_overrides/` is no longer read or sent to clients. Servers distribute item render profiles through their server resource pack instead, and a player's own `item_overrides.json` now takes priority.
- **FTB Chunks integration removed** on Fabric and NeoForge. Fabric claim mods are reached through Common Protection API instead.

### Added

- **Bar Stacks hold bricks on every loader**, including resin brick, with their own brick-shaped bar. Blocks made from bricks are still refused.
- **Common Protection API on Fabric.** When installed, it is asked before player placement, automated growth, and deposits into a neighboring stack. A refused automated insertion keeps its items.
- **Sculk sensors hear stacks.** Placing or removing a stack block and changing its contents emit the matching vibrations.
- **Error cube for crashing item models.** An item whose model throws while drawn in `2d` mode shows a red "ERR" cube until the next resource reload, and the client log names it. `disable_items` now defaults to `evilcraft:broom_part`, an item that crashes clients when drawn.
- **1.21.1 stacks migrate automatically.** Saved stack blocks record their data version, and items from 1.21.1 run through vanilla's data fixers on first load.
- **Unreadable stored items are kept.** An item that cannot load, such as one from a removed mod, is set aside in the block's saved data and returns when it becomes readable. Warnings name the dimension and position.

### Changed

- **`/ss item` and `/ss write` run on the client** and need no permission. The server never asks a client to write files.
- **Gestures use vanilla's block-use pipeline.** Stack clicks are ordinary interactions, so claim, logging, and anti-cheat mods see what they expect. Only the main hand acts.
- **The server picks the cell.** Extraction and soul-torch rotation are aimed by the server along your line of sight, the same way deposits already were.
- **Selection outlines.** Singles and Bar Stacks outline only their occupied cells, and aiming through empty space reaches the block behind.
- **Neighbor deposits respect protection.** Depositing into an adjacent Singles or Bar Stack checks permission at that stack's position on every loader.
- **One automation identity.** All loaders present automated edits as `[SomeStacks]`. Cleanup a player causes is attributed to that player.
- **`/ss reload`** refreshes server policy and re-syncs players.

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
