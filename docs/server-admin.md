# Server administration

## The config file

Some Stacks stores loader-neutral world policy at `<world>/serverconfig/somestacks-server.json`, so it travels with the world. `/ss` list edits update the running values and save the file immediately. A direct file edit takes effect after restarting the server; `/ss reload` does not reread this JSON.

| Setting | Section | Default | Effect |
| --- | --- | --- | --- |
| `max_pile_height` | `piles` | `8` | Maximum blocks in one vertical Storage pile, Singles column, or Bar column. Bounds both hand placement and automated growth. Range 1–64. |
| `enable_storage_stack_block` | `stacks` | `true` | When false, no new Storage Stacks are placed or grown. Existing ones keep working. |
| `enable_singles_stack_block` | `stacks` | `true` | The same for Singles Stacks. |
| `enable_bar_stack_block` | `stacks` | `true` | The same for Bar Stacks. |
| `disable_mods` | `compatibility` | empty | Namespaces whose items no stack will accept. |
| `disable_items` | `compatibility` | empty | Item ids refused on the player deposit gestures. |
| `ingots` | `compatibility` | Forge: `#forge:ingots*`; Fabric and NeoForge: `#c:ingots*`; all: `#somestacks:ingots` | What a Bar Stack accepts: `#name` is an item-tag pattern, a bare id is one item. |
| `placements_per_tick` | `render_gallery` | `64` | Blocks the gallery commands place per tick, floor included. |
| `enabled` | `render_gallery` | `false` | Whether `/ss gallery` and `/ss ingotgallery` can be run at all. |
| `required_permission_level` | `render_gallery` | `3` | Permission level `/ss gallery` and `/ss ingotgallery` require. Range 0–4. |
| `gen_mods` | `render_gallery` | empty | Namespaces the `list` gallery forms build, in the order given. |
| `gen_items` | `render_gallery` | empty | Items `/ss gallery items` builds a row from. |

Disabling a mod or an item bars *new* contents. Anything already stored can still be taken out.

### Ingots

`ingots` decides what a Bar Stack holds — and, by complement, what a Singles Stack refuses. Widening one narrows the other by exactly as much.

An entry beginning with `#` names an item tag and may contain `*`, matching a run of any characters. The Forge default `#forge:ingots*` covers `#forge:ingots` itself and every `forge:ingots/<metal>` beneath it. Fabric and NeoForge use the corresponding conventional `#c:ingots*` default.

Any other entry is a single item id, for accepting one hand-picked item without a data pack tag. A bare id is checked against the registry when added; a `#` tag entry is taken as typed, since it may name a tag no loaded data pack declares. To hand-pick a larger set, declare an item tag in a data pack and add it here with `#` — the mod ships `#somestacks:ingots` for exactly this.

The item set is re-resolved whenever the config changes *and* whenever tags are bound, so a data pack reload is picked up without touching the config.

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
| `/ss ingot add\|remove\|list <#tag \| item>` | Level 2 | Edit the ingot list — `#` tag patterns and item ids. |
| `/ss reload` | Level 2 | Re-read server render overrides and re-sync every player. |
| `/ss help [<command>]` | Anyone | Command forms, gates, and detail. |

Command edits to the five text lists update both the running server and the config file, so a later save cannot undo them.

An item id is checked against the registry when it is added to a list, because those lists are matched by exact id and a typo would sit there looking effective. A mod id is taken as typed, since it may name a mod that is not installed yet.

### `/ss reload`

Re-reads `config/somestacks/server_item_overrides/` and pushes the current synchronized config to every player. It does **not** re-read `<world>/serverconfig/somestacks-server.json`; restart the server after editing that file directly.

### Render galleries

A gallery is a review tool: a single-layer field of stacks over a uniform sandstone floor, built east of you, one column per namespace or item group with that group's rows running north. Each stack shows one layer — nine items for Storage, eight bars for Bar.

Galleries **overwrite** their floor and stack positions directly and do not apply the protection checks a placement gesture answers to. That is why they are disabled by default and, once enabled, default to a permission level above the rest of `ss`. A gallery covering every loaded mod in a large pack is tens of thousands of placements; `placements_per_tick` spreads that over ticks.

## What the server tells the client

On login and `/ss reload`, the server sends each player the three stack-type enable flags and the server render overrides. The disabled-mod and disabled-item lists, the ingot list, and the pile settings stay server-side and are never sent.

## Protection and claim mods

On both loaders, the mod checks build limits, replaceability, entity obstruction, the world border, and spawn protection before structural edits. Player gestures — deposit, extract, place, rotate, toggle-permanent — also re-run a real interaction event on both loaders, so claim and protection mods using that hook can veto the action: Forge's `RightClickBlock`, Fabric API's `UseBlockCallback`. Automated growth additionally fires Forge's `EntityPlaceEvent`; Fabric has no equivalent placement event for automation to trigger, so Fabric growth answers to the vanilla checks only.

Blocks the mod removes on its own — an emptied block a settle or a collapse leaves behind — are attributed to the loader's automation actor and reported through a real break event on both loaders: Forge's `BreakEvent`, Fabric API's `PlayerBlockBreakEvents`. **A refused removal leaves the block standing**, and the run carries on around it: the mod will not delete where it may not build.

The server validates every packet independently of the client: the sender, one gesture per player per tick, that the sender is not a spectator, that the target is loaded and within reach, and every operation-specific rule. Deposit targeting and support are recomputed from the player's current view rather than trusted from the packet.
