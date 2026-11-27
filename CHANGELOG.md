# Changelog

## 0.8.3 — Minecraft 1.21.4

For Fabric, Forge, and NeoForge.

### Changed

- Stack gestures use vanilla block interactions, with deposit, extraction, and rotation targets selected by the server from the clicked point and line of sight. Only the main hand acts.
- Placement, neighboring-stack deposits, growth, and cleanup respect protection checks. Automated edits use `[SomeStacks]`; cleanup caused by a player, including the collapse or trimming that breaking a stack causes, retains that player's attribution.
- `/ss item` and `/ss write` run locally without operator permission. Item render profiles resolve local overrides first, then resource packs, then automatic measurement. Servers distribute profiles through their server resource pack instead of server override files.
- Stack actions use registered sound events with subtitles, customizable through resource-pack `sounds.json`. The old data-pack sound mappings are removed.
- Server policy uses `somestacks-server.toml` on every loader. Existing world JSON policy migrates automatically; the original is retained as `somestacks-server.json.migrated`. Existing TOML takes precedence.
- Bar Stacks accept ingots and bricks through the `somestacks:ingots` item tag alone. The `compatibility.ingots` setting and `/ss ingot` command are removed; move custom entries into a data pack. Existing custom entries are logged during config migration.
- `/ss reload` refreshes server policy and synchronizes changed client settings. Use vanilla `/reload` for item-tag changes.

### Added and fixed

- Bar Stacks render bricks with their own brick-shaped bars. Blocks made from bricks remain excluded.
- Stack placement, removal, and content changes emit vibrations for sculk sensors.
- Saved items record their data version and migrate on load. Unreadable items remain in saved recovery data and retry on later loads; cleanup preserves blocks containing recovery data.
- Singles and Bar selection outlines follow occupied cells, allowing clicks through empty space.
- Stacks no longer send nearby players the contents of stored containers such as shulker boxes, or the pages of stored books; neither is drawn, and both stay saved.
- A selected placement type the server disables is replaced by the next enabled mode when the policy arrives, and placing a disabled type or depositing into a full Storage pile at maximum height now explains the refusal.
- Placing a stack and growing one by depositing answer to the same build check. In Adventure mode, growing a Storage pile now needs a held item whose `can_place_on` names the pile's top block, just as placing a stack does.
- Forge and NeoForge let another mod's earlier right-click handling on a block take precedence on the client, as it already did on the server and on Fabric.
- On Fabric, the empty-hand mode cycle no longer fires while mining a block or steering a boat.
- Item rendering caches prepared profiles and presentations, refreshes them after edits or resource reloads, and displays a red "ERR" cube when a flat item model fails to render.
- `/ss write changed` no longer replaces `config/somestacks/item_overrides.json` when the file cannot be read; it fails, and the log names the file and the problem. Entries in the file that cannot be used are kept exactly as written instead of being dropped on the next save.
- `/ss gen mod list` shows the list in alphabetical order, the order galleries use. Command messages are whole sentences, so they can be translated, and `/ss help` states list limits and the default scale from the values the mod uses.
- Expanded loader GameTest coverage for interactions, protection, automation, persistence, and migration.

### Protection integrations

- Fabric supports optional Common Protection API queries. Direct FTB Chunks integration is removed; Fabric claim mods are reached through Common Protection API and block-use callbacks. Forge and NeoForge use native protection hooks.
- The startup log names the mod version, network protocol, and the protection mods queried directly.

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
