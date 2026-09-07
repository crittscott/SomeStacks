# Changelog

## 0.8.2 — Minecraft 1.21.1 port (2026-09-07)

This release moves Some Stacks from Minecraft 1.20.1 to 1.21.1 and adds NeoForge as a
first-class loader alongside Forge and Fabric.

### Platform

- Forge **52.x** (dev pin `1.21.1-52.1.16`, accepts `[52.0.0,53)`); was Forge 47.x.
- Fabric Loader **≥ 0.16.0**, Fabric API **≥ 0.102.0+1.21.1**.
- Mappings updated to Mojang official + Parchment **2024.11.17-1.21.1**.
- Build toolchain: Gradle wrapper **9.5.1**, Architectury Loom **1.17.491**, Shadow **9.4.3**.

### Breaking — worlds and data packs

- **Stack contents from the 1.20.1 version are not migrated.** Local inventory is now
  persisted through Data Components and a `HolderLookup.Provider`; pre-1.21 stored data is
  not read. Empty your stacks before loading a world with this build, or expect their
  contents to be lost.
- **Server policy key renamed.** `compatibility.ingot_tags` is now `compatibility.ingots` in
  `<world>/serverconfig/somestacks-server.json`. Existing files must be edited by hand:
  rename the key and prefix every tag-pattern entry with `#` (see below).
- **Bundled ingot tag moved to the 1.21 path** `data/<namespace>/tags/item/` and
  `pack.mcmeta` now declares data pack format 48 (supported range 34–48). Data packs that
  extended `somestacks:ingots` under the old `tags/items/` path must move their files.

### Removed

- **Open Parties and Claims integration.** No 1.21.1 build of OPAC exists. FTB Chunks
  protection remains and now also covers NeoForge.
- **JUnit test scaffolding.** All automated testing is now the per-loader GameTest suites
  (`:<loader>:runGameTestServer`), none of which run as part of `build`.
- **Architectury API runtime dependency.** Architectury is build-time only now; no loader
  ships an Architectury API runtime dependency.

### Changed

- **Bar Stack ingot list accepts bare item ids.** Each `compatibility.ingots` entry is
  either a `#`-prefixed item-tag pattern (`*` still allowed) or a bare item id that
  contributes just that one item. `/ss ingot add` registry-checks a bare id and passes `#`
  entries through untyped. Defaults are `#forge:ingots*` / `#c:ingots*` plus
  `#somestacks:ingots`.
- **Loader minimum versions lowered** as far as they can go to widen modpack compatibility.
- **Bundled render overrides refreshed** for several furniture and decoration mods
  (Another Furniture, Better End, MCW Stairs railings now render 3-D, and others).
- **Startup and configuration logging expanded** — loader initialization, config
  provenance, incompatible Fabric clients, and active FTB Chunks protection are logged;
  routine model-bake and tint-analysis noise is reduced.
