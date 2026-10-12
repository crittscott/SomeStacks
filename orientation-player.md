<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — player-facing behavior

Structure: `orientation-code.md`.

Some Stacks turns held items into visible storage using three blocks without block items, recipes, creative-tab entries, or screens. Depositing creates a stack; emptying it normally removes it.

Requires Minecraft 1.20.1 and matching client/server loader builds: Forge 47.4.10 or later within 47.x, or Fabric Loader 0.19.3 with Fabric API 0.92.7+1.20.1.

## The three stack types

| Type | Capacity per block | Contents | Character |
| --- | --- | --- | --- |
| **Storage Stack** | 27 item stacks | Any allowed item | Bulk storage that merges, sorts, packs down, and grows or shrinks |
| **Singles Stack** | 64 items | Allowed non-ingots | A 4 x 4 x 4 display grid, one supported item per cell |
| **Bar Stack** | 64 items | Server-defined ingots | Eight alternating layers of supported bars |

A vertical run of one stack type is one pile or column. Default maximum height is eight blocks (216 Storage slots, 512 Singles or Bar positions).

## Controls

Hold `V` for the Stack Modifier; rebind under **Options -> Controls -> Some Stacks -> Stack Modifier (Hold)**.

Hold `V` and right-click air to cycle placement mode: Storage, Singles, Bar, Toggle Permanent. The selected mode shows above the hotbar; server-disabled types are skipped, and Toggle Permanent is always included. Mode selects new blocks; existing stacks keep their type.

| Gesture | Result |
| --- | --- |
| Hold `V`, right-click air | Cycle placement mode |
| Hold `V` + item, right-click a stack | Deposit into that stack, whatever the selected mode |
| Hold `V` + item, right-click another block | Deposit into an adjacent Singles/Bar Stack when applicable; otherwise place the selected type against that face and make the first deposit |
| Right-click a stack, no `V`, no Shift | Extract the nearest stored item on the view ray |
| Toggle Permanent mode, hold `V`, empty hand, right-click a Storage Stack | Toggle whether that pile keeps empty blocks |
| Shift-right-click a Storage/Singles Stack with a redstone torch | Rotate that block's layout 90 degrees |
| Shift-right-click an occupied Singles cell with a soul torch | Rotate that item 90 degrees |

Only the main hand acts; torches are not spent. Rotation shows the new angle above the hotbar and may happen every tick; its sound is limited to once every four ticks per player. Sneaking retains ordinary item/block interaction except for the torch gestures.

## Depositing and placing

Placement and first deposit are atomic: they require a valid item, supported target cell, unobstructed shape, replaceable space, permitted height, and edit permission. Failure leaves no empty stack.

Water placement waterlogs the stack and preserves water. A top-face deposit onto an occupied or unsupported cell can add the selected type above the run. Different types can sit beside or atop each other; only contiguous same-type blocks share a run.

Creative deposits leave the held stack unchanged.

Deposits are rejected when the server has disabled the item's mod or exact item id; existing contents stay extractable. Items the selected type does not accept simply do nothing: Bar takes only configured ingots, Singles takes everything allowed that Bar does not.

## Extracting

Plain right-click targets the nearest rendered item on the view ray inside the clicked block.

- Storage: as much of the targeted stack as the hand can hold.
- Singles: one item.
- Bar: one bar, possibly collapsing unsupported bars above it.

The main hand must be empty or hold the exact same item, damage, and NBT with room. Plain stack clicks are consumed even if extraction fails, preventing unrelated held-item use.

Breaking or replacing any stack block drops that block's local contents; no stack block item drops. Storage runs above and below the gap settle independently in place. Singles leaves separated runs standing. Removing a Bar Stack removes the support seam beneath the Bars above it, so unsupported bars collapse and drop.

## Per-type behavior

### Storage

27 ordinary stacks shown as a 3 x 3 x 3 grid; a slot holding one item and a full slot show the same item model. A contiguous pile is one inventory: deposits merge compatible partial stacks first, fill from the base up, and grow upward when full and allowed. Settlement merges exact item/damage/NBT identities, sorts, packs down, and removes empty top blocks. Extraction removes from the aimed cell first; the following scheduled block tick settles the pile.

Storage is the only type with permanence. A permanent pile retains empty blocks; a temporary pile trims empty top blocks and disappears when emptied. The setting belongs to the whole pile and shows above the hotbar when toggled. A redstone torch rotates one block's visible layout, not the pile.

### Singles

64 items, one per cell in a 4 x 4 x 4 grid. An item currently valid for Bar is invalid here. Every item needs support: the lowest block's bottom layer rests on the world, every other cell rests on the cell directly below, and support follows the same visible column across block seams even when the blocks have different rotations.

A deposit goes only into the cell selected by the view; it does not search the column when that cell is occupied or unsupported. Removing an item shifts every occupied cell above it in that visible column down one layer, across seams, preserving each item's own rotation. It does not compact other columns or erase preexisting gaps. Empty top blocks remove themselves.

A redstone torch rotates a whole block; a soul torch rotates only the aimed item. Both are quarter-turns. Per-item rotation stays with the item when gravity moves it.

### Bar

64 server-approved ingots rendered as fixed bar cuboids in eight layers of eight, each layer across the one below. The item model is not drawn; a resource pack supplies or derives the bar texture and tint.

The bottom layer rests on the world. Every bar above it must overlap a bar directly below, and support crosses seams between adjacent Bar blocks. Removing a bar, or breaking a Bar block, drops every bar that loses support, possibly cascading through layers and blocks. Supported bars stay in place. Bars have no rotation gesture.

## Shared block behavior

- **Water:** all three are waterloggable.
- **Light:** each occupied cell holding a glowing `BlockItem` contributes a quarter of that block's default light level by integer division; contributions add, capped at 15. Item count within a Storage slot does not multiply its contribution.
- **Pistons:** all three block piston movement.
- **Collision:** Storage is a full block. Singles and Bar collide on occupied cells or bars; their empty space does not suffocate or fog the camera, but mobs treat the whole block as unavailable for pathfinding.
- **Empty dynamic blocks:** empty Singles and Bar blocks have no outline shape, but their full-block interaction shape makes them clickable and breakable.
- **Sounds:** deposits, extraction, and rotations play server-selected sounds; data packs can replace them per type and action.

## Automation

Every block exposes the whole contiguous run on all six sides: `IItemHandler` on Forge, Transfer API `Storage<ItemVariant>` on Fabric. Slots number from the bottom block up.

| Type | One slot | Slot limit |
| --- | --- | --- |
| Storage | One inventory slot | 64 advertised; the item's own maximum still applies |
| Singles | One display cell | 1 |
| Bar | One bar position | 1 |

Advertised storage includes every current position plus one block of headroom while below maximum height. Inserting into headroom can grow the run. Growth is refused when the type is disabled, the space cannot be replaced, an entity obstructs the final shape, build height or run height is reached, or the loader's edit authority refuses the automation actor.

Storage insertion is positional at the moment of the call, then the next settlement packs and sorts. Singles and Bar accept only the exact empty supported position named; machines walking positions bottom to top build layer by layer.

Storage extraction removes from the named slot then settles; Singles uses the same one-column gravity as player extraction; Bar moves the whole column's topmost bar into the hole, avoiding the item-dropping collapse caused by player extraction.

Reentrant automation during another structural mutation is refused with the ordinary loader failure and can be retried later. Fabric stages mutations until the outer Transfer API transaction commits. All sides and blocks in a run share staged contents and capacity. Singles and Bar permit one structural extraction position per run per transaction; bulk callers retry for subsequent items.

## Comparators

A comparator reads the fill of the entire run, not just one block:

```text
0 when empty, otherwise floor(fill * 14) + 1
```

Storage counts each slot as a fraction of its legal stack size; Singles and Bar count occupied positions. 0 means empty; a completely full current run gives 15.

## Server configuration

Both loaders store per-world JSON at `<world>/serverconfig/somestacks-server.json`.

| Setting | Default | Effect |
| --- | --- | --- |
| `piles.max_pile_height` | `8` | Maximum height of every same-type run, 1-64 |
| `stacks.enable_storage_stack_block` | `true` | Prevents new Storage placement and growth when false |
| `stacks.enable_singles_stack_block` | `true` | Prevents new Singles placement and growth when false |
| `stacks.enable_bar_stack_block` | `true` | Prevents new Bar placement and growth when false |
| `compatibility.disable_mods` | empty | Refuses new items from listed namespaces in gestures and automation |
| `compatibility.disable_items` | empty | Refuses exact items in player deposits only |
| `compatibility.ingot_tags` | Forge: `forge:ingots*`; Fabric: `c:ingots*`; both: `somestacks:ingots` | Defines what Bar accepts and Singles refuses |
| `render_gallery.enabled` | `false` | Enables `/ss gallery` and `/ss ingotgallery` |
| `render_gallery.required_permission_level` | `3` | Permission level those commands require, 0-4 |
| `render_gallery.placements_per_tick` | `64` | Throttles gallery construction |
| `render_gallery.gen_mods` | empty | Namespaces used by `list` gallery/write forms |
| `render_gallery.gen_items` | empty | Items used by `/ss gallery items` |

Disabling types prevents placement/growth; existing blocks and extraction remain. Item/mod/tag changes never eject contents. Enable flags sync on login; cycling skips disabled types.

Ingot-tag entries may contain `*`: `forge:ingots*` matches `forge:ingots` and child tags such as `forge:ingots/copper`; Fabric defaults to `c:ingots*`. Data-pack tag changes are picked up on reload.

## Commands

`/ss` provides admin and render tools.

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ss help [command]` | Anyone | List or explain commands |
| `/ss item <item> <mode> ...` / `reset` | Level 2, player | Change how one item appears on that client |
| `/ss write changed` | Level 2, player | Save that client's changed profiles |
| `/ss write <modid\|all\|list>` | Level 2, player | Generate complete profile files on that client |
| `/ss gallery <modid\|all\|list\|items>` | Configured gallery permission, player; off by default | Build Storage render galleries |
| `/ss ingotgallery <modid\|all\|list>` | Configured gallery permission, player; off by default | Build Bar render galleries |
| `/ss gen ...` | Level 2 | Edit gallery namespace/item lists |
| `/ss deny ...` | Level 2 | Edit disabled namespace/exact-item lists |
| `/ss ingot ...` | Level 2 | Edit ingot-tag patterns |
| `/ss reload` | Level 2 | Reload server render overrides and resync players |

Galleries build east of the player over sandstone, with rows running north, spread across server ticks. They directly replace floor and stack positions without ordinary placement protection, so they are disabled by default and default to permission level 3 once enabled.

`/ss reload` does not reread world-policy JSON. Command edits save immediately; direct file edits take effect after server restart.

## Item appearance

Storage and Singles have four modes: `2d` (flat item art projected onto visible faces of a small background cube), `3d` (the item's FIXED renderer), `gui` (the inventory renderer adjusted to the cell), and `block` (a BlockItem's state, falling back to `3d` when it cannot be drawn safely). Profiles also set scale and a three-component offset.

Resolution order is server override, local override, resource-pack data, then automatic model measurement. Server overrides win over local preferences. Unconfigured items are measured and fitted to a cell; results cache at `config/somestacks/measured_cache.json`. Pack selection changes between sessions, owning-mod version changes, and resource reload invalidate relevant measurements.

`/ss item` edits the local in-memory layer. `/ss write changed` saves explicit edits to `config/somestacks/item_overrides.json`. Namespace forms write complete files to output-only `config/somestacks/generated_overrides/`, never loaded automatically.

Bar appearance is separate: resource packs map item ids to textures and optional tints under `assets/<namespace>/textures/bars/*.json`. Missing tints derive from the item's sprite and registered color. Bar mappings are client-only and not synchronized, so players with different packs can see different bars.

## Extension points

- Data pack: add items to tags selected by `ingot_tags`, including the shipped `somestacks:ingots` tag; replace action sounds through `data/<namespace>/somestacks_sounds/*.json`.
- Resource pack: add Storage/Singles profiles through `assets/<namespace>/item_render_overrides/*.json`; add Bar textures/tints through `assets/<namespace>/textures/bars/*.json`.
- Server: impose Storage/Singles profiles through `config/somestacks/server_item_overrides/*.json`, synchronized on login or `/ss reload`.

## Protection and validation

Player placement, deposit, extraction, and rotation honor build limits, obstruction, border, and spawn protection as applicable. Forge uses its interaction/place/break events; Fabric uses `UseBlockCallback` and `PlayerBlockBreakEvents`. Adjacent deposits validate the clicked block and destination. Automatic growth/removal uses a loader-provided automation actor. Fabric has no automation placement event, so growth also consults FTB Chunks and Open Parties and Claims directly when installed; other Fabric claim mods are not consulted for that growth check.

Ordinary block-use clicks use synchronized mode/modifier state. The server validates per-tick pacing, spectator status, loaded targets, reach, main hand, items, adjacency, support, enablement, height, and protection. Deposit, placement, extraction, and Singles item rotation select cells from the server player's current view. Forge restores denied placement state and block-entity data, publishing neighbor updates after acceptance.
