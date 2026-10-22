<!-- FIXED HEADER: not content. Do not edit, trim, or count toward the size limit. -->
> **Orientation snapshot, not a specification.** Describes the code as it currently is; the code wins on any disagreement. See CLAUDE.md § Orientation files.
<!-- END FIXED HEADER -->

# Some Stacks — player-facing behavior

Some Stacks makes items visible storage: deposit to create a stack; empty it to remove it. No block items, recipes, creative entries, or screens.

Requires Minecraft 1.20.1 and matching client/server loader builds: Forge 47.4.10 or later within 47.x, or Fabric Loader 0.19.3 with Fabric API 0.92.7+1.20.1.

## The three stack types

| Type | Capacity per block | Contents | Character |
| --- | --- | --- | --- |
| **Storage Stack** | 27 item stacks | Any allowed item | Bulk storage that merges, sorts, packs down, and grows or shrinks |
| **Singles Stack** | 64 items | Allowed non-ingots | A 4 x 4 x 4 display grid, one supported item per cell |
| **Bar Stack** | 64 items | `somestacks:ingots` items | Eight alternating layers of supported bars |

Contiguous vertical blocks of one type form a run, limited by default to eight blocks (216 Storage slots or 512 Singles/Bar positions).

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

Only the main hand acts; torches are not spent. Holding `V` prevents main-hand item use on air. Rotation shows its angle and can occur every tick; sound is limited to once per four ticks per player. Sneaking retains ordinary interaction except for torch gestures.

## Depositing and placing

Placement and first deposit are atomic, requiring a valid item, supported cell, unobstructed shape, replaceable space, permitted height, and edit permission. Failure leaves no empty stack.

Water placement waterlogs the stack and preserves water. A top-face deposit onto an occupied or unsupported cell can add the selected type above the run. Different types can sit beside or atop each other; only contiguous same-type blocks share a run.

Creative deposits spend nothing.

Deposits are rejected when the server has disabled the item's mod or exact item id; existing contents stay extractable. Items the selected type does not accept simply do nothing: Bar takes only the `somestacks:ingots` item tag, Singles takes everything allowed that Bar does not.

## Extracting

Plain right-click targets the nearest rendered item along the eye ray through the client's clicked point, preserving targeting under lag.

- Storage: as much of the targeted stack as the hand can hold.
- Singles: one item.
- Bar: one bar, possibly collapsing unsupported bars above it.

The main hand must be empty or hold matching item/damage/NBT with room. Stack clicks are consumed even if extraction fails.

Breaking or replacing any stack block drops that block's local contents; no stack block item drops. Storage runs above and below the gap settle independently in place. Singles leaves separated runs standing. Removing a Bar Stack removes the support seam beneath the Bars above it, so unsupported bars collapse and drop.

Contents drop even with `doTileDrops` false.

## Per-type behavior

### Storage

27 ordinary stacks shown as a 3 x 3 x 3 grid; a slot holding one item and a full slot show the same item model. A contiguous pile is one inventory: deposits merge compatible partial stacks first, fill from the base up, and grow upward when full and allowed. Settlement merges exact item/damage/NBT identities, sorts, packs down, and removes empty top blocks. Extraction removes from the aimed cell first; the following scheduled block tick settles the pile.

Only Storage has permanence: permanent piles retain empty blocks; temporary piles trim empty tops and disappear when emptied. This pile-wide setting shows above the hotbar when toggled. A redstone torch rotates one block's layout.

### Singles

64 items, one per cell in a 4 x 4 x 4 grid. An item currently valid for Bar is invalid here. Every item needs support: the lowest block's bottom layer rests on the world, every other cell rests on the cell directly below, and support follows the same visible column across block seams even when the blocks have different rotations.

A deposit goes only into the cell selected by the view; it does not search the column when that cell is occupied or unsupported. Removing an item shifts every occupied cell above it in that visible column down one layer, across seams, preserving each item's own rotation. It does not compact other columns or erase preexisting gaps. Empty top blocks remove themselves.

A redstone torch rotates a whole block; a soul torch rotates only the aimed item. Both are quarter-turns. Per-item rotation stays with the item when gravity moves it.

### Bar

64 ingots rendered as fixed bar cuboids in eight layers of eight, each layer across the one below. The tag covers the loader's common ingot/brick tags, vanilla ingots and bricks, and listed modded ones. Resource packs supply or derive bar textures and tints; item models are not drawn.

The bottom layer rests on the world. Every bar above it must overlap a bar directly below, and support crosses seams between adjacent Bar blocks. Removing a bar, or breaking a Bar block, drops every bar that loses support, possibly cascading through layers and blocks. Supported bars stay in place. Bars have no rotation gesture.

## Shared block behavior

- **Water:** all three are waterloggable; an emptied stack removed by cleanup leaves its water behind.
- **Light:** each occupied cell holding a glowing `BlockItem` contributes a quarter of that block's default light level by integer division; contributions add, capped at 15. Storage slot counts do not multiply it.
- **Pistons:** all three block piston movement.
- **Structures:** structure rotation and mirroring turn Storage and Singles layouts with the build; per-item Singles rotations are kept.
- **Missing mods:** a saved item whose mod is absent is kept aside, logged, and returns with the mod.
- **Collision:** Storage is a full block. Singles and Bar collide on occupied cells or bars; their empty space does not suffocate or fog the camera, but mobs treat the whole block as unavailable for pathfinding.
- **Empty dynamic blocks:** empty Singles and Bar blocks have no outline shape, but their full-block interaction shape makes them clickable and breakable.
- **Sounds:** each action plays a registered, subtitled `somestacks:block.<type>.<action>` event (wood sounds by default), replaceable in `sounds.json`.
- **Vibrations:** placement, growth, and cleanup/cascade removal emit placement/destruction events; each successful deposit, extraction, rotation, or permanence toggle emits a block change from the player (only destruction if it removed the block). Denied edits emit none.

## Automation

Every block exposes the whole contiguous run on all six sides: `IItemHandler` on Forge, Transfer API `Storage<ItemVariant>` on Fabric. Slots number from the bottom block up.

| Type | One slot | Slot limit |
| --- | --- | --- |
| Storage | One inventory slot | 64 advertised; the item's own maximum still applies |
| Singles | One display cell | 1 |
| Bar | One bar position | 1 |

Advertised storage includes every current position plus one block of headroom while below maximum height. Inserting into headroom can grow the run. Growth is refused when the type is disabled, the space cannot be replaced, an entity obstructs the final shape, build height or run height is reached, or the loader's edit authority refuses the automation actor.

Storage insertion is positional at the moment of the call, then the next settlement packs and sorts. Singles and Bar accept only the exact empty supported position named; machines walking positions bottom to top build layer by layer.

Storage extraction removes from the named slot then settles; Singles uses player-style column gravity; Bar moves the column's topmost bar into the hole, avoiding player extraction's collapse.

Reentrant automation during a structural mutation is refused; callers can retry. Fabric stages edits until the outer Transfer API commit, sharing contents/capacity across all sides and run members. Singles/Bar allow one structural extraction position per run per transaction; bulk callers retry.

## Comparators

A comparator reads the fill of the entire run, not just one block:

```text
0 when empty, otherwise floor(fill * 14) + 1
```

Storage counts each slot as a fraction of its legal stack size; Singles and Bar count occupied positions. 0 means empty; a completely full current run gives 15.

## Server configuration

World policy: Forge uses `<world>/serverconfig/somestacks-server.toml`, reloaded while running; Fabric uses `<world>/serverconfig/somestacks-server.json`, reread by `/ss reload`. Settings:

| Setting | Default | Effect |
| --- | --- | --- |
| `piles.max_pile_height` | `8` | Maximum height of every same-type run, 1-64 |
| `stacks.enable_storage_stack_block` | `true` | Prevents new Storage placement and growth when false |
| `stacks.enable_singles_stack_block` | `true` | Prevents new Singles placement and growth when false |
| `stacks.enable_bar_stack_block` | `true` | Prevents new Bar placement and growth when false |
| `compatibility.disable_mods` | empty | Refuses new items from listed namespaces in gestures and automation |
| `compatibility.disable_items` | `evilcraft:broom_part` | Refuses exact items in player deposits only; galleries omit them |
| `render_gallery.enabled` | `false` | Enables `/ss gallery` and `/ss ingotgallery` |
| `render_gallery.required_permission_level` | `3` | Permission level those commands require, 0-4 |
| `render_gallery.placements_per_tick` | `64` | Throttles gallery construction |
| `render_gallery.gen_mods` | empty | Namespaces used by `list` gallery/write forms |
| `render_gallery.gen_items` | empty | Items used by `/ss gallery items` |

Disabled types keep existing blocks and extraction. Item/mod/tag changes never eject contents. Client settings sync on login and when changed; cycling skips disabled types.

## Commands

`/ss` holds admin and render tools.

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ss help [command]` | Anyone | List or explain commands |
| `/ss item <item> <mode> ...` / `reset` | Anyone, client-local | Change how one item appears on your client |
| `/ss write changed` | Anyone, client-local | Save your changed profiles |
| `/ss write <modid\|all\|list>` | Anyone, client-local | Generate complete profile files; `list`/`all` use the server's synced lists |
| `/ss gallery <modid\|all\|list\|items>` | Configured gallery permission, player; off by default | Build Storage render galleries |
| `/ss ingotgallery <modid\|all\|list>` | Configured gallery permission, player; off by default | Build Bar render galleries |
| `/ss gen ...` | Level 2 | Edit gallery namespace/item lists |
| `/ss deny ...` | Level 2 | Edit disabled namespace/exact-item lists |
| `/ss reload` | Level 2 | Reload policy; sync changed client settings |

Galleries build east over sandstone across ticks, in id order without disabled items, replacing floor and stacks without placement protection.

Command edits save immediately. Mod lists normalize case/spacing and duplicates; mods need not be installed. Each allows 256 namespaces of at most 256 characters. Malformed config names are logged and omitted. Over-limit configs retain the previous policy (defaults at startup); rejected additions/reloads report failure.

## Item appearance

Storage/Singles modes: `2d` projects item art onto cube faces; `3d` uses the FIXED renderer; `gui` fits the inventory renderer; `block` draws a BlockItem state, falling back to `3d` when unsafe. Profiles also set scale and a three-component offset.

Resolution order: local override, resource-pack data (including server packs), then automatic measurement. Unconfigured items are measured and fitted to a cell; results cache at `config/somestacks/measured_cache.json`. Pack selection changes between sessions, owning-mod version changes, and resource reload invalidate relevant measurements.

`/ss item` edits the local in-memory layer. `/ss write changed` saves explicit edits to `config/somestacks/item_overrides.json`. Namespace forms write complete files to output-only `config/somestacks/generated_overrides/`, never loaded automatically.

A failing `2d` model shows a red "ERR" cube until resource reload, logged once.

Bar appearance is separate: resource packs map item ids to textures and optional tints under `assets/<namespace>/textures/bars/*.json`. Missing tints derive from the sprite and tint of the first quad the item draws in inventory. Bar mappings are client-only, so players with different packs can see different bars.

## Extension points

- Data packs: Bar admission through `somestacks:ingots`.
- Resource packs: profiles at `assets/<namespace>/item_render_overrides/*.json`, Bar mappings as above, and sounds at `assets/somestacks/sounds.json`.

## Protection and validation

Player placement, deposit, extraction, and rotation honor build limits, obstruction, border, and spawn protection. Forge uses its interaction/place/break events; Fabric uses `UseBlockCallback` and `PlayerBlockBreakEvents`. Adjacent deposits validate the clicked block and destination. Automatic growth/removal acts as `[SomeStacks]`; cleanup caused only by one player's extraction or permanence toggle acts as that player. Fabric placement and growth also consult Common Protection API, FTB Chunks, and Open Parties and Claims when installed, before space is offered to automation; adjacent deposits also ask Common Protection API.

Fabric removal asks Common Protection API and reports cancellation/completion with the original actor, state, and entity.

Block-use clicks use synchronized mode/modifier state. Existing-stack gestures are block use, so a Forge rule denying only item use does not stop them. Vanilla checks reach and loaded chunks; the server validates player status, hand, items, adjacency, support, enablement, height, and protection. Forge restores denied placement state/NBT and publishes neighbor updates after acceptance.
