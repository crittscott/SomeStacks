# Server administration

## The config file

Some Stacks stores loader-neutral world policy at `<world>/serverconfig/somestacks-server.json`, so it travels with the world. `/ss` list edits update the running values and save the file immediately. Run `/ss reload` after editing this file directly.

| Setting | Section | Default | Effect |
| --- | --- | --- | --- |
| `max_pile_height` | `piles` | `8` | Maximum blocks in one vertical Storage pile, Singles column, or Bar column. Bounds both hand placement and automated growth. Range 1–64. |
| `enable_storage_stack_block` | `stacks` | `true` | When false, no new Storage Stacks are placed or grown. Existing ones keep working. |
| `enable_singles_stack_block` | `stacks` | `true` | The same for Singles Stacks. |
| `enable_bar_stack_block` | `stacks` | `true` | The same for Bar Stacks. |
| `disable_mods` | `compatibility` | empty | Namespaces whose items no stack will accept. |
| `disable_items` | `compatibility` | `evilcraft:broom_part` | Item ids refused on player deposits; automation is unaffected. The default excludes an item known to crash clients when drawn. |
| `placements_per_tick` | `render_gallery` | `64` | Blocks the gallery commands place per tick, floor included. |
| `enabled` | `render_gallery` | `false` | Whether `/ss gallery` and `/ss ingotgallery` can be run at all. |
| `required_permission_level` | `render_gallery` | `3` | Permission level `/ss gallery` and `/ss ingotgallery` require. Range 0–4. |
| `gen_mods` | `render_gallery` | empty | Namespaces the `list` gallery forms build, in the order given. |
| `gen_items` | `render_gallery` | empty | Items `/ss gallery items` builds a row from. |

Disabling a mod or an item bars *new* contents. Anything already stored can still be taken out.

## Ingots

What a Bar Stack holds is the item tag `somestacks:ingots`: ingots and bricks, despite the name. Singles Stacks take everything allowed that the tag does not, so widening one narrows the other by exactly as much. There is no config setting or command for it; it is ordinary data-pack data.

The shipped tag includes `#c:ingots`, `#forge:ingots`, and `#c:bricks` (all optional, so a missing one is ignored) plus a hand-picked list of vanilla and modded ingots and brick items. Blocks made from bricks, such as `minecraft:bricks` or brick stairs, are not included. Bricks draw with a brick-shaped bar texture rather than the ingot one. Child tags such as `c:ingots/iron` count only when the parent tag includes them, which the loaders' conventional tags normally do.

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

The server side of `/ss` is an administrator's tool. Most of it is gated by vanilla's **Level 2**, the gamerule and world-editing level. `/ss item` and `/ss write` are the exception: they change only the issuing player's view and files and need no operator permission.

The two gallery commands overwrite a region of the world outright without the protection checks a placement gesture answers to, so they answer to server config instead of a fixed level: `render_gallery.enabled` (`false` by default — both commands are refused until an admin turns this on) and `render_gallery.required_permission_level` (default `3`, the server-administration level an operator holds under the default `op-permission-level`).

The gallery commands also require a **player** rather than the console, because they build where the sender stands.

`/ss help` is available to everyone. Its translated usage forms and access descriptions remain visible even when the player cannot run the command.

## Command reference

| Command | Gate | Purpose |
| --- | --- | --- |
| `/ss item <item> <mode> [<scale> [<x> <y> [<z>]]]` | Anyone, in game | Set how one item is drawn, in your own view, in memory. |
| `/ss item <item> reset` | Anyone, in game | Drop your entry for that item. |
| `/ss write changed` | Anyone, in game | Save what `/ss item` set to `config/somestacks/item_overrides.json`. |
| `/ss write <modid \| all \| list>` | Anyone, in game | Dump resolved profiles to `config/somestacks/generated_overrides/`. |
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

Refreshes server policy and sends client settings only when their synchronized values change. It rereads the active world-policy JSON file on every loader. Gallery enablement or permission changes refresh connected players' command availability. The confirmation reports players online, rather than packets sent; a successful reload returns command success even when nobody is online. Over-limit namespace policies leave the previous active policy intact. This command does not reload render resources or data packs: use a resource reload for rendering changes and vanilla `/reload` for the ingot tag.

### Render galleries

A gallery is a review tool: a single-layer field of stacks over a uniform sandstone floor, built east of you, one column per namespace or item group with that group's rows running north. Each stack shows one layer — nine items for Storage, eight bars for Bar.

Galleries **overwrite** their floor and stack positions directly and do not apply the protection checks a placement gesture answers to. That is why they are disabled by default and, once enabled, default to a permission level above the rest of `ss`. A gallery covering every loaded mod in a large pack is tens of thousands of placements; `placements_per_tick` spreads that over ticks.

## What the server tells the client

On login and whenever the three stack-type enable flags change, the server sends those flags to each player. Deny lists, gallery lists, and pile settings remain server-side. The ingot tag reaches clients through vanilla tag synchronization. Render profiles are supplied by client resource packs, including an ordinary server resource pack.

## Protection and claim mods

On all three loaders, the mod checks build limits, replaceability, entity obstruction, the world border, and spawn protection before structural edits. Player gestures — deposit, extract, place, rotate, toggle-permanent — also re-run a real interaction event on both loaders, so claim and protection mods using that hook can veto the action: Forge's `RightClickBlock`, Fabric API's `UseBlockCallback`. Automated growth additionally fires Forge's `EntityPlaceEvent`; Fabric has no equivalent placement event for automation to trigger, so Fabric growth queries Common Protection API when installed, in addition to vanilla checks.

Blocks the mod removes on its own — an emptied block a settle or a collapse leaves behind — are attributed to the loader's automation actor and reported through a real break event on all three loaders: Forge's `BreakEvent`, Fabric API's `PlayerBlockBreakEvents`. **A refused removal leaves the block standing**, and the run carries on around it: the mod will not delete where it may not build.

The server validates every packet independently of the client: the sender, one gesture per player per tick, that the sender is not a spectator, that the target is loaded and within reach, and every operation-specific rule. Deposit targeting and support are recomputed from the player's current view rather than trusted from the packet.
