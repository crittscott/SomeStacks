# Some Stacks Living Specification

This document describes the architecture and operating behavior of the current codebase. It is the orientation document for maintainers: update it when a change alters a system boundary, user-visible rule, persistent state, data format, or important extension point. It deliberately omits routine implementation detail.

## Technical baseline

- Mod id: `somestacks`
- Minecraft 1.20.1, Forge 47.4.0, Java 17, Parchment mappings
- Both client and server must have the mod and use network protocol `1`.
- The mod registers three blocks and their block entity types. It does not register block items, recipes, menus, or a conventional inventory UI; stack blocks are created through the interaction system described below.

## Product model

Some Stacks turns items in the player's hand into world storage. Each stack is a block entity whose inventory is also its visible model. Players target the rendered cells directly to deposit or extract items.

The runtime is split into four cooperating parts:

1. Client interaction rules interpret right-clicks and select a cell or placement position.
2. Network packets carry the requested operation to the server.
3. Server-side block entities own inventory, validation, persistence, packing, gravity, and world mutation.
4. Client block entity renderers draw the synchronized contents, using resource-reloadable compatibility data where ordinary item rendering is insufficient.

The normal flow is:

`client click -> first matching interaction rule -> packet -> server validation and mutation -> block entity update -> client renderer`

There is no continuously ticking block entity. Work happens in response to interaction, capability access, configuration events, or resource reloads.

## The three stack types

| Type | Stored contents | Visual/physical arrangement | Distinct behavior |
| --- | --- | --- | --- |
| Storage Stack | 27 ordinary item stacks per block | A rotatable 3 x 3 x 3 grid with gaps between cells | Vertical blocks form a pile. Deposits merge, overflow upward, and may create another Storage Stack. The pile periodically sorts, consolidates, packs downward, and removes empty temporary blocks. |
| Singles Stack | 64 items, one per slot | A rotatable 4 x 4 x 4 grid of touching quarter-block cells | Accepts non-ingot items. Each item must be supported by the cell below. Removing an item shifts every occupied cell above it down one position in the same column. The whole grid and each individual item have independent quarter-turn rotations. |
| Bar Stack | 64 items, one per slot | Eight two-pixel-high layers of eight bars; successive layers alternate east-west and north-south | Accepts items in `#somestacks:ingots`, which delegates to `#forge:ingots`. A bar above the bottom layer must overlap at least one bar beneath it. Extraction repeatedly drops every bar made unsupported by the removal. |

Storage accepts any nonempty item not excluded by the disabled-mod list. Singles uses the same rule but excludes items valid for Bar Stack. Player-driven deposits also reject item ids in the server's disabled-item list.

All three block entities expose Forge's item-handler capability on every side. Storage wraps its local 27 slots in a pile-aware handler: a real insertion uses normal Storage deposit behavior, including upward overflow, regardless of the requested slot. Singles and Bar expose their raw 64-slot handlers. Their player-path support and cascade rules are not imposed on direct capability operations.

## Interaction model

`V` is the default binding of the stack-modifier key, rebindable under its own Some Stacks category in the controls screen. It is a held modifier, not an ordinary press-to-cycle key. Client Forge interaction events run through ordered rule lists; the first match wins. World changes occur only after a packet reaches the server.

| Gesture | Result |
| --- | --- |
| Hold `V` and right-click air | Cycle Storage, Singles, Bar, and Toggle Permanent modes, showing the selected mode in the action bar. The code does not require Shift. Synced-disabled Singles and Bar modes are skipped. |
| Hold `V`, hold an item, and right-click an existing stack | Deposit into the clicked stack, irrespective of the currently selected placement mode. Clicking the top face of a Singles or Bar Stack whose targeted column is full instead places the current mode's stack in the space above and makes the first deposit there, growing the column upward. |
| Hold `V`, hold an item, and right-click another block | If the adjacent block on the clicked face is a Singles or Bar Stack, deposit there. Otherwise place the selected stack type in the replaceable adjacent position and make the first deposit. A newly placed block is removed again if that deposit fails. |
| Right-click a stack without `V` or Shift | Ray-select the nearest occupied rendered cell and extract it. Storage takes as much of the selected item stack as the player's hand can accept; Singles and Bar take one item. The hand must be empty or contain the same item and tags with free capacity. |
| Select Toggle Permanent, hold `V`, use an empty hand, and right-click a Storage Stack | Toggle whether that Storage Stack may disappear automatically when empty. |
| Shift-right-click a Storage or Singles Stack with a redstone torch | Rotate the entire stored layout by 90 degrees. Bar layouts have a fixed alternating orientation. |
| Shift-right-click an item in a Singles Stack with a soul torch | Rotate that individual rendered item by 90 degrees. |

Shift plus `V` is not a general placement gesture. Placement and deposit rules require that Shift not be held.

The torch rules do not apply to Bar Stack: block rotation matches only Storage and Singles, and item rotation matches only Singles. A torch click on a Bar Stack matches no rule and falls through to vanilla, so the torch is placed against the block as usual.

### Cell targeting and support

Extraction traces only occupied cells and chooses the closest hit. Singles and Bar deposit traces every possible cell along the view ray and chooses the last empty cell before the first occupied cell, or the farthest intersected empty cell when no occupied cell is hit. The server recomputes deposit targeting from the player's current eye position and look direction.

Grounding is enforced for player deposits:

- A Singles item is grounded on the bottom layer or by the same column in the layer immediately below.
- A Bar is grounded on the bottom layer or when its horizontal footprint overlaps an occupied bar in the immediately lower layer.
- When a new Singles or Bar block is placed above an existing block of the same kind, its first item must also be supported by the top layer of the lower block.

After successful extraction, the server marks the position for same-tick right-click suppression. This cancels the vanilla use-item-on-block event that can arrive after the custom extraction packet and would otherwise use the newly held item at a position whose stack block may just have disappeared.

## Server-side storage behavior

### Storage piles

A Storage deposit fills compatible partial slots, then empty slots. Any remainder recurses into the Storage Stack directly above. If the space above is replaceable and Storage creation is enabled, the deposit creates another Storage Stack and continues there. Creating that overflow block runs the same protection path as any placement — build height, world border, spawn protection, and Forge's block-place event — so a pile never grows above the world or across a protected boundary. Automation-driven overflow (capability insertion) carries no player, so it is attributed to the level's fake player and checked the same way.

A deposit that overflows upward repacks once, from the block the deposit entered, rather than once per block it passed through.

After a successful deposit or extraction, the pile may be repacked. Repacking is throttled by a cooldown stored on the pile's base block and processes at most the configured number of contiguous Storage Stack blocks around the initiating block. Within that window it:

1. Copies all stored stacks.
2. Totals them by exact identity — item, damage value, and tags — so compatible stacks always merge no matter where in the window they sat.
3. Re-cuts each total into whole stacks plus at most one partial, then orders the result by item registry id, damage value, tags, and count with the fullest stack first.
4. Writes the result from lower blocks and lower slot indices upward.
5. Removes empty, non-permanent blocks from the top of the processed window until it reaches a nonempty block, a permanent block, or a block that still has a Storage Stack directly above it. An empty block is never removed while another Storage Stack sits directly above, so a pile taller than the window is not severed.

The sort cooldown and maximum window are server-performance controls, not capacity limits. A vertical pile may be taller than one repack window.

Storage Stack is the only type with comparator output. Its signal is the rounded fraction of its 27 local slots' capacity; neighboring Storage blocks are not included in that calculation.

### Singles gravity

Singles removal closes the gap in one vertical column. Every occupied cell above the removed position moves down exactly one layer, preserving its per-item rotation. This is a deterministic column shift, not a dropped-item cascade. A cell left empty carries no rotation, so the next item deposited into it starts unrotated.

### Bar gravity

After one bar is extracted, the block scans the remaining bars from the bottom layer up. Any bar without an overlapping support footprint in the layer below is removed and dropped into the world. One pass suffices: a layer is only ever supported by the layer beneath it, which the pass has already settled.

### Cascade publication

Singles and Bar gravity, like Storage repacking, suppress per-slot client sync while they run and settle once at the end: one content update to clients and one light-level recomputation for the whole cascade.

### Empty and broken blocks

- Empty Singles and Bar blocks remove themselves after player extraction.
- Empty Storage blocks remove themselves unless permanent or another Storage Stack sits directly above them; pile repacking removes empty temporary blocks from the processed top under the same never-remove-under-a-stack rule.
- Breaking or replacing any stack block drops every item still in its local item handler.

## World state, collision, light, and persistence

The visible inventories are authoritative block entity state and are included in save NBT and block entity update packets.

- Storage persists items, block rotation, pile-sort time, and the permanent flag.
- Singles persists items, block rotation, and all 64 per-item rotations.
- Bar persists items.

Storage retains the normal full-block shape. Singles and Bar compute and cache an outline/collision union from occupied cells, so their physical shapes match their contents. Both still expose a full-block interaction shape so the player can right-click the block reliably through gaps.

Content changes update the client and recalculate emitted light. Each occupied slot containing a `BlockItem` contributes one quarter of that block's default light emission, integer-truncated; contributions are summed and capped at 15. Item count within a Storage slot does not increase that slot's contribution.

## Networking and synchronization

The logical packet directions are:

- Client to server: place-and-deposit, deposit, extract, rotate block, rotate item, and toggle permanent.
- Server to client: configuration synchronization, user render-override set/reset, and the write-overrides request.

These directions are registered with the network channel, so a packet arriving from the wrong logical side is rejected before it is handled.

The server owns all inventory and block mutation and does not trust client gesture state. Every mutation packet first clears a common boundary: a real sender, a loaded target position, and a target within the player's interaction reach. Operations whose gesture requires a specific held item or an empty hand — rotate block, rotate item, toggle permanent — verify that item state server-side rather than trusting the client. World-editing operations — place, deposit, extract — also consult the world border and vanilla spawn protection, which exempts operators. Because the mod's packets replace the vanilla interaction the client suppresses, deposit and extract additionally fire Forge's right-click-block event server-side, so claim and protection mods can veto access to a stack exactly as they would a vanilla container. Placement additionally checks that the target is replaceable, checks the player's permission to use the item there, fires Forge's block-place event so protection mods can veto or record it, enforces the selected block's enable flag, validates blacklists, and removes a just-created block if its initial deposit fails. Deposit recomputes cell targeting and support. Extraction accepts the client-selected slot index, then validates the block entity, index, contents, and hand compatibility before changing state.

On player login and server-config reload, the server sends clients the three block-enable flags and the admin render overrides read from `config/somestacks/server_item_overrides/`. The client uses the flags for mode selection and the overrides as its top render layer. Blacklists and pile settings stay server-side.

## Rendering architecture

All stack blocks use `ENTITYBLOCK_ANIMATED` and are drawn by block entity renderers rather than ordinary world block models.

- Storage and Singles render each stored item through `CubeRenderHelper` inside the cell selected by their spatial-index utility. Block rotation changes the visual cell coordinates and ray-hit coordinates together. Singles per-item rotation turns the model within its existing cell and does not alter occupancy or collision.
- Bar does not render the original item model. It emits a fixed six-face cuboid for each bar using the configured texture and tint for that item.

### Item render modes

`CubeRenderHelper` has four modes:

| Mode | Behavior |
| --- | --- |
| `2d` | Draw a small `stack_cube` background and project the item's unculled baked quads onto all six faces, applying item tint. |
| `3d` | Use the normal item renderer in `FIXED` display context. |
| `gui` | Use the normal item renderer in `GUI` context with counter-rotation to fit the stack cell. |
| `block` | Render a `BlockItem`'s default block state directly; if block rendering throws, fall back to `3d`. A non-`BlockItem` configured as `block` draws nothing. |

An item no override layer mentions takes its whole profile from measurement (see the measurement pipeline below). The bundled corpus specifies mode, scale, and offset for every item it covers, so items it covers are never measured at render time. The `block` mode is only ever authored; measurement selects `gui` only for the horizontal-art block types described below.

### Item render overrides

Render configuration resolves through layers, per item. The first layer with an entry for an item owns its whole presentation:

1. Server admin overrides: `config/somestacks/server_item_overrides/*.json` on the server, synced to every client at login and on server-config reload. Later files by name order win on duplicate items.
2. The user's own overrides: `config/somestacks/item_overrides.json` on the client, maintained by the `ss` command and loaded at startup. It only ever contains entries the user explicitly set.
3. The bundled corpus: all JSON under `assets/*/item_render_overrides/`, loaded by client resource reload. The bundled files are organized by the namespace of the items they correct and form the main cross-mod compatibility corpus.
4. Measurement, for items no layer mentions.

Every location uses the same schema. Each top-level key is an item id. Every field is optional:

```json
{
  "modid:item": {
    "mode": "2d",
    "scale": 0.8,
    "offset": [0.0, 0.1, 0.0]
  }
}
```

`mode` is one of `2d`, `3d`, `block`, or `gui`; `scale` must be positive; `offset` has exactly three numbers. For `2d`, only the x and y offset components are used.

Fields an entry omits take plain defaults — scale `1` and zero offset — rather than measured values, because scale and offset mean different things from one mode to the next and a measured scale is only valid for the mode it was measured for. An omitted mode is the sole exception and is measured. Malformed entries and fields are logged and skipped.

A multiplayer admin makes overrides authoritative for all players by copying a client-written override file into the server's `server_item_overrides` folder verbatim; no format translation is involved.

### Bar texture data

Client resource reload also loads `assets/*/textures/bars/*.json`. A mapping may be a texture id string or an object with `texture` and optional `tint` fields. A mapped item without an explicit tint is auto-tinted by averaging pixels with alpha greater than 127 from the particle sprite of the item's baked model, then brightening the average ten percent toward white. A mapping with `tint` uses the supplied RGB or ARGB hex color. An unmapped item uses the iron-block fallback texture without auto-tinting.

### Sound data

`assets/*/sounds/*.json` maps each stack type's `deposit` and `extract` actions to registered sound ids. Resource reload replaces the six mutable runtime sound choices. Missing or invalid entries fall back to vanilla wood-place or wool-break sounds. The bundled configuration currently selects vanilla wood sounds.

## Server configuration

The Forge server config contains:

| Setting | Default | Operating effect |
| --- | --- | --- |
| Pile sort cooldown | 20 ticks | Minimum elapsed time between repacks, tracked on the Storage pile base. |
| Maximum stacks per repack | 3 | Limits the contiguous Storage blocks touched by one repack operation. |
| Enable Storage / Singles / Bar | `true` | Prevents new placement of the disabled type. Storage also stops auto-creating overflow blocks. Existing blocks remain present and their direct deposit/extract paths remain usable. |
| Disabled mods | `spartanfire`, `spartanweaponry` | Rejects new contents from those namespaces; existing contents can still be extracted. |
| Disabled items | empty | Rejects those ids on player packet deposit paths; existing contents can still be extracted. |
| `ss` command allow list | empty | Player names permitted to use the `ss` command. Empty means no one may use it. |

The client mode cycler skips synced-disabled Singles and Bar modes. Storage remains in the client cycle even when disabled, but the server still refuses its placement.

## Render measurement

The client can measure the geometry an item actually renders in the `FIXED` display context and derive a complete render profile in the standard override vocabulary (`mode`, `scale`, `offset`). Measurement supplies the presentation of every item that no override layer mentions — the weird mod the user has that the bundled corpus has never seen — and the mode of any entry that omits one.

- Non-custom baked models are measured by resolving item overrides, applying the `FIXED` display transform (honoring model substitution) and the item renderer's origin shift, then accumulating the bounds of every render-pass quad. Flatness is decided by the model's own `isGui3d()`: a generated item sprite reports false and yields `2d`. Geometry whose thinnest axis is a small fraction of its longest also yields `2d`, catching models that claim depth but draw none. Volumetric models yield `3d` with a uniform scale fitting the fixed target fill fraction (0.9) of a stack cell and an offset that recenters the measured bounds. Fitting sizes every model to the same cell regardless of its real-world size, so block families that read too large against their neighbours carry a per-family scale factor applied on top of the fit; blocks deriving from `ButtonBlock` are fitted to three quarters. That factor list is an extension point alongside the presentation list below.
- Blocks deriving from `BasePressurePlateBlock` or `CarpetBlock` carry their art on the horizontal plane, which a flat projection reduces to a one-pixel edge. They are measured in the `GUI` display context behind that path's counter-rotation and yield `gui`, the inventory-slot presentation. This is the only case where measurement selects `gui`, and the list is the extension point for other horizontal-art block types.
- Custom-renderer (BEWLR) items are probed once by running their renderer against a vertex-capturing buffer. A thrown exception or an empty capture falls back to `2d` and logs the failure. Because measured scales describe true drawn size, no custom-renderer scale correction is applied anywhere in the render path.
- Profiles are cached per item and persisted to `config/somestacks/measured_cache.json` on the client, so an item is measured once ever rather than once per session. The cache records each namespace's mod version; at load, entries from a namespace whose version changed are dropped and re-measured, so a mod update cannot leave stale geometry. A manual resource reload, which can also change models, discards the cache entirely.

The bundled `item_render_overrides/` corpus was generated by measuring every namespace and hand-correcting the results in place. Because its entries are complete, editing one field of an entry leaves the others fixed rather than reverting them to measurement.

## The `ss` command

The `ss` command is player-only and gated by the server config's `ss_command_allowlist`: a player may use any `ss` subcommand only if their name is on that list. The list is empty by default, so no one — including the single player of a single-player world — can use `ss` until a name is added; an attempt by a non-listed player fails with a message directing them to the server config. It is the user-facing tool for correcting the rendering of items the bundled corpus does not cover, or covers wrongly:

- `ss item <item> <mode> <scale> <x> <y> <z>` sets the entry for that item in the issuing player's user override layer. The change renders immediately but lives only in memory until written.
- `ss item <item> reset` removes that entry; the item returns to server, built-in, or measured behavior. Because the layers below are never modified, reset always restores original behavior, not a previous tweak.
- `ss test <modid>` generates rows of Storage Stacks containing every item of that namespace, over a sandstone floor, for reviewing render settings in the world. `ss test all` does the same for every loaded, non-disabled namespace at once; in a large modpack that is thousands of rendered block entities and is meant for deliberate review sessions. Generation writes directly into the world east of the player and replaces whatever blocks occupy the floor and stack positions.
- `ss write` makes the issuing player's client write its user override layer to `config/somestacks/item_overrides.json`. Only explicitly set entries are ever written; measured values never enter the file.

## Main extension points

- Interaction behavior: add or reorder a rule in `client/interaction/`, then add a packet when the result mutates server state. Rule order is semantic because only the first match runs.
- Stack invariants and persistence: the three block entities in `block/` are authoritative. Keep their NBT, update packet, collision cache, and renderer assumptions aligned.
- Spatial layout and targeting: `StorageCubeIdx`, `SinglesCubeIdx`, and `BarCubeIdx` are shared geometry contracts between rendering, selection, collision, grounding, and cross-block support.
- Ordinary item compatibility: prefer an override entry — authored in game with `ss item` and `ss write`, or edited into the bundled `item_render_overrides/` corpus — before changing the global rendering paths.
- Bar appearance: extend `textures/bars/` and reuse the base ingot/brick textures where tinting is sufficient.
- Client-visible configuration: extend `ConfigSyncPkt` as well as the server config; purely server-side controls need no client copy.
