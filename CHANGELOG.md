# Changelog

## 0.8.3 — Minecraft 1.20.1

For Fabric and Forge.

### Changed

- Stack gestures use vanilla block interactions, with deposit, extraction, and rotation targets selected by the server from the clicked point and line of sight. Only the main hand acts.
- Placement, neighboring-stack deposits, growth, and cleanup respect protection checks. Automated edits use `[SomeStacks]`; cleanup caused by a player retains that player's attribution.
- `/ss item` and `/ss write` run locally without operator permission. Item render profiles resolve local overrides first, then resource packs, then automatic measurement. Servers distribute profiles through their server resource pack instead of server override files.
- Stack actions use registered sound events with subtitles, customizable through resource-pack `sounds.json`. The old data-pack sound mappings are removed.
- Server policy uses `somestacks-server.toml` on every loader, with the same settings, ranges, and explanations on both. On Fabric, existing world JSON policy migrates automatically; the original is retained as `somestacks-server.json.migrated`. Existing TOML takes precedence.
- Bar Stacks accept ingots and bricks through the `somestacks:ingots` item tag alone. The `compatibility.ingots` setting and `/ss ingot` command are removed; move custom entries into a data pack. On Fabric, existing custom entries are logged during config migration.
- `/ss reload` refreshes server policy and synchronizes changed client settings. Use vanilla `/reload` for item-tag changes.

### Added and fixed

- Bar Stacks render bricks with their own brick-shaped bars. Blocks made from bricks remain excluded.
- Stack placement, removal, and content changes emit vibrations for sculk sensors.
- Saved items record their data version and migrate on load. Unreadable items remain in saved recovery data and retry on later loads; cleanup preserves blocks containing recovery data.
- Singles and Bar selection outlines follow occupied cells, allowing clicks through empty space.
- Item rendering caches prepared profiles and presentations, refreshes them after edits or resource reloads, and displays a red "ERR" cube when a flat item model fails to render.
- `/ss write changed` never overwrites a render override file it could not read, and keeps entries it cannot use exactly as written.
- `/ss deny item add` and `/ss gen item add` complete item ids from a bare name, as vanilla commands do. `/ss gen mod list` shows the list in alphabetical order, the order galleries use.
- Holding the Stack Modifier never uses the main-hand item, including after a block click no gesture handles (for example while sneaking), so food, bows, thrown items, and buckets no longer appear to act and then resync.
- A selected placement type the server disables moves to the next enabled mode at once. Placing a disabled type, or depositing into a full Storage pile at maximum height, now explains the refusal above the hotbar.
- On Fabric, the Stack Modifier no longer cycles the mode while breaking a block or steering a boat. On Forge, another mod that handles a right-click on a block first keeps it on the client as it already did on the server.
- Stacks no longer send nearby players the contents of stored shulker boxes and other filled containers, or the pages of stored books, which they never drew.
- In Adventure mode, a Storage deposit that grows the pile follows the same rule as placing a stack: the held item's `CanPlaceOn` list.
- Expanded loader GameTest coverage for interactions, protection, automation, persistence, and migration.

### Protection integrations

- Fabric supports optional Common Protection API queries and direct FTB Chunks and Open Parties and Claims placement/growth checks. Forge uses native interaction, placement, and break hooks.
