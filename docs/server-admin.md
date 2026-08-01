# Server administration

## The config file

Some Stacks uses a Forge **server** config, so it lives with the world at `<world>/serverconfig/somestacks-server.toml` and travels with it. Editing the file directly takes effect when Forge reports the config reloaded.

| Setting | Section | Default | Effect |
| --- | --- | --- | --- |
| `max_pile_height` | `piles` | `8` | Maximum blocks in one vertical Storage pile, Singles column, or Bar column. Bounds both hand placement and automated growth. Range 1–64. |
| `enable_storage_stack_block` | `stacks` | `true` | When false, no new Storage Stacks are placed or grown. Existing ones keep working. |
| `enable_singles_stack_block` | `stacks` | `true` | The same for Singles Stacks. |
| `enable_bar_stack_block` | `stacks` | `true` | The same for Bar Stacks. |
| `disable_mods` | `compatibility` | empty | Namespaces whose items no stack will accept. |
| `disable_items` | `compatibility` | empty | Item ids refused on the player deposit gestures. |
| `ingot_tags` | `compatibility` | `forge:ingots*`, `somestacks:ingots` | Item tags whose contents a Bar Stack accepts. |
| `placements_per_tick` | `render_gallery` | `64` | Blocks the gallery commands place per tick, floor included. |
| `gen_mods` | `render_gallery` | empty | Namespaces the `list` gallery forms build, in the order given. |
| `gen_items` | `render_gallery` | empty | Items `/ss gallery items` builds a row from. |

Disabling a mod or an item bars *new* contents. Anything already stored can still be taken out.

### Ingot tags

`ingot_tags` decides what a Bar Stack holds — and, by complement, what a Singles Stack refuses. Widening one narrows the other by exactly as much.

An entry may contain `*`, matching a run of any characters. That is why the default `forge:ingots*` works so broadly: it covers `forge:ingots` itself and every `forge:ingots/<metal>` beneath it, which reaches mods that tag their ingots only under the child tag without adding to the parent.

To accept a hand-picked set of items, declare an item tag holding them in a data pack and add that tag here. The mod ships `somestacks:ingots` for exactly this purpose.

The item set is re-resolved whenever the config changes *and* whenever tags are bound, so a data pack reload is picked up without touching the config.

## Permissions

The `/ss` command is an administrator's tool throughout. Gates are vanilla permission levels:

- **Level 2** — the gamerule and world-editing level. Most of `ss`.
- **Level 3** — the server-administration level, which an operator holds under the default `op-permission-level`. The two gallery commands, because they overwrite a region of the world outright without the protection checks a placement gesture answers to.

Some subcommands additionally require a **player** rather than the console, because they act on the sender's own client view or build where the sender stands.

`/ss help` is gated by nothing, so a player who cannot run a subcommand can still read what it needs.

## Command reference

| Command | Gate | Purpose |
| --- | --- | --- |
| `/ss item <item> <mode> [<scale> [<x> <y> [<z>]]]` | Level 2, in game | Set how one item is drawn, in your own view, in memory. |
| `/ss item <item> reset` | Level 2, in game | Drop your entry for that item. |
| `/ss write changed` | Level 2, in game | Save what `/ss item` set to `config/somestacks/item_overrides.json`. |
| `/ss write <modid \| all \| list>` | Level 2, in game | Dump resolved profiles to `config/somestacks/generated_overrides/`. |
| `/ss gallery <modid \| all \| list \| items>` | Level 3, in game | Build Storage Stack render galleries. |
| `/ss ingotgallery <modid \| all \| list>` | Level 3, in game | Build Bar Stack render galleries. |
| `/ss gen mod add\|remove\|list <modid>` | Level 2 | Edit the gallery namespace list. |
| `/ss gen item add\|remove\|list <item>` | Level 2 | Edit the gallery item list. |
| `/ss deny mod add\|remove\|list <modid>` | Level 2 | Edit the disabled namespace list. |
| `/ss deny item add\|remove\|list <item>` | Level 2 | Edit the disabled item list. |
| `/ss ingot add\|remove\|list <tag>` | Level 2 | Edit the ingot tag patterns. |
| `/ss reload` | Level 2 | Re-read server render overrides and re-sync every player. |
| `/ss help [<command>]` | Anyone | Command forms, gates, and detail. |

Command edits to the five text lists update both the running server and the config file, so a later save cannot undo them.

An item id is checked against the registry when it is added to a list, because those lists are matched by exact id and a typo would sit there looking effective. A mod id is taken as typed, since it may name a mod that is not installed yet.

### `/ss reload`

Re-reads `config/somestacks/server_item_overrides/` and pushes the current synchronized config to every player. It does **not** re-read the Forge server config file — a direct edit to that applies through Forge's own config reload.

### Render galleries

A gallery is a review tool: a single-layer field of stacks over a uniform sandstone floor, built east of you, one column per namespace or item group with that group's rows running north. Each stack shows one layer — nine items for Storage, eight bars for Bar.

Galleries **overwrite** their floor and stack positions directly and do not apply the protection checks a placement gesture answers to. That is why they sit a permission level above the rest of `ss`. A gallery covering every loaded mod in a large pack is tens of thousands of placements; `placements_per_tick` spreads that over ticks.

## What the server tells the client

On login, and on a server-config reload, the server sends each player the three stack-type enable flags and the server render overrides. The disabled-mod and disabled-item lists, the ingot tags, and the pile settings stay server-side and are never sent.

## Protection and claim mods

The mod's gestures replace the vanilla interactions the client suppresses, so the server re-runs the checks a vanilla interaction would have triggered: world border, spawn protection, Forge's `RightClickBlock`, `EntityPlaceEvent`, and `BreakEvent`. Claim and logging mods see the events they expect, describing the position and face actually involved.

Blocks the mod removes on its own — an emptied block a settle or a collapse leaves behind — are reported as a break by the level's fake player. **A refused removal leaves the block standing**, and the run carries on around it: the mod will not delete where it may not build.

The server validates every packet independently of the client: the sender, one gesture per player per tick, that the sender is not a spectator, that the target is loaded and within reach, and every operation-specific rule. Deposit targeting and support are recomputed from the player's current view rather than trusted from the packet.
