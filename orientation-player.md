<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — player-facing behavior

Current behavior; see `orientation-code.md` for structure.

Some Stacks turns held items into visible storage. It adds three blocks, with no block items, recipes, creative-tab entries, or storage screens. Depositing creates a stack; removing its last contents normally removes it.

Requires Minecraft 1.21.4 and matching client/server loader builds. Fabric requires Fabric API; Common Protection API is optional.

## The three stack types

| Type | Capacity per block | Contents | Character |
| --- | --- | --- | --- |
| **Storage Stack** | 27 item stacks | Any allowed item | Bulk storage that merges, sorts, packs down, and grows or shrinks |
| **Singles Stack** | 64 items | Allowed non-ingots | A 4 x 4 x 4 display grid, one item per cell |
| **Bar Stack** | 64 items | Data-pack-defined ingots | Eight alternating layers of bars |

A vertical run of one stack type is a single pile or column with one shared inventory. Default maximum height is 8 blocks (216 Storage slots, 512 Singles or Bar positions).

## Controls

The Stack Modifier is held `V` by default, rebindable under **Options -> Controls -> Some Stacks**.

Hold `V` and right-click air to cycle placement mode: Storage, Singles, Bar, Toggle Permanent. The selected mode shows above the hotbar; server-disabled types are skipped. The mode sets what a new block becomes; it never changes an existing stack.

| Gesture | Result |
| --- | --- |
| Hold `V`, right-click air | Cycle placement mode |
| Hold `V` + item, right-click a stack | Deposit into that stack, whatever the selected mode |
| Hold `V` + item, right-click another block | Place the selected type against that face and make the first deposit; an allowed adjacent Singles/Bar Stack can instead receive the deposit (Fabric requires Common Protection API) |
| Right-click a stack, no `V`, no Shift | Extract the nearest stored item on the view ray |
| Toggle Permanent mode, hold `V`, empty hand, right-click a Storage Stack | Toggle whether that pile keeps empty blocks |
| Shift-right-click a Storage/Singles Stack with a redstone torch | Rotate that block's layout 90 degrees |
| Shift-right-click an occupied Singles cell with a soul torch | Rotate that item 90 degrees |

Forge/NeoForge allow deposits into adjacent Singles/Bar Stacks after consulting destination protection; aiming through an empty grounded cell at its support fills that cell. Fabric allows neighbor deposits when Common Protection API is installed and permits them; otherwise it requires direct clicks.

Only the main hand acts; off-hand items do not block cycling. Torches are not spent. Rotation shows above the hotbar; sound is limited to once every 4 ticks per player. Storage/Singles turn with structures. Sneaking retains ordinary use except for torch gestures.

## Depositing and placing

A placement and its first deposit are atomic: the block is not placed unless the item is valid, the target cell is supported, the final shape is unobstructed and replaceable, the run stays within its height limit, and protection allows the edit. Failed placement leaves nothing behind.

Placement into water waterlogs the stack and keeps the water. A top-face deposit onto an occupied or unsupported top cell adds a new block above the run, of the currently selected type; different types can therefore sit beside or atop one another, and only contiguous same-type blocks share a run.

In creative mode, deposits do not reduce the held stack.

Deposits are rejected when the server has disabled the item's mod or exact item id; existing contents stay extractable. Items the selected type does not accept simply do nothing: Bar takes only items in the `somestacks:ingots` tag, Singles takes everything allowed that Bar does not.

## Extracting

Plain right-click targets the nearest rendered item on the view ray inside the clicked block.

- Storage: as much of the targeted stack as the hand can hold.
- Singles: one item.
- Bar: one bar, and any bars left unsupported above it collapse and drop.

The main hand must be empty or hold the same item and components with room. A plain click on a stack is consumed even when extraction fails, so an unrelated held item is not used against the stack.

Breaking or replacing any stack block drops that block's local contents; no stack block item drops. Storage runs above and below the gap settle independently. Removing a Bar Stack also removes the support seam beneath the Bars above it, so those collapse.

## Per-type behavior

### Storage

27 stacks shown as a 3 x 3 x 3 grid; the art does not change with count. A contiguous pile is one inventory: deposits merge into compatible partial stacks first, fill from the base up, and grow upward when full and allowed. Each scheduled settlement merges exact identities, restores stack sizes, sorts, packs down, and removes empty top blocks.

Storage is the only type with a permanence mode. A permanent pile keeps empty blocks; a temporary pile trims empty blocks from the top and vanishes when emptied. The setting belongs to the whole pile. A redstone torch rotates one block's 3 x 3 x 3 layout, not the pile.

### Singles

64 items, one per cell in a 4 x 4 x 4 grid. An item currently valid for Bar is invalid here. Every item needs support: the lowest block's bottom layer rests on the world, every other cell rests on the one directly below, and support follows the same visible column across a block seam even when the two blocks have different rotations.

A deposit goes only into the cell the view selects; it does not search the column. Removing an item drops every occupied cell above it in that visible column down one layer, across seams, keeping each item's own rotation; it does not compact other columns or close preexisting gaps. Empty top blocks remove themselves.

A redstone torch rotates a whole block; a soul torch rotates only the aimed item. Per-item rotation stays with the item when gravity moves it.

### Bar

64 server-approved ingots rendered as fixed bar cuboids in eight layers of eight, each layer across the one below. The item model is not drawn; a resource pack supplies the bar texture and tint.

Every bar above the bottom layer must overlap a bar directly below, and support crosses a seam between adjacent Bar blocks. Removing a bar, or breaking a Bar block, drops every bar that loses support, possibly cascading through layers and blocks. Bars do not rotate.

## Shared block behavior

- **Water:** all three are waterloggable.
- **Light:** each cell holding a glowing `BlockItem` contributes a quarter of that block's light level by integer division; contributions add, capped at 15. Item count in a Storage slot does not matter.
- **Pistons:** all three block piston movement.
- **Collision:** Storage is a full block. Singles and Bar collide only on occupied cells or bars; their empty space does not suffocate or fog the camera, but mobs treat the whole block as unpathable.
- **Targeting:** Singles and Bar have selection outlines around occupied cells or bars. Aiming through empty space can target a block behind the stack; empty stacks have no outline.
- **Sounds:** deposits, extraction, and rotations use registered Some Stacks sound events with specific subtitles; resource packs can replace them per type and action through ordinary `sounds.json` entries.

## Automation

Every block exposes the whole run on all six sides: `IItemHandler` on Forge/NeoForge, Transfer API on Fabric. Slots number from the bottom block up.

| Type | One slot | Slot limit |
| --- | --- | --- |
| Storage | one inventory slot | 64 advertised; the item's own max still applies |
| Singles | one display cell | 1 |
| Bar | one bar position | 1 |

The advertised storage includes every current position plus one block of headroom while below the max height; inserting into headroom can grow the run. Growth is refused when the type is disabled, the space cannot be replaced, an entity blocks the final shape, a height limit is reached, or the loader's edit authority refuses the automation actor. All three loaders present automation under the same `[SomeStacks]` identity so machine and claim policies can recognize it.

Storage insertion is positional at the moment of the call, then the next settlement packs and sorts. Singles and Bar accept only the exact empty supported position named. For extraction, Storage removes from the named slot then settles, Singles uses the same one-column gravity as player extraction, and Bar moves the column's topmost bar into the hole to avoid the player-extraction collapse.

Reentrant automation during another structural mutation is refused with the ordinary loader failure and can be retried next tick. On Fabric, all accesses to one run share staged contents until the transaction commits, including different sides and different blocks. Singles and Bar allow one structural extraction position per transaction.

## Comparators

A comparator reads the fill of the entire run, not just one block:

```text
0 when empty, otherwise floor(fill * 14) + 1
```

Storage counts each slot as a fraction of its legal stack size; Singles and Bar count occupied positions. 0 means empty; a full run gives 15.

## Server configuration

Forge and NeoForge use loader-managed `somestacks-server.toml`, with per-world overrides under `<world>/serverconfig/`; file edits are picked up by the native config lifecycle. Fabric uses per-world JSON at `<world>/serverconfig/somestacks-server.json`, reread by `/ss reload`.

| Setting | Default | Effect |
| --- | --- | --- |
| `piles.max_pile_height` | `8` | Maximum height of every same-type run, 1-64 |
| `stacks.enable_*_stack_block` | `true` | When false, blocks new placement and growth of that type |
| `compatibility.disable_mods` | empty | Refuses new items from listed namespaces in gestures and automation |
| `compatibility.disable_items` | empty | Refuses exact items in player deposits only |
| `render_gallery.enabled` | `false` | Enables `/ss gallery` and `/ss ingotgallery` |
| `render_gallery.required_permission_level` | `3` | Permission level those two commands need |
| `render_gallery.placements_per_tick` | `64` | Throttles gallery construction |
| `render_gallery.gen_mods` / `gen_items` | empty | Namespace and item lists used by the `list` and `items` gallery forms |

Disabling a type, item, mod, or ingot category never removes or ejects existing contents. The client receives the enable flags on login and skips disabled types when cycling modes. Data-pack tag changes are picked up on reload.

## Commands

`/ss` combines server administration with local render-authoring tools.

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ss help [command]` | anyone | List or explain commands |
| `/ss item ...` / `reset` | anyone; client-local | Change how one item appears on that client |
| `/ss write changed\|<modid>\|all\|list` | anyone; client-local | Save changed item profiles, or generate complete profile files |
| `/ss gallery ...` / `/ss ingotgallery ...` | `render_gallery.required_permission_level`; off by default | Build Storage or Bar render galleries |
| `/ss gen ...` / `deny ...` | level 2 | Edit gallery lists or disabled lists |
| `/ss reload` | level 2 | Refresh server policy, then resync players; Fabric rereads JSON |

Galleries build east over sandstone, one alphabetized column per mod, spreading work across ticks. They bypass placement protection and are off by default. Fabric `/ss reload` rereads JSON, using defaults for malformed fields; Forge/NeoForge validate TOML through their config lifecycle. Edit ingot classification through `somestacks:ingots`, applied on `/reload`.

## Item appearance

Storage and Singles show items in four modes: `2d` (flat art), `3d` (the FIXED renderer), `gui` (the inventory renderer), and `block` (a BlockItem's state, falling back to `3d`). Profiles also set scale and a three-component offset.

Resolution order is local override, resource-pack data, then automatic measurement. A server can distribute profiles through its server resource pack; local overrides keep priority. Measured results cache at `config/somestacks/measured_cache.json` and are invalidated by pack, mod-version, or reload changes.

`/ss item` changes the client's in-memory layer; `/ss write changed` saves it to `config/somestacks/item_overrides.json`. Namespace forms write complete files to `config/somestacks/generated_overrides/`, which is output only. Servers cannot invoke these commands or request client writes.

For Bar appearance, resource packs map item ids to textures and tints under `assets/<namespace>/textures/bars/*.json`, with missing tints derived from the item's sprite and color. Bar mappings are not synchronized, so players with different packs may see different bars.

## Extension points

- Data pack: edit `somestacks:ingots` to classify Bar items. The shipped tag includes optional `c:ingots` and `forge:ingots` tags plus explicitly listed ingots; child tags are included only through normal tag composition.
- Resource pack: replace registered `somestacks:block.*` action sounds through `sounds.json`; add Storage/Singles profiles via `assets/<namespace>/item_render_overrides/*.json`; add Bar textures and tints via `assets/<namespace>/textures/bars/*.json`.
- Server: distribute Storage/Singles profiles and Bar mappings through a server resource pack.

## Protection and validation

Gestures honor vanilla block-use, loader results, build limits, obstruction, border and spawn protection. Forge/NeoForge consult destination interaction protection for neighbor deposits. Fabric requires direct stack clicks unless Common Protection API is installed and permits a neighbor deposit. Fabric consults that API for placement and automation growth when available; otherwise those edits use vanilla checks. Automation uses `[SomeStacks]`; player cleanup retains the player, mixed Storage cleanup uses automation. Forge/NeoForge publish accepted placements and restore denied snapshots without neighbor updates. All loaders fire native break hooks. Surviving edits emit sculk-detectable change; removing the final Single/Bar emits only destruction there.

The payload carries mode/modifier state; actions use vanilla block-use hits. The server checks hand, spectator/protection state, item, target, support, enablement, height and obstruction, then selects cells along the reach ray through the hit. Clients never choose cells.

## Saved worlds

A 1.21.1 world migrates its items and rotations on load. Unreadable items stay saved and return when readable into a free valid slot. Warnings include dimension and position; failed retries warn once per location per server session.

