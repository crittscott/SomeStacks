# Changelog

## 0.8.3 — unreleased

- Stack actions use vanilla block interactions. Only mode/modifier state is sent separately; the server selects deposit, extraction, and rotation cells from the actual hit ray.
- `/ss item` and `/ss write` run locally without operator permission. Render profiles resolve user overrides, resource packs, then automatic measurement; servers share profiles through a server resource pack. Server-imposed override files and their network commands are removed.
- Stack actions use registered sound events with subtitles, replaceable through resource-pack `sounds.json`. Server data-pack sound mappings are removed.
- Placement and cleanup use native protection hooks, snapshot rollback, a shared automation identity, and player attribution for player-caused cleanup. Fabric adds optional Common Protection API queries; direct Fabric/NeoForge FTB Chunks placement checks remain.
- Saved items record their data version. Preceding-release NBT and component-format saves migrate on load, including layout rotation and permanence. Unreadable entries remain in saved recovery data, retry on load, and prevent automatic cleanup from deleting their block.
- Item rendering caches prepared presentations and profiles, invalidates them on edits/reloads, and displays an error cube for failed flat model capture. Measurement uses the 1.21.1 renderer and a new cache format.
- Shared mechanics retain whole-run automation, positional Singles/Bar insertion, comparator batching, and the distinct gravity/backfill behaviors. NeoForge and Forge retain their native `IItemHandler` APIs.
- Server policy uses TOML on every loader; preceding-release world JSON migrates with an archived original. Ingot/brick membership uses the `somestacks:ingots` data-pack tag.
- Loader GameTest suites cover interactions, protection, profile precedence, persistence, recovery, and mixed-format migration. Development client runs provide `/ssclienttest`; Fabric supports provider-present and provider-absent protection checks.

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
