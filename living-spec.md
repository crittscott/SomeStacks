# Some Stacks Living Specification

This document summarizes the mod's architecture, user-visible rules, persistent state, configuration, and data formats. The code is authoritative for implementation details. **This document is updated after changes to the code and is always somewhat inconsistent with the code; the code wins, always.**

This document is not, and should not become, a prose version of the code, nor does it contain history, what was changed and why, nor potential future changes or plans.

## Technical baseline

- Mod id: `somestacks`
- Minecraft 1.20.1, Forge 47.4.0, Java 17, Parchment mappings
- Required on both client and server; network protocol `2`
- Three registered blocks and block entity types: Storage Stack, Singles Stack, and Bar Stack
- No block items, recipes, menus, or conventional inventory screens

## Architecture

Some Stacks turns held items into visible world storage. Players interact with rendered cells rather than an inventory screen.

The normal path is:

`client interaction -> packet -> server validation and mutation -> block entity sync -> client renderer`

The server owns inventory, validation, persistence, growth, gravity, and world mutation. The client owns gesture recognition, cell targeting, and rendering.

Block entities do not tick continuously. Inventory edits schedule a deferred pass on the bottom block of the affected vertical run. That pass settles Storage piles where necessary and publishes content, lighting, and comparator changes for all three types.

## Stack types

| Type | Capacity per block | Layout | Main rules |
| --- | --- | --- | --- |
| Storage Stack | 27 ordinary item stacks | Rotatable 3 x 3 x 3 grid with gaps | A vertical run forms one pile. Deposits fill and grow it from the bottom. Settling consolidates, sorts, packs, and trims the pile. |
| Singles Stack | 64 items, one per cell | Rotatable 4 x 4 x 4 grid | Accepts non-bar items. Every item above the bottom layer needs support directly below. Extraction shifts that visual column downward through the entire vertical run. |
| Bar Stack | 64 items, one per position | Eight alternating layers of eight bars | Accepts configured ingot-tag items. Every bar above the bottom layer must overlap a bar below. Player extraction collapses unsupported bars; automation backfills from the top. |

Storage accepts nonempty items outside the disabled-mod list. Singles applies the same rule but excludes items valid for Bar Stack. Player deposits also apply the disabled-item list.

Bar validity comes from the item tags named by the `ingot_tags` server setting. Entries support `*` wildcards. The defaults, `forge:ingots*` and `somestacks:ingots`, cover Forge ingot tags and the mod's data-pack-extensible tag. The resolved item set is refreshed on configuration and tag reloads. Expanding Bar validity narrows Singles validity by the same set.

## Vertical runs, automation, and redstone

Every block exposes a Forge item-handler capability on every side. A block in a vertical run exposes the entire run, so automation sees the same inventory from any height.

Capability operations are positional:

- A Storage slot holds an ordinary item stack.
- A Singles slot names one cell and holds one item.
- A Bar slot names one bar position and holds one item.

Singles and Bar insertion refuses empty positions without support rather than redirecting the item. A caller walking slots from the bottom fills supported positions in order. Simulations use the structure's present occupancy.

A run advertises its existing positions plus one block of reachable headroom, bounded by `max_pile_height`. Insertion into headroom grows the run if the type is enabled and placement passes build-height, replaceability, entity-obstruction, world-border, and spawn-protection checks. Capability growth uses the level's fake player.

Comparators report the fill of the entire vertical run from any block in it:

`0` when empty; otherwise `floor(fill * 14) + 1`

Storage fill uses the fraction occupied in each ordinary stack slot. Singles and Bar use occupied positions. Comparator notifications are published only when the run's value changes.

## Player interaction

`V` is the default rebindable stack-modifier key. It is a held modifier.

| Gesture | Result |
| --- | --- |
| Hold `V` and right-click air | Cycle Storage, Singles, Bar, and Toggle Permanent modes. Disabled stack types are skipped. |
| Hold `V` with an item and right-click a stack | Deposit into that stack, regardless of the selected placement mode. If a targeted Singles or Bar column is full, a top-face click places the selected stack type above it and deposits there. |
| Hold `V` with an item and right-click another block | Deposit into an adjacent Singles or Bar Stack, or place the selected stack type in the adjacent replaceable position and make the first deposit. |
| Right-click a stack without `V` or Shift | Extract the nearest occupied rendered cell. Storage extracts as much as the hand accepts; Singles and Bar extract one item. |
| Select Toggle Permanent, hold `V`, and right-click a Storage Stack with an empty hand | Toggle automatic removal for the whole pile. |
| Shift-right-click Storage or Singles with a redstone torch | Rotate the block layout 90 degrees. |
| Shift-right-click a Singles item with a soul torch | Rotate that rendered item 90 degrees. |

Placement and deposit do not accept Shift plus `V`.

A plain right-click on a stack is consumed by the block, so held items are not used against it. Sneaking bypasses that handler; the two torch gestures are intercepted separately. Bar Stacks have no torch rotation gesture.

Extraction selects the nearest occupied cell along the player's reach ray. Singles and Bar deposits target the last empty cell before the first occupied cell, or the farthest intersected empty cell. The server recomputes deposit targeting and support from the player's current view.

The hand must be empty or contain the same item and tags with room for extraction.

## Storage behavior

### Storage piles

A Storage pile is a maximal contiguous vertical run, limited to `max_pile_height` for placement and growth. A pile already above the configured limit remains usable but cannot grow.

Player deposits fill compatible partial stacks and then empty slots from the base upward, growing the pile when necessary. Capability insertion addresses the named slot instead.

Any inventory edit schedules a settle on the pile's base. Settling:

- consolidates stacks by exact item identity, damage, and tags;
- packs and sorts them from the base upward;
- propagates the pile's permanent flag;
- removes empty, non-permanent blocks from the top.

A permanent pile does not shrink automatically. Rotation remains per block, even though settling can move contents between blocks.

### Singles columns

Singles support follows visual columns across block boundaries, including between differently rotated blocks. The bottom layer is grounded when no Singles Stack exists below.

Removing an item shifts occupied cells above it down one layer in the same visual column, preserves per-item rotation, and draws one item across each block boundary as needed. Empty top blocks remove themselves; an empty block supporting another Singles Stack remains until gravity clears it.

Player and automated extraction use the same shift. Automated insertion places one item only in the named empty, supported cell.

### Bar columns

Bar support is based on footprint overlap with the layer below and continues across block boundaries. The bottom layer is grounded when no Bar Stack exists below.

Player extraction drops every bar that becomes unsupported, continuing upward while support changes. Breaking or replacing a Bar Stack also collapses the column above it.

Automated extraction removes the requested bar and moves the column's topmost bar into the hole. This preserves support and produces no dropped items. Automated insertion places one bar only in the named empty, supported position.

## State, shape, light, and removal

Block entity NBT and update packets contain the visible inventory.

- Storage also persists block rotation and the permanent flag.
- Singles also persists block rotation and 64 per-item rotations.
- Bar has no additional persistent presentation state.

All three types are waterloggable. A stack placed into water keeps it, whether the placement came from a player gesture or from capability-driven growth.

Storage has a full-block shape. Singles and Bar derive their outline and collision shapes from occupied cells while retaining a full-block interaction shape. An empty Singles or Bar block therefore shows no highlight box but can still be clicked and broken. Neither type is passable to mob pathfinding, and neither suffocates a player standing in its empty space.

Occupied slots containing `BlockItem`s emit light. Each contributes one quarter of that block's default light emission; the sum is capped at 15. The item count inside a Storage slot does not multiply its contribution.

Breaking or replacing any stack block drops the contents of that block. Mod-driven removal follows the pile and column rules above, and answers to protection as growth does: it consults the level's fake player and reports a break event. A refused removal leaves the emptied block standing, and the run carries on around it. Bars still collapse onto a seam whose block was kept, because moving them is an inventory edit rather than a world edit.

## Networking and protection

Client-to-server packets cover placement and deposit, deposit, extraction, block rotation, item rotation, and permanent-mode changes. Server-to-client packets cover synchronized configuration and render-override commands. Packets received from the wrong logical side are rejected.

Every gesture is a main-hand gesture. No packet carries a hand.

The server validates the sender, one gesture per player per tick, that the sender is not a spectator, the loaded position, reach, target block entity, slot, held item, and operation-specific rules. Reach is checked against the position the packet would change; a neighbouring position a packet also names is consulted for protection but never written. Mutations also respect the world border, vanilla spawn protection, and Forge's right-click-block event. A deposit reached through a click on a neighbouring block consults both that block and the stack. Placement additionally checks replaceability, entity obstruction against the collision shape created by the first deposit, type enablement, pile height, item restrictions, whether the first deposit would succeed, and Forge's block-place event.

A gesture claims the click it displaces, so the vanilla interaction that follows in the same tick cannot act on the same position. The consults above fire the very event that claim suppresses, so they always run first. A packet claims nothing until the server has confirmed the gesture it describes.

Values arriving over the network are held to the same bounds their file format states. Enum indexes, collection counts, render scale and offset, and the namespaces a write request names are all range-checked on receipt, and a packet failing any of them is dropped whole.

A deposit by a player in creative mode fills the stack without spending the held stack.

The server synchronizes stack-type enable flags and server render overrides on login and server-config reload. Blacklists and pile settings remain server-side.

## Rendering

All three stack blocks use block entity renderers.

Storage and Singles render stored items inside their cells. Bar renders a fixed cuboid using item-specific texture data rather than the original item model.

### Item render profiles

An item render profile contains a mode, scale, and offset.

| Mode | Presentation |
| --- | --- |
| `2d` | Projects flat item art onto visible faces of a small cell background. |
| `3d` | Uses the item renderer in `FIXED` context. |
| `gui` | Uses the item renderer in `GUI` context. |
| `block` | Renders a `BlockItem`'s default block state, with `3d` fallback for block-entity-rendered or failing block models. |

Profiles resolve by precedence:

1. Server overrides from `config/somestacks/server_item_overrides/*.json`
2. User overrides from `config/somestacks/item_overrides.json`
3. Resource overrides from `assets/*/item_render_overrides/*.json`
4. Automatic measurement

Resource-override files are named for the namespace of the items they configure.

An override entry uses this format:

```json
{
  "modid:item": {
    "mode": "2d",
    "scale": 0.8,
    "offset": [0.0, 0.1, 0.0]
  }
}
```

Fields are optional. Scale ranges from `0.01` to `20`; each offset component ranges from `-1` to `1`. An entry owns the whole presentation: omitted scale and offset use `1` and zero, while an omitted mode is measured.

Measurement supplies profiles for items absent from the override layers. Results are cached in `config/somestacks/measured_cache.json` and invalidated by relevant mod-version changes, resource-pack changes, or a manual resource reload.

### Bar texture data

Bar appearance is client-side resource data loaded from `assets/*/textures/bars/*.json`. Mappings assign an item a bar texture and optional tint; resource packs can extend or replace them. Unmapped items use the base ingot texture with an automatically derived tint when available. Because this data is not synchronized, players may see different bar appearances.

### Sound data

`data/*/somestacks_sounds/*.json` maps deposit and extraction sounds for each stack type. It is server-side data-pack state. Later resources override earlier mappings; missing or invalid entries use vanilla fallback sounds.

## Server configuration

| Setting | Default | Effect |
| --- | --- | --- |
| Maximum pile height | `8` | Limits placement and growth of all vertical runs. |
| Enable Storage / Singles / Bar | `true` | Prevents new placement and growth of a disabled type. Existing blocks remain usable. |
| Disabled mods | empty | Rejects new contents from those namespaces. |
| Disabled items | empty | Rejects those item ids on player deposit paths. |
| Ingot tags | `forge:ingots*`, `somestacks:ingots` | Defines Bar-valid items and, by complement, items excluded from Singles. |
| Render gallery placements per tick | `64` | Throttles `ss gallery` and `ss ingotgallery` world edits. |
| Gen mods | empty | Ordered namespaces used by render-gallery `list` commands. |
| Gen items | empty | Items used by `ss gallery items`. |

Operators edit the five text lists through `ss gen`, `ss deny`, and `ss ingot`. Command edits update both the running values and the config file. Direct file edits apply through Forge's config reload.

## The `ss` command

`ss` is an administrative command throughout: render authoring, world-building render galleries, server list editing, and unrestricted help.

Gates are vanilla permission levels. Level 2 is the gamerule and world-editing level; level 3 is the server-administration level, which an operator holds under the default `op-permission-level`.

| Command | Gate | Purpose |
| --- | --- | --- |
| `ss item <item> <mode> [...]` | Level 2 player | Set an in-memory user render override. |
| `ss item <item> reset` | Level 2 player | Remove a user render override. |
| `ss write changed` | Level 2 player | Write user overrides to `config/somestacks/item_overrides.json`. |
| `ss write <modid\|all\|list>` | Level 2 player | Write resolved namespace profiles under `config/somestacks/generated_overrides/`. |
| `ss gallery <modid\|all\|list\|items>` | Level 3 player | Build Storage Stack render galleries. |
| `ss ingotgallery <modid\|all\|list>` | Level 3 player | Build Bar Stack render galleries. |
| `ss gen mod|item add\|remove\|list` | Level 2 | Edit render-gallery namespace and item lists. |
| `ss deny mod|item add\|remove\|list` | Level 2 | Edit disabled namespace and item lists. |
| `ss ingot add\|remove\|list` | Level 2 | Edit ingot-tag patterns. |
| `ss reload` | Level 2 | Reload server render overrides and resend synchronized configuration. |
| `ss help [command]` | None | Show command forms, gates, and summaries. |

A render gallery is a single-layer field of filled stacks on a uniform floor: one column of stacks per namespace or item group, with that group's rows running north. Gallery commands overwrite their floor and stack positions directly and do not apply ordinary placement protections. Large galleries are spread across ticks by the configured placement limit.

Generated override files are output only; they are not an active override layer. They use the same format as resource and server overrides.

`ss reload` does not re-read the Forge server config file.

## Automated verification

The suite contains 51 JUnit tests and 82 Forge GameTests.

JUnit covers registry-independent logic under `src/test/java` and runs with `build`. Forge GameTests cover registered game objects, levels, block entities, capabilities, events, packets, persistence, growth, gravity, protection, and synchronization. `runGameTestServer` runs the GameTests.

`build` does not run GameTests, and `runGameTestServer` does not run JUnit.
