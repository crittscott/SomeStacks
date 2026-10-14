# Server administration

## The config file

Some Stacks stores world policy at `<world>/serverconfig/somestacks-server.toml` on Forge and `<world>/serverconfig/somestacks-server.json` on Fabric, so it travels with the world. `/ss` list edits update the running values and save immediately through the loader. Forge uses its native server-config system and reloads TOML edits while running; `/ss reload` rereads Fabric JSON edits.

| Setting | Section | Default | Effect |
| --- | --- | --- | --- |
| `max_pile_height` | `piles` | `8` | Maximum blocks in one vertical Storage pile, Singles column, or Bar column. Bounds both hand placement and automated growth. Range 1–64. |
| `enable_storage_stack_block` | `stacks` | `true` | When false, no new Storage Stacks are placed or grown. Existing ones keep working. |
| `enable_singles_stack_block` | `stacks` | `true` | The same for Singles Stacks. |
| `enable_bar_stack_block` | `stacks` | `true` | The same for Bar Stacks. |
| `disable_mods` | `compatibility` | empty | Namespaces whose items no stack will accept. |
| `disable_items` | `compatibility` | `evilcraft:broom_part` | Item ids refused on the player deposit gestures. The default lists items known to crash clients when drawn. |
| `placements_per_tick` | `render_gallery` | `64` | Blocks the gallery commands place per tick, floor included. |
| `enabled` | `render_gallery` | `false` | Whether `/ss gallery` and `/ss ingotgallery` can be run at all. |
| `required_permission_level` | `render_gallery` | `3` | Permission level `/ss gallery` and `/ss ingotgallery` require. Range 0–4. |
| `gen_mods` | `render_gallery` | empty | Namespaces the `list` gallery forms build, in the order given. |
| `gen_items` | `render_gallery` | empty | Items `/ss gallery items` builds a row from. |

Disabling a mod or an item bars *new* contents. Anything already stored can still be taken out.

### Ingots

The `somestacks:ingots` item tag decides what a Bar Stack holds — and, by complement, what a Singles Stack refuses. Widening one narrows the other by exactly as much. The shipped tag includes `#forge:ingots`, `#c:ingots`, and `#c:bricks` where those tags exist, the vanilla ingots and bricks, and a list of modded ingots and bricks. A data pack adds to it (see [Pack authors](pack-authors.md)), and `/reload` applies the change.

## Item placement conflicts

Mods that add right-click placement for ordinary items can handle and cancel a click before Some Stacks receives it. Holding the Stack Modifier does not guarantee precedence over another mod. This can create the other mod's block instead of a Some Stacks block, or prevent deposits into an existing stack.

**Caverns & Chasms on Forge** has a known conflict with ingot placement. Disable its item-placement feature in its common config by setting; check its config file.

Gallery-generated stacks can look correct even while manual deposits are intercepted, because gallery construction does not use the right-click placement path.

## Permissions

The `/ss` command is an administrator's tool throughout. Most of it is gated by vanilla's **Level 2**, the gamerule and world-editing level.

The two gallery commands overwrite a region of the world outright without the protection checks a placement gesture answers to, so they answer to server config instead of a fixed level: `render_gallery.enabled` (`false` by default — both commands are refused until an admin turns this on) and `render_gallery.required_permission_level` (default `3`, the server-administration level an operator holds under the default `op-permission-level`).

Some subcommands additionally require a **player** rather than the console, because they act on the sender's own client view or build where the sender stands.

`/ss help` is gated by nothing, so a player who cannot run a subcommand can still read what it needs.

## Command reference

| Command | Gate | Purpose |
| --- | --- | --- |
| `/ss item <item> <mode> [<scale> [<x> <y> [<z>]]]` | Level 2, in game | Set how one item is drawn, in your own view, in memory. |
| `/ss item <item> reset` | Level 2, in game | Drop your entry for that item. |
| `/ss write changed` | Level 2, in game | Save what `/ss item` set to `config/somestacks/item_overrides.json`. |
| `/ss write <modid \| all \| list>` | Level 2, in game | Dump resolved profiles to `config/somestacks/generated_overrides/`. |
| `/ss gallery <modid \| all \| list \| items>` | `render_gallery.required_permission_level` (default 3), in game; disabled by default | Build Storage Stack render galleries. |
| `/ss ingotgallery <modid \| all \| list>` | `render_gallery.required_permission_level` (default 3), in game; disabled by default | Build Bar Stack render galleries. |
| `/ss gen mod add\|remove\|list <modid>` | Level 2 | Edit the gallery namespace list. |
| `/ss gen item add\|remove\|list <item>` | Level 2 | Edit the gallery item list. |
| `/ss deny mod add\|remove\|list <modid>` | Level 2 | Edit the disabled namespace list. |
| `/ss deny item add\|remove\|list <item>` | Level 2 | Edit the disabled item list. |
| `/ss reload` | Level 2 | Re-read world policy and server render overrides, then re-sync every player. |
| `/ss help [<command>]` | Anyone | Command forms, gates, and detail. |

Command edits to the four text lists update both the running server and the config file, so a later save cannot undo them.

An item id is checked against the registry when it is added to a list, because those lists are matched by exact id and a typo would sit there looking effective. A mod id is taken as typed, since it may name a mod that is not installed yet.

### `/ss reload`

Rereads world policy, re-reads `config/somestacks/server_item_overrides/`, and pushes the current synchronized config to every player. Fabric rereads `somestacks-server.json`; Forge reads the values its config system loaded from `somestacks-server.toml`, which it already reloads on its own when the file changes and then re-syncs players. Ingot-tag changes come from data packs and use `/reload`.

### Render galleries

A gallery is a review tool: a single-layer field of stacks over a uniform sandstone floor, built east of you, one column per namespace or item group with that group's rows running north. Each stack shows one layer — nine items for Storage, eight bars for Bar.

Galleries **overwrite** their floor and stack positions directly and do not apply the protection checks a placement gesture answers to. That is why they are disabled by default and, once enabled, default to a permission level above the rest of `ss`. A gallery covering every loaded mod in a large pack is tens of thousands of placements; `placements_per_tick` spreads that over ticks.

## What the server tells the client

On login and `/ss reload`, the server sends each player the three stack-type enable flags and the server render overrides. The disabled-mod and disabled-item lists and the pile settings stay server-side and are never sent.

## Protection and claim mods

On both loaders, the mod checks build limits, replaceability, entity obstruction, the world border, and spawn protection before structural edits. Player gestures — deposit, extract, place, rotate, toggle-permanent — also re-run a real interaction event on both loaders, so claim and protection mods using that hook can veto the action: Forge's `RightClickBlock`, Fabric API's `UseBlockCallback`. Automated growth additionally fires Forge's `EntityPlaceEvent`; Fabric has no equivalent placement event for automation to trigger, so Fabric growth answers to the vanilla checks only.

Blocks the mod removes on its own — an emptied block a settle or a collapse leaves behind — are attributed to the loader's automation actor and reported through a real break event on both loaders: Forge's `BreakEvent`, Fabric API's `PlayerBlockBreakEvents`. **A refused removal leaves the block standing**, and the run carries on around it: the mod will not delete where it may not build.

The server validates every packet independently of the client: the sender, one gesture per player per tick, that the sender is not a spectator, that the target is loaded and within reach, and every operation-specific rule. Deposit targeting and support are recomputed from the player's current view rather than trusted from the packet.
