# Server administration

These instructions apply to Minecraft 26.3. Use matching client/server Minecraft and loader builds. Back up worlds before upgrading. Custom data packs use format **121.0**; server resource packs use **97.1** (see [Pack authors](pack-authors.md)).

## The config file

Server policy is stored in documented TOML; loader-specific locations are:

- **NeoForge:** `config/somestacks-server.toml`, a loader-managed SYNCED config; optional `<world>/syncedconfig/somestacks-server.toml` overrides it. The loader watches edits.
- **Fabric:** `config/somestacks-server.toml` supplies instance defaults; `<world>/serverconfig/somestacks-server.toml`, when present, overrides it for that world. Every setting has an explanation. After editing the active file, run `/ss reload`. Malformed fields use their defaults and are logged; a TOML syntax error uses the complete defaults.

`/ss deny` and `/ss gen` edits update the running values and save the file immediately.

| Setting | Section | Default | Effect |
| --- | --- | --- | --- |
| `max_pile_height` | `piles` | `8` | Maximum blocks in one vertical Storage pile, Singles column, or Bar column. Bounds both hand placement and automated growth. Range 1–64. |
| `enable_storage_stack_block` | `stacks` | `true` | When false, no new Storage Stacks are placed or grown. Existing ones keep working. |
| `enable_singles_stack_block` | `stacks` | `true` | The same for Singles Stacks. |
| `enable_bar_stack_block` | `stacks` | `true` | The same for Bar Stacks. |
| `disable_mods` | `compatibility` | empty | Namespaces whose items players and automation may not put into stacks. |
| `disable_items` | `compatibility` | `evilcraft:broom_part` | Item ids refused on player deposits. Automation is not affected. The default lists items that crash clients when drawn. |
| `placements_per_tick` | `render_gallery` | `64` | Blocks the gallery commands place per tick, floor included. |
| `enabled` | `render_gallery` | `false` | Whether `/ss gallery` and `/ss ingotgallery` can be run at all. |
| `required_permission_level` | `render_gallery` | `3` | Permission level `/ss gallery` and `/ss ingotgallery` require. Range 0–4. |
| `gen_mods` | `render_gallery` | empty | Namespaces the `list` gallery forms build, in the order given. |
| `gen_items` | `render_gallery` | empty | Items `/ss gallery items` builds a row from. |

Disabling a type, mod, or item bars *new* contents. Anything already stored can still be taken out.

Existing world JSON policy is migrated once to a world TOML override; the original is retained as `somestacks-server.json.migrated`. An existing TOML takes precedence. Invalid JSON that cannot be migrated is retained for repair. Custom `compatibility.ingots` entries are logged for transfer to a data-pack item tag.

## Ingots

What a Bar Stack holds is the item tag `somestacks:ingots`: ingots and bricks, despite the name. Singles Stacks take everything allowed that the tag does not, so widening one narrows the other by exactly as much. There is no config setting or command for it; it is ordinary data-pack data.

The shipped tag includes `#c:ingots` and `#c:bricks` (both optional, so a missing one is ignored) plus a hand-picked list of vanilla and modded ingots and brick items. Blocks made from bricks, such as `minecraft:bricks` or brick stairs, are not included. Bricks draw with a brick-shaped bar texture rather than the ingot one. Child tags such as `c:ingots/iron` count only when the parent tag includes them, which the loaders' conventional tags normally do.

To change it, add a data pack to the world, e.g. `<world>/datapacks/my_ingots/`, containing `pack.mcmeta` and `data/somestacks/tags/item/ingots.json`:

```json
{
  "replace": false,
  "values": [
    "examplemod:steel_ingot",
    { "id": "#othermod:alloys", "required": false }
  ]
}
```

- `"replace": false` adds to the shipped list. `"replace": true` discards it, so the tag holds only what you list.
- An entry is an item id or `#` and a tag id. Mark entries from mods that may be absent `"required": false`; a missing required entry makes the whole tag fail to load.
- You cannot remove a single entry from the shipped list. To drop one, use `"replace": true` and list everything you want to keep.

Run `/reload` to apply the change. Items already in a stack stay where they are; the tag only decides what new deposits a Bar or Singles Stack accepts. `/ss ingotgallery` shows what the current tag accepts.

## Permissions

The server side of `/ss` is an administrator's tool. Most of it is gated by vanilla's **Level 2**, the gamerule and world-editing level. `/ss item` and `/ss write` are the exception: they run entirely on the player's own client, change only that player's view and files, and need no permission.

The two gallery commands overwrite a region of the world outright without the protection checks a placement gesture answers to, so they answer to server config instead of a fixed level: `render_gallery.enabled` (`false` by default — both commands are refused until an admin turns this on) and `render_gallery.required_permission_level` (default `3`, the server-administration level an operator holds under the default `op-permission-level`).

The gallery commands also require a **player** rather than the console, because they build where the sender stands.

`/ss help` is available to everyone. Its translated usage forms and access descriptions remain visible even when the player cannot run the command.

## Command reference

| Command | Gate | Purpose |
| --- | --- | --- |
| `/ss item <item> <mode> [<scale> [<x> <y> [<z>]]]` | Anyone, client-local | Set how one item is drawn, in your own view, in memory. |
| `/ss item <item> reset` | Anyone, client-local | Drop your entry for that item. |
| `/ss write changed` | Anyone, client-local | Save what `/ss item` set to `config/somestacks/item_overrides.json`. |
| `/ss write <modid \| all \| list>` | Anyone, client-local | Dump resolved profiles to `config/somestacks/generated_overrides/`. |
| `/ss gallery <modid \| all \| list \| items>` | `render_gallery.required_permission_level` (default 3), in game; disabled by default | Build Storage Stack render galleries. |
| `/ss ingotgallery <modid \| all \| list>` | `render_gallery.required_permission_level` (default 3), in game; disabled by default | Build Bar Stack render galleries. |
| `/ss gen mod add\|remove\|list <modid>` | Level 2 | Edit the gallery namespace list. |
| `/ss gen item add\|remove\|list <item>` | Level 2 | Edit the gallery item list. |
| `/ss deny mod add\|remove\|list <modid>` | Level 2 | Edit the disabled namespace list. |
| `/ss deny item add\|remove\|list <item>` | Level 2 | Edit the disabled item list. |
| `/ss reload` | Level 2 | Refresh policy and synchronize changed client settings; report players online. |
| `/ss help [<command>]` | Anyone | Command forms, gates, and detail. |

Command edits to the four text lists update both the running server and the config file, so a later save cannot undo them.

Item ids are checked against the registry when added to a list. Mod namespaces are trimmed, lowercased, validated, and deduplicated; the mod need not be installed. Each mod list allows at most 256 unique namespaces of at most 256 characters. Malformed file entries are logged and omitted. A replacement exceeding either limit is rejected as a whole, retaining the active policy (defaults at startup); rejected command additions and reloads report failure.

### `/ss reload`

Refreshes server policy and sends client settings only when their synchronized values change. Fabric rereads the active `somestacks-server.toml`. NeoForge reads the values published by its native TOML config lifecycle. Gallery enablement or permission changes refresh connected players' command availability. The confirmation reports players online, rather than packets sent; a successful reload returns command success even when nobody is online. Over-limit namespace policies leave the previous active policy intact. This command does not reload render resources or data packs: use a resource reload for rendering changes and vanilla `/reload` for the ingot tag.

### Render galleries

A gallery is a review tool: a single-layer field of stacks over a uniform sandstone floor, built east of you, one column per namespace or item group with that group's rows running north. Each stack shows one layer — nine items for Storage, eight bars for Bar.

Galleries **overwrite** their floor and stack positions directly and do not apply the protection checks a placement gesture answers to. That is why they are disabled by default and, once enabled, default to a permission level above the rest of `ss`. A gallery covering every loaded mod in a large pack is tens of thousands of placements; `placements_per_tick` spreads that over ticks.

## What the server tells the client

On login and whenever synchronized settings change, the server sends each player the three stack-type enable flags and the namespace lists the client needs. The disabled-item list and the pile settings stay server-side. The ingot tag reaches clients the vanilla way, with the other item tags.

Item appearance is client resource data. To give players shared render profiles or Bar textures, ship them in a server resource pack.

## Protection and claim mods

On both loaders, structural edits answer to build limits, replaceability, obstruction, border, and spawn protection. Player gestures use the real vanilla interaction pipeline; the mod never invents additional right-click events. NeoForge fires native placement events before publishing new blocks, restoring full snapshots on denial, and consults destination interaction protection before a deposit reaches an adjacent Singles or Bar Stack. Fabric queries Common Protection API for destination interaction, placement, growth, and removal. The API is bundled and required; claim mods provide its permission rules.

Blocks the mod removes during cleanup fire native break hooks: NeoForge `BreakEvent`, or Fabric's `PlayerBlockBreakEvents` lifecycle. Player cleanup retains the player actor; automated or mixed deferred Storage edits use `[SomeStacks]`. A refused removal leaves the empty block standing. Extracting the final Single or Bar emits destruction only when cleanup succeeds; a surviving block emits change.

The gesture payload carries placement mode and modifier state; actions use vanilla block-use packets and their actual hit results. The server validates the main hand, spectator state, held item, target, support, enablement, height, obstruction, and edit authority. Cell targeting is recomputed server-side along the reach ray through the hit point.
