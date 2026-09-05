# Some Stacks — player-facing behavior

As-built description of the mod's observable behavior. Where this disagrees with the code, the code
wins. An orientation, not a full spec and not a history.

Some Stacks turns held items directly into visible world storage. It adds three stack blocks and
nothing else: no block items, recipes, creative-tab entries, or storage screens. A stack exists
because an item was deposited into the world and normally disappears when its last contents are
removed.

Requires Minecraft 1.21.1 and Fabric Loader 0.19.3+ with Fabric API 0.116.15+1.21.1. Forge and
NeoForge builds are planned but not yet available. Must be installed on both client and server.

## The three stack types

| Type | Capacity per block | Contents | Character |
| --- | --- | --- | --- |
| **Storage Stack** | 27 item stacks | Any allowed item | Bulk storage that merges, sorts, packs down, and grows or shrinks |
| **Singles Stack** | 64 items | Allowed non-ingots | A 4 x 4 x 4 display grid, one item per cell |
| **Bar Stack** | 64 items | Server-defined ingots | Eight alternating layers of bars |

A vertical run of one stack type is a single pile or column with one shared inventory. Default
maximum height is 8 blocks (216 Storage slots, 512 Singles or Bar positions).

## Controls

The Stack Modifier is held `V` by default, rebindable under **Options -> Controls -> Some Stacks**.

Hold `V` and right-click air to cycle placement mode: Storage, Singles, Bar, Toggle Permanent. The
selected mode shows above the hotbar; server-disabled types are skipped. The mode sets what a new
block becomes; it never changes an existing stack.

| Gesture | Result |
| --- | --- |
| Hold `V`, right-click air | Cycle placement mode |
| Hold `V` + item, right-click a stack | Deposit into that stack, whatever the selected mode |
| Hold `V` + item, right-click another block | Deposit into an adjacent Singles/Bar Stack if applicable, otherwise place the selected type against that face and make the first deposit |
| Right-click a stack, no `V`, no Shift | Extract the nearest stored item on the view ray |
| Toggle Permanent mode, hold `V`, empty hand, right-click a Storage Stack | Toggle whether that pile keeps empty blocks |
| Shift-right-click a Storage/Singles Stack with a redstone torch | Rotate that block's layout 90 degrees |
| Shift-right-click an occupied Singles cell with a soul torch | Rotate that item 90 degrees |

Only the main hand acts. Torch gestures do not spend the torch. Rotation reports the new angle
above the hotbar; its sound is limited to once per player every 4 ticks. Sneaking keeps ordinary
interaction available except where a torch gesture claims the click.

## Depositing and placing

A placement and its first deposit are atomic: the block is not placed unless the item is valid, the
target cell is supported, the final shape is unobstructed and replaceable, the run stays within its
height limit, and protection allows the edit. Failed placement leaves nothing behind.

Placement into water waterlogs the stack and keeps the water. A top-face deposit onto an occupied
or unsupported top cell adds a new block above the run, of the currently selected type; different
types can therefore sit beside or atop one another, and only contiguous same-type blocks share a
run.

In creative mode, deposits do not reduce the held stack.

Deposits are rejected when the server has disabled the item's mod or exact item id; existing
contents stay extractable. Items the selected type does not accept simply do nothing: Bar takes
only configured ingots, Singles takes everything allowed that Bar does not.

## Extracting

Plain right-click targets the nearest rendered item on the view ray inside the clicked block.

- Storage: as much of the targeted stack as the hand can hold.
- Singles: one item.
- Bar: one bar, and any bars left unsupported above it collapse and drop.

The main hand must be empty or hold the same item (matching damage and components) with room. A
plain click on a stack is consumed even when extraction fails, so an unrelated held item is not
used against the stack.

Breaking or replacing any stack block drops that block's local contents; no stack block item drops.
Storage runs above and below the gap settle independently. Removing a Bar Stack also removes the
support seam beneath the Bars above it, so those collapse.

## Per-type behavior

### Storage

27 stacks shown as a 3 x 3 x 3 grid; the art does not change with count. A contiguous pile is one
inventory: deposits merge into compatible partial stacks first, fill from the base up, and grow
upward when full and allowed. Each scheduled settlement merges exact identities, restores stack
sizes, sorts, packs down, and removes empty top blocks.

Storage is the only type with a permanence mode. A permanent pile keeps empty blocks; a temporary
pile trims empty blocks from the top and vanishes when emptied. The setting belongs to the whole
pile. A redstone torch rotates one block's 3 x 3 x 3 layout, not the pile.

### Singles

64 items, one per cell in a 4 x 4 x 4 grid. An item currently valid for Bar is invalid here. Every
item needs support: the lowest block's bottom layer rests on the world, every other cell rests on
the one directly below, and support follows the same visible column across a block seam even when
the two blocks have different rotations.

A deposit goes only into the cell the view selects; it does not search the column. Removing an item
drops every occupied cell above it in that visible column down one layer, across seams, keeping each
item's own rotation; it does not compact other columns or close preexisting gaps. Empty top blocks
remove themselves.

A redstone torch rotates a whole block; a soul torch rotates only the aimed item. Per-item rotation
stays with the item when gravity moves it.

### Bar

64 server-approved ingots rendered as fixed bar cuboids in eight layers of eight, each layer across
the one below. The item model is not drawn; a resource pack supplies the bar texture and tint.

Every bar above the bottom layer must overlap a bar directly below, and support crosses a seam
between adjacent Bar blocks. Removing a bar, or breaking a Bar block, drops every bar that loses
support, possibly cascading through layers and blocks. Bar Stacks have no rotation gesture.

## Shared block behavior

- **Water:** all three are waterloggable.
- **Light:** each cell holding a glowing `BlockItem` contributes a quarter of that block's light
  level by integer division; contributions add, capped at 15. Item count in a Storage slot does not
  matter.
- **Pistons:** all three block piston movement.
- **Collision:** Storage is a full block. Singles and Bar collide only on occupied cells or bars;
  their empty space does not suffocate or fog the camera, but mobs treat the whole block as
  unpathable.
- **Empty dynamic blocks:** an empty Singles or Bar block has no outline but stays clickable and
  breakable.
- **Sounds:** deposits, extraction, and rotations play server-selected sounds; data packs can
  replace them per type and action.

## Automation

Every block exposes loader-native item storage on all six sides (`IItemHandler` on Forge, Transfer
API `Storage<ItemVariant>` on Fabric) covering the entire contiguous run, whichever block a machine
connects to. Slots number from the bottom block up.

| Type | One slot | Slot limit |
| --- | --- | --- |
| Storage | one inventory slot | 64 advertised; the item's own max still applies |
| Singles | one display cell | 1 |
| Bar | one bar position | 1 |

The advertised storage includes every current position plus one block of headroom while below the
max height; inserting into headroom can grow the run. Growth is refused when the type is disabled,
the space cannot be replaced, an entity blocks the final shape, a height limit is reached, or the
loader's edit authority refuses the automation actor.

Storage insertion is positional at the moment of the call, then the next settlement packs and
sorts. Singles and Bar accept only the exact empty supported position named. For extraction,
Storage removes from the named slot then settles, Singles uses the same one-column gravity as player
extraction, and Bar moves the column's topmost bar into the hole to avoid the player-extraction
collapse.

Reentrant automation during another structural mutation is refused with the ordinary loader failure
and can be retried next tick. On Fabric, mutations stage until the transaction commits, and Singles
and Bar allow one structural extraction position per transaction.

## Comparators

A comparator reads the fill of the entire run, not just one block:

```text
0 when empty, otherwise floor(fill * 14) + 1
```

Storage counts each slot as a fraction of its legal stack size; Singles and Bar count occupied
positions. 0 means empty; a full run gives 15.

## Server configuration

Per-world JSON policy at `<world>/serverconfig/somestacks-server.json`.

| Setting | Default | Effect |
| --- | --- | --- |
| `piles.max_pile_height` | `8` | Maximum height of every same-type run, 1-64 |
| `stacks.enable_*_stack_block` | `true` | When false, blocks new placement and growth of that type |
| `compatibility.disable_mods` | empty | Refuses new items from listed namespaces in gestures and automation |
| `compatibility.disable_items` | empty | Refuses exact items in player deposits only |
| `compatibility.ingot_tags` | `forge:ingots*` / `c:ingots*`, plus `somestacks:ingots` | What Bar accepts and Singles refuses; entries may contain `*` |
| `render_gallery.enabled` | `false` | Enables `/ss gallery` and `/ss ingotgallery` |
| `render_gallery.required_permission_level` | `3` | Permission level those two commands need |
| `render_gallery.placements_per_tick` | `64` | Throttles gallery construction |
| `render_gallery.gen_mods` / `gen_items` | empty | Namespace and item lists used by the `list` and `items` gallery forms |

Disabling a type, item, mod, or ingot category never removes or ejects existing contents. The
client receives the enable flags on login and skips disabled types when cycling modes. Data-pack
tag changes are picked up on reload.

## Commands

`/ss` is mainly an admin and render-authoring tool.

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ss help [command]` | anyone | List or explain commands |
| `/ss item ...` / `reset` | level 2 | Change how one item appears on that client |
| `/ss write changed\|<modid>\|all\|list` | level 2 | Save changed item profiles, or generate complete profile files |
| `/ss gallery ...` / `/ss ingotgallery ...` | `render_gallery.required_permission_level`; off by default | Build Storage or Bar render galleries |
| `/ss gen ...` / `deny ...` / `ingot ...` | level 2 | Edit gallery lists, disabled lists, or ingot tag patterns |
| `/ss reload` | level 2 | Reload server item-render overrides and resync players |

Gallery commands build east of the player over a replaced sandstone floor, skip the protection
checks ordinary placement uses (hence off by default), and spread large jobs across ticks.
`/ss reload` does not reread the world-policy JSON; direct edits to that file take effect on
restart.

## Item appearance

Storage and Singles can show an item in four modes: `2d` (flat art on a small cube), `3d` (the
item's FIXED renderer), `gui` (its inventory renderer), and `block` (a BlockItem's block state,
falling back to `3d`). Each profile can also set scale and a three-component offset.

Resolution order: server override, the player's local override file, resource-pack data, then
automatic measurement from the baked model. A server override therefore wins over local
preferences. Measured results cache at `config/somestacks/measured_cache.json` and are invalidated
by resource-pack, mod-version, or resource-reload changes.

`/ss item` changes the client's in-memory layer immediately; `/ss write changed` saves those
changes to `config/somestacks/item_overrides.json`. The namespace forms write complete files to
`config/somestacks/generated_overrides/`, which is output only and not loaded.

Bar appearance is separate and client-side: resource packs map item ids to bar textures and tints
under `assets/<namespace>/textures/bars/*.json`, with missing tints derived from the item's sprite
and color. Bar mappings are not synchronized, so players with different packs may see different
bars.

## Extension points

- Data pack: add items to the tags named by `ingot_tags`, including `somestacks:ingots`; replace
  action sounds via `data/<namespace>/somestacks_sounds/*.json`.
- Resource pack: add Storage/Singles profiles via
  `assets/<namespace>/item_render_overrides/*.json`; add Bar textures and tints via
  `assets/<namespace>/textures/bars/*.json`.
- Server: impose Storage/Singles profiles via `config/somestacks/server_item_overrides/*.json`,
  synced on login and `/ss reload`.

## Protection and validation

Player placement, deposit, extraction, and rotation answer to build limits, obstruction, the world
border, and spawn protection, and fire a real protection event that claim and logging mods can hook
(Forge's interaction/place/break events; Fabric API's `UseBlockCallback` and
`PlayerBlockBreakEvents`). Automatic growth and removal use a loader automation actor. Fabric has
no placement event, so on Fabric automated growth also consults FTB Chunks directly when it is
installed.

The server independently validates every gesture packet: one per player per tick, nonspectator,
loaded target, reach, held item, target block and index, adjacency, support, type enablement,
height, and protection. Deposit cell selection is recomputed from the player's current view;
extraction and Singles item rotation trust a range- and occupancy-checked client cell index.

## Boundaries

- No inventory screen, portable stack item, recipe, creative-tab entry, or block-item drop.
- Only Storage piles can be permanent; empty Singles and Bar blocks are cleaned up.
- Exact-item denial applies to player gestures only; namespace denial applies to automation too.
- Server render overrides govern Storage and Singles profiles, not client-only Bar mappings.
- Existing contents stay legal to extract and move internally after a config or tag change that
  would reject a new deposit.
