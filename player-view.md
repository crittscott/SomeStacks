# Some Stacks — as-built player-facing description

This describes the mod's current observable behavior. It is not a design specification; where it
disagrees with the code, the code is authoritative.

Some Stacks turns held items directly into visible world storage. It adds three stack blocks, but no
block items, recipes, creative-tab entries, or storage screens. A stack exists because an item was
deposited into the world, and it normally disappears when its last contents are removed.

The mod requires Minecraft 1.20.1 and either Forge 47.x or Fabric Loader 0.19.3 with Fabric API
0.92.11+1.20.1. Architectury API is required on both loaders. Some Stacks must be installed on both
the client and server.

## The three stack types

| Type | Capacity per block | Contents | Character |
| --- | ---: | --- | --- |
| **Storage Stack** | 27 item stacks | Any allowed item | Bulk storage that merges, sorts, packs down, grows, and shrinks |
| **Singles Stack** | 64 items | Allowed non-ingots | A 4 x 4 x 4 display grid with one supported item per cell |
| **Bar Stack** | 64 items | Server-defined ingots | Eight alternating layers of supported bars |

A vertical run of the same stack type is one pile or column. The default maximum height is eight
blocks, so the default capacities are 216 ordinary slots for Storage and 512 individual positions
for Singles or Bar.

## Controls and placement modes

The Stack Modifier is `V` by default. It is a held modifier and can be rebound under
**Options -> Controls -> Some Stacks -> Stack Modifier (Hold)**.

Hold `V` and right-click the air to cycle through:

1. Storage Stack
2. Singles Stack
3. Bar Stack
4. Toggle Permanent

The selected mode is displayed above the hotbar. A stack type disabled by the server is skipped;
Toggle Permanent is always included. The mode controls what a new block will be. It does not change
the type of a stack already in the world.

## Gesture reference

| Gesture | Result |
| --- | --- |
| Hold `V`, right-click air | Cycle placement mode |
| Hold `V` with an item, right-click an existing stack | Deposit into the clicked stack, regardless of selected mode |
| Hold `V` with an item, right-click another block | Deposit into an adjacent Singles/Bar Stack when applicable; otherwise place the selected type against that face and make the first deposit |
| Right-click a stack with no `V` and no Shift | Extract from the nearest visible stored item on the view ray |
| Select Toggle Permanent, then hold `V` and right-click a Storage Stack empty-handed | Toggle whether that whole pile keeps empty blocks |
| Shift-right-click a Storage or Singles Stack with a redstone torch | Rotate that block's layout 90 degrees |
| Shift-right-click an occupied Singles cell with a soul torch | Rotate that item 90 degrees |

Only main-hand gestures act. The modifier gestures do not spend either torch. Rotation reports the
new angle above the hotbar. Rotation itself may happen every tick, but its sound is limited to once
per player every four ticks.

Sneaking keeps ordinary item/block interaction available except where one of the two torch gestures
claims the click.

## Depositing and placing

A placement and its first deposit are atomic. The block is not placed unless the item is valid, the
targeted first cell is supported, the final shape is unobstructed, the space is replaceable, the run
would remain within its height limit, and protection allows the edit. Failed placement therefore
does not leave an empty stack block behind.

Placement into water creates a waterlogged stack and preserves the water. A new block can also be
added on top of an existing run when a top-face deposit targets an occupied or unsupported cell in
the clicked block.
What appears above is the currently selected stack type, so different types can be placed next to or
on top of one another; only contiguous blocks of the same type share a run.

In creative mode, deposits fill stacks without reducing the item stack in the player's hand.

Deposits are rejected when the server has disabled the item's mod or exact item id. Existing stored
items remain extractable. Items rejected by the selected type simply do nothing: Bar accepts only
configured ingots, and Singles accepts everything allowed that Bar does not.

## Extracting

Plain right-click traces the items rendered inside the clicked block and targets the nearest one on
the player's view ray.

- Storage extracts as much of the targeted ordinary stack as the hand can hold.
- Singles extracts one item.
- Bar extracts one bar and may collapse unsupported bars above it.

The main hand must be empty or already contain the exact same item, damage, and NBT data with room in
the stack. A plain click on a stack is consumed even when extraction fails, so an unrelated held
item is not accidentally used against the stack.

Breaking or replacing any stack block drops that block's local contents. No stack block item drops.
The Storage runs left above and below the gap settle independently in place. Singles leaves the
separated runs as they stand. Removing a Bar Stack also removes the support seam beneath the Bar
Stacks above, so unsupported bars there collapse and drop.

## Storage Stacks

A Storage Stack holds 27 ordinary item stacks displayed as a 3 x 3 x 3 grid. The displayed art does
not change with the stack count: a slot holding one item and a full slot each show one item model.

A contiguous vertical pile is one inventory. Deposit into any block and the pile:

- merges into compatible partial stacks before opening new slots;
- fills from the base upward;
- grows upward when all current slots are full and growth is allowed;
- merges every exact item/damage/NBT identity during settlement;
- sorts its resulting stacks into a stable order; and
- removes empty blocks from the top.

Extraction is from the aimed cell first. On the following scheduled block tick, the whole pile packs
down and sorts over the gap.

Storage is the only type with a permanence mode. In Toggle Permanent mode, use the modifier and an
empty hand on any block in the pile. A permanent pile retains empty blocks; a temporary pile removes
empty blocks from its top and disappears completely when emptied. The setting belongs to the whole
pile and is shown above the hotbar when toggled.

A redstone torch rotates one Storage block's visible 3 x 3 x 3 layout. It does not rotate the entire
vertical pile.

## Singles Stacks

A Singles Stack holds 64 items, one per cell in a 4 x 4 x 4 grid. An item that the server currently
classifies as valid for Bar is invalid for Singles.

Every item needs support:

- the bottom layer of the lowest Singles block rests on the world;
- any other cell rests on the cell directly beneath it; and
- across a boundary between two Singles blocks, support follows the same visible column even if the
  two blocks have different rotations.

A deposit goes into the cell selected by the view through the grid. It does not search the rest of
the column if that named cell is occupied or unsupported.

Removing an item shifts every occupied cell above it in that visible column down one layer. The
shift crosses block boundaries and preserves each item's own rotation. It does not horizontally
compact other columns or erase preexisting gaps. Empty blocks left at the top remove themselves.

A redstone torch rotates a whole Singles block. A soul torch rotates only the aimed item. Both are
quarter-turns. Per-item rotation remains attached to the item when gravity moves it.

## Bar Stacks

A Bar Stack holds 64 server-approved ingots rendered as fixed bar-shaped cuboids. It has eight
layers of eight bars; each layer lies across the layer below it. The item model itself is not drawn.
A resource pack supplies or derives the bar texture and tint.

Every bar above the bottom layer must overlap a bar immediately below it. The bottom of a Bar column
rests on the world, and support crosses a boundary between two adjacent Bar blocks.

When a player removes a bar, every bar that loses support drops as an item. That loss of support can
travel through multiple layers and blocks. Bars that still overlap support stay in place. Breaking
or replacing a Bar block produces the same support collapse above it.

Bar Stacks have no rotation gesture. Their alternating layer directions are fixed.

## Shared block behavior

- **Water:** All three blocks are waterloggable.
- **Light:** Each occupied cell containing a glowing `BlockItem` contributes one quarter of that
  block's default light level, using integer division. Contributions add and the block is capped at
  light level 15. Item count within one Storage slot does not multiply its contribution.
- **Pistons:** All three blocks block piston movement.
- **Collision:** Storage has a full-block physical shape. Singles and Bar collision follows their
  occupied cells or bars. Their empty internal space does not suffocate the player or fog the
  camera, but mobs treat the entire block as unavailable for pathfinding.
- **Empty dynamic blocks:** An empty Singles or Bar block has no outline shape, but its full-block
  interaction shape still makes it clickable and breakable.
- **Sounds:** Deposits, extraction, and rotations play server-selected sounds. Data packs can replace
  those sounds per type and action.

## Automation

Every block exposes loader-native item storage on all six sides: `IItemHandler` on Forge and
Transfer API `Storage<ItemVariant>` on Fabric. The view covers the entire contiguous vertical run,
no matter which block a hopper, pipe, or other machine connects to. Slots are numbered from the
bottom block upward.

| Type | One automation slot means | Slot limit |
| --- | --- | ---: |
| Storage | One ordinary inventory slot | 64 advertised; the item's own maximum still applies |
| Singles | One physical display cell | 1 |
| Bar | One physical bar position | 1 |

The advertised storage contains every current position plus one block of headroom while the run is
below the configured maximum height. Inserting into a headroom position can grow the run. Growth is
refused when the type is disabled, the space cannot be replaced, an entity obstructs the final
shape, build height or maximum run height is reached, or the loader's world-edit authority refuses
the automation actor.

Storage automation insertion is positional at the moment of the call, then the next settlement
packs and sorts the pile. Singles and Bar insertion accepts only the exact empty supported position
named. A machine that walks positions from bottom to top builds their structures layer by layer.

Automated extraction differs by type:

- Storage removes from the named slot, then the pile settles.
- Singles uses the same vertical one-column gravity as player extraction.
- Bar removes the named bar and moves the whole column's topmost bar into the hole. This avoids the
  item-dropping collapse caused by player extraction.

A reentrant automation mutation triggered during another structural mutation is refused with the
ordinary loader failure result and can be retried on a later tick. On Fabric, mutations are staged
until the outer Transfer API transaction commits; Singles and Bar permit one structural extraction
position per transaction, so bulk callers should retry for subsequent items.

## Comparators

A comparator against any block reads the fill of the entire contiguous run, not just that block.

```text
0 when empty, otherwise floor(fill * 14) + 1
```

Storage counts each slot as a fraction of its legal stack size. Singles and Bar count occupied
positions. Signal 0 therefore means empty and nothing else; a completely full current run gives 15.

## Server configuration

Each world stores its loader-neutral JSON policy at
`<world>/serverconfig/somestacks-server.json`.

| Setting | Default | Player-visible effect |
| --- | ---: | --- |
| `piles.max_pile_height` | `8` | Maximum height of every same-type run, from 1 through 64 |
| `stacks.enable_storage_stack_block` | `true` | Prevents new Storage placement and growth when false |
| `stacks.enable_singles_stack_block` | `true` | Prevents new Singles placement and growth when false |
| `stacks.enable_bar_stack_block` | `true` | Prevents new Bar placement and growth when false |
| `compatibility.disable_mods` | empty | Refuses new items from listed namespaces in gestures and automation |
| `compatibility.disable_items` | empty | Refuses exact items in player deposit gestures only |
| `compatibility.ingot_tags` | Forge: `forge:ingots*`; Fabric: `c:ingots*`; both: `somestacks:ingots` | Defines what Bar accepts and Singles refuses |
| `render_gallery.placements_per_tick` | `64` | Throttles administrator gallery construction |
| `render_gallery.gen_mods` | empty | Namespaces used by the `list` gallery/write forms |
| `render_gallery.gen_items` | empty | Items used by `/ss gallery items` |

Disabling a type does not remove existing blocks or prevent extraction from them. Disabling an item,
mod, or ingot category does not eject existing contents. The client receives the three enable flags
on login and skips disabled types while cycling placement modes.

An ingot-tag entry may contain `*`. For example, `forge:ingots*` matches `forge:ingots` and child
tags such as `forge:ingots/copper`; Fabric uses the corresponding `c:ingots*` convention by
default. Data-pack tag changes are picked up on reload.

## Commands

`/ss` is primarily an administrator and render-authoring tool.

| Command family | Permission | Purpose |
| --- | --- | --- |
| `/ss help [command]` | Anyone | List commands or explain one |
| `/ss item <item> <mode> ...` and `reset` | Level 2, player | Change how one item appears on that client |
| `/ss write changed` | Level 2, player | Save that client's changed item profiles |
| `/ss write <modid|all|list>` | Level 2, player | Generate complete profile files on that client |
| `/ss gallery <modid|all|list|items>` | Level 3, player | Build Storage render galleries |
| `/ss ingotgallery <modid|all|list>` | Level 3, player | Build Bar render galleries |
| `/ss gen ...` | Level 2 | Edit gallery namespace and item lists |
| `/ss deny ...` | Level 2 | Edit disabled namespace and exact-item lists |
| `/ss ingot ...` | Level 2 | Edit the ingot tag-pattern list |
| `/ss reload` | Level 2 | Reload server item-render overrides and resync players |

Gallery commands build east of the player over a sandstone floor, with rows running north. They
replace their floor and stack positions directly and do not perform the protection checks used by
ordinary placement, which is why they require permission level 3. Large galleries are spread across
server ticks.

`/ss reload` does not reread the world-policy JSON. Command edits are saved immediately; direct
file edits take effect after restarting the server.

## Item appearance and render overrides

Storage and Singles can show an item in four modes:

| Mode | Appearance |
| --- | --- |
| `2d` | Flat item art projected onto visible faces of a small background cube |
| `3d` | The item's renderer in FIXED context |
| `gui` | Its inventory-style renderer, adjusted to sit in the cell |
| `block` | A BlockItem's block state, with fallback to `3d` when that cannot be drawn safely |

Each profile can also set scale and a three-component offset. Resolution order is server override,
the player's local override file, resource-pack compatibility data, then automatic measurement. A
server override therefore wins over local preferences for Storage and Singles presentation.

Unconfigured items are measured from their actual baked model and fitted into a cell. Results are
cached at `config/somestacks/measured_cache.json`. Changing selected resource packs between sessions,
changing the owning mod's version, or manually reloading resources invalidates relevant cached
measurements.

`/ss item` changes the player's in-memory local layer immediately. `/ss write changed` saves only
those explicit changes to `config/somestacks/item_overrides.json`. The namespace forms of `/ss write`
produce complete files in `config/somestacks/generated_overrides/`; that directory is output only
and is not loaded automatically.

Bar appearance is separate. Resource packs map item ids to bar textures and optional tints under
`assets/<namespace>/textures/bars/*.json`. Missing tints are derived from the item's sprite and
registered item color. Bar mappings are client-side and are not synchronized, so two players with
different resource packs may see different bars.

## Data-pack and resource-pack extension points

- A data pack can add items to tags selected by `ingot_tags`, including the shipped
  `somestacks:ingots` tag.
- A data pack can replace stack action sounds through
  `data/<namespace>/somestacks_sounds/*.json`.
- A resource pack can add Storage/Singles profiles through
  `assets/<namespace>/item_render_overrides/*.json`.
- A resource pack can add Bar textures and tints through
  `assets/<namespace>/textures/bars/*.json`.
- A server can impose Storage/Singles profiles through
  `config/somestacks/server_item_overrides/*.json`, synchronized on login or `/ss reload`.

## Multiplayer protection and packet validation

Player placement, deposit, extraction, and rotation answer to build limits, obstruction, the world
border, and spawn protection on both loaders. On Forge, interaction and structural edits also fire
the ordinary Forge events, allowing claim and logging mods that use those hooks to allow, deny, or
record the edit. The initial Fabric port has no general claim-event integration and applies the
vanilla checks only. Automatic growth and removal use a loader-provided automation actor.

The server independently validates every gesture packet: one attempt per player per tick,
nonspectator status, loaded target, reach, held item, target block and index, adjacency, support,
type enablement, height, and protection as applicable. Cell selection for deposits is recomputed
from the player's current view.

## Current boundaries

- There is no inventory screen, portable stack item, recipe, creative-tab entry, or block-item
  drop.
- Only Storage piles can be permanent. Empty Singles and Bar blocks are normally removed by their
  structural cleanup.
- Exact-item denial applies to player gestures, not automation; namespace denial applies to both.
- Server item-render overrides govern Storage and Singles profiles, not client-only Bar texture
  mappings.
- Existing contents remain legal to extract and to move internally after a config or tag change
  that would reject a new deposit.
- Fabric protection currently covers vanilla build, obstruction, border, and spawn rules, but not
  loader-specific claim or logging APIs.
