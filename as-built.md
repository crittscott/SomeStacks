# Some Stacks As-Built Orientation

This is an orientation and maintenance guide to the code as it exists. It identifies the major
subsystems, persistent state, and design boundaries a maintainer should understand before making a
change. It is deliberately incomplete: `player-view-2.md` describes current observable behavior,
and the code is authoritative when either document is wrong.

## Project at a glance

Some Stacks is an unreleased Minecraft 1.20.1 mod for Forge 47.x and Java 17. Its mod id is
`somestacks`; its root package is `com.github.crittscott.somestacks`. The current project version is
1.1.1.

The mod registers three blocks and three block entity types. It registers no items: there are no
block items, recipes, creative tab entries, menus, or portable containers. A stack block enters the
world only through a custom player gesture, an item-handler insertion that grows an existing run,
or an administrator's render-gallery command. Breaking or emptying one never yields a block item.

| Registry name | Block entity | Per-block capacity | Role |
| --- | --- | ---: | --- |
| `storage_stack_block` | `stack_be` | 27 ordinary item stacks | Dense, sorted bulk storage |
| `singles_stack_block` | `singles_stack_be` | 64 single items | A supported 4 x 4 x 4 display grid |
| `bar_stack_block` | `bar_stack_be` | 64 single items | Eight supported, alternating layers of bars |

The mod is required on both client and server. Its SimpleChannel protocol is `2`, with an exact
version match on both sides. The project does not preserve obsolete prerelease data formats; change
the stored data and migrate existing development worlds rather than adding compatibility branches.

## Code map

| Area | Owns |
| --- | --- |
| `SomeStacks`, `ModRegistry` | Lifecycle wiring, server config registration, blocks, block entities, login sync, reload listeners, and commands |
| `ServerConfig`, `ServerOverridesLoader` | World server config, baked admission sets, ingot-tag resolution, and server render-override files |
| `block/*StackBlock` | Block state, waterlogging, shapes, comparator access, removal behavior, and scheduled publication ticks |
| `block/*StackBE` | Per-block inventory, persistent NBT, local extraction/deposit behavior, rotations, shapes, capabilities, and client updates |
| `StoragePile`, `SinglesColumn`, `BarColumn` | Maximal vertical runs, growth, run-wide automation, fill level, comparator output, and structural cleanup |
| `*ColumnHandler`, `PileItemHandler` | Forge `IItemHandler` views of whole runs |
| `StorageCubeIdx`, `SinglesCubeIdx`, `BarCubeIdx` | Slot geometry, ray targeting, rotations, support, seams, and collision boxes |
| `client/interaction` | Ordered recognition of the player's right-click gestures |
| `network` | Six mutation packets, three client/configuration packets, codecs, and common server validation |
| `server/Protection` | Vanilla/Forge interaction, placement, break, border, spawn, and obstruction checks |
| `server/RightClickBlockSuppressor`, `GestureThrottle` | Suppression of the displaced vanilla click and packet gesture pacing |
| `server/StackSoundData`, `StackSounds` | Data-pack-defined server-side action sounds |
| `client/*BER`, `CubeRenderHelper` | Block entity rendering for all three stack types |
| `client/ItemRenderOverrides`, `client/measure` | Layered render profiles, automatic model measurement, and the client cache |
| `client/BarTextureStore` | Resource-pack bar textures and authored or computed tints |
| `command` | `/ss`, render-profile authoring, list editing, and throttled gallery generation |
| `gametest`, `src/test` | Live-level behavioral tests and JVM unit tests; GameTest code is excluded from the release JAR |
| `resources` | Block states, models, textures, translations, compatibility profiles, sounds, and the ingot item tag |

The central distinction is between a block and its vertical run. A block entity owns and persists
only its local slots. Server operations resolve the maximal contiguous run of the same block type
and apply pile- or column-wide policy there. Block entities cache that resolution for the current
game tick; place and remove hooks invalidate affected caches immediately.

## Persistent world state

All inventory state is ordinary block entity NBT produced by `ItemStackHandler`.

| Block entity | Saved keys | Meaning |
| --- | --- | --- |
| Storage | `Items`, `Rotation`, `Permanent` | 27 stacks, block layout quarter-turn, and the pile's keep-empty-blocks mode |
| Singles | `Items`, `Rotation`, `CubeRotations` | 64 one-item cells, block layout quarter-turn, and 64 per-item quarter-turns |
| Bar | `Items` | 64 one-item bar positions |

Storage's base block is authoritative for permanence, although the value is propagated to every
block in the pile during settlement and inherited when a block joins the run. Storage and Singles
rotations are per block, not per run. A Singles item's rotation travels with that item when gravity
moves it, and rotations belonging to empty cells are cleared.

Each block state carries `waterlogged` and a derived integer `light` property from 0 through 15.
The light value is recomputed from local contents rather than saved separately in block entity NBT.
Runtime-only state includes cached run objects and voxel shapes, pending-publication flags, the last
published comparator signal, gesture marks, and throttles.

Block entities do not tick. Mutations schedule a block tick on the bottom of the affected run. That
deferred pass coalesces block entity synchronization, lighting, and comparator work; Storage also
uses it to consolidate and settle the pile.

## Admission rules

There are two admission layers, and they deliberately have different reach:

- `disable_mods` rejects a namespace in `isValidStorageItem`, `isValidSinglesItem`, and
  `isValidBarItem`. It therefore applies to player gestures and Forge capability insertion.
- `disable_items` is checked by `PlaceAndDepositPkt` and `DepositPkt`. It rejects player deposits,
  but it is not part of any handler's `isItemValid` and does not reject automation.

Storage otherwise accepts every nonempty item. Bar accepts items in the server's resolved ingot
set. Singles accepts the complement of Bar's rule, after the disabled-mod check. Changing the ingot
configuration therefore transfers an item's admission category between Singles and Bar. Existing
contents are never evicted, and internal gravity, settlement, and backfill paths deliberately move
stored items without reapplying a rule that may have changed since deposit.

`ServerConfig` resolves `ingot_tags` by walking every known item tag and matching each configured
entry as a whole-name glob where `*` means any run of characters. It publishes an immutable item
set and increments an ingot-generation counter. Resolution runs on config load/reload and whenever
item tags are rebound, so data-pack reloads take effect.

## Storage piles

A `StoragePile` is a maximal vertical run of Storage blocks. Every block exposes the same flat
inventory: 27 slots per existing block, numbered upward from the base.

Player deposits ignore the clicked cell and fill compatible partial stacks first, then empty slots,
from the base upward. If items remain, the pile grows one block at a time until the hand is empty or
growth fails. Capability insertion is positional: it answers only for the named slot, and a later
settle packs the result down. This distinction keeps the public handler's slot capacity truthful.

Settlement performs four operations:

1. Group every stored stack by exact item, damage, and NBT identity.
2. Re-cut each total into legal stack sizes.
3. Sort by registry id, damage, tag text, and descending count, then write from the base upward.
4. Propagate permanence and remove empty top blocks unless the pile is permanent or protection
   refuses removal.

A player's extraction is local to the rendered cell they targeted, but it schedules this run-wide
settlement. Breaking a Storage block drops that block's 27 local slots; the surviving runs above and
below are invalidated and scheduled independently.

## Singles columns

A `SinglesColumn` is a maximal vertical run of Singles blocks. One block is a 4 x 4 x 4 grid; a
handler slot names a physical cell and has a limit of one.

A cell above the local bottom layer needs the cell directly below it. A bottom-layer cell in the
lowest block is grounded by the world. At a same-type block seam, it needs the lower block's top cell
in the same visual column. The seam conversion accounts for each block's independent rotation.

Insertion never searches for another cell. It accepts the named empty, supported cell or refuses.
A position in the advertised headroom can grow the column and then receive its one item. This makes
an ascending capability walk build a supported structure one layer at a time.

Extraction shifts only the targeted visual column down by one layer. The move crosses block seams,
maps between differently rotated storage frames, and carries per-item rotations. Existing gaps are
preserved rather than compacted. Empty blocks are removed from the top downward; removal stops where
protection refuses it.

Breaking a Singles block drops that block's local contents. Unlike a Bar column, removal does not
run a support cascade through the blocks above.

## Bar columns

A `BarColumn` is a maximal vertical run of Bar blocks. Each block contains eight layers of eight
bars. Even and odd layers lie along opposite horizontal axes. A bar is supported when its footprint
overlaps at least one bar in the immediately lower layer; bottom-layer support crosses a same-type
block seam, while the lowest block stands on the world.

Player insertion places one item at the ray-selected supported position. Player extraction removes
one targeted bar and immediately walks upward, dropping every bar that has lost support. Breaking or
replacing a Bar block begins the same cascade above the removed block. Drops are grouped by exact
identity and block position to avoid spawning one entity per bar. A protection refusal can keep an
empty block standing, but it does not make that empty block support the bars above it.

Automation deliberately uses a different extraction rule. It removes the requested bar, then moves
the column's topmost bar into the hole. The vacated topmost position supported nothing and the hole
retains its previous support, so the structure remains standing and nothing drops. Insertion, like
Singles, is positional and accepts only an empty supported position.

## Forge item capability and redstone

Every block entity exposes `ForgeCapabilities.ITEM_HANDLER` from every side. From any block in a
run, the capability represents the entire run. Existing slots are followed by one block's worth of
headroom while the configured height permits growth: 27 slots for Storage or 64 for Singles/Bar.
The handler does not advertise the entire potential height.

Growth checks stack-type enablement, maximum height, build height, replaceability, final collision
against entities, world border and spawn protection, and Forge's placement event. Player-driven
Storage growth is attributed to that player; capability-driven growth uses the level's Minecraft
fake player. Simulations use the same side-effect-free feasibility checks as commits.

Capability mutations are guarded by `RunEdit`. A reentrant mutation triggered while another run
edit is in progress returns the normal refusal result: insertion keeps its stack and extraction
returns empty. Simulations remain available.

Every block in a run reports the same comparator value:

```text
0 when empty, otherwise floor(fill * 14) + 1
```

Storage fill is the sum of each slot's fraction of its legal maximum over all current slots. Singles
and Bar fill is occupied positions divided by current positions. Comparator neighbors along the
whole run are notified only when the resulting signal changes.

## Player gestures and networking

The client owns gesture recognition. `InteractionRuleRegistry` evaluates ordered rules and the first
match wins: permanence, block rotation, item rotation, deposit into the clicked stack, deposit into
an adjacent Singles/Bar stack, generic placement, then extraction. The selected placement mode is
client-only state; the server learns it only through the placement packet.

The channel registers these messages in positional id order:

| Direction | Packet | Purpose |
| --- | --- | --- |
| C2S | `PlaceAndDepositPkt` | Atomically place a selected stack type and make its first deposit |
| C2S | `DepositPkt` | Deposit into an existing stack, including one reached through an adjacent block |
| C2S | `TogglePermanentPkt` | Toggle a Storage pile's permanence |
| S2C | `ConfigSyncPkt` | Sync enabled types and server render overrides |
| S2C | `RenderOverridePkt` | Apply or reset one `/ss item` entry on one client |
| C2S | `RotateBlockPkt` | Rotate one Storage or Singles block |
| C2S | `RotateItemPkt` | Rotate one occupied Singles cell |
| C2S | `ExtractPkt` | Extract from one rendered cell |
| S2C | `WriteOverridesPkt` | Ask a client to write its user layer or generated profiles |

All mutation packets require a real sender, consume the player's one gesture attempt for the tick,
reject spectators, require a loaded target within block reach plus one block of slack, and then
validate their own hand, block, index, adjacency, and operation rules. The server recomputes deposit
targets and support from the player's current view rather than trusting a client-supplied slot.

Placement and first deposit are one transaction. Feasibility is checked before block placement, so
a rejected initial deposit does not leave an empty block or fire a placement event for a block that
is immediately rolled back. Creative deposits work from a copy of the hand stack.

## Protection and displaced vanilla clicks

The custom packets replace clicks the client suppresses locally. `Protection` reruns the relevant
vanilla and Forge gates on the server: world border, spawn protection, `RightClickBlock`,
`EntityPlaceEvent`, `BreakEvent`, build height, and entity obstruction.

A gesture also produces the normal vanilla use packet. After all protection events for the custom
operation pass, `RightClickBlockSuppressor` marks the actual clicked position for the current player
and tick. Its highest-priority event listener cancels the trailing vanilla click so a held item is
not also placed or used. Marks are made last because the protection consult itself fires the same
event.

When a gesture reaches a Singles or Bar block through the neighboring block it actually clicked,
both positions are checked. Item-spending deposits require both block access and item-use permission.
Automatic growth and self-removal use the level's fake player. Self-removal fires a BreakEvent; a
refusal leaves the empty block standing, and callers update their model of the run accordingly.

There is no claim-mod-specific integration. Compatibility comes from vanilla protection methods and
the Forge events above.

## Server configuration and commands

The Forge server config lives at `<world>/serverconfig/somestacks-server.toml`. It owns maximum run
height; three stack-type enable flags; disabled namespaces and item ids; ingot tag patterns; gallery
placement budget; and the gallery mod/item lists. The three enable flags and server render overrides
are synced on login and server-config reload. The other values remain server-side.

`/ss` is divided between runtime policy and render-authoring tools. Most subcommands require vanilla
permission level 2; galleries require level 3 and an in-game player because they overwrite their
floor and stack positions directly without placement protection. `/ss help` is ungated.

List-editing commands write through the Forge config value and immediately rebake the lookup sets.
`/ss reload` rereads only `config/somestacks/server_item_overrides/` and resyncs players; Forge owns
reload of the server TOML. Gallery generation is queued and spends at most
`placements_per_tick` block changes per server tick.

## Client presentation

All three blocks use block entity renderers. Storage and Singles delegate each occupied cell to
`CubeRenderHelper`; Bar draws fixed cuboids and does not use the stored item's model.

Storage/Singles item profiles have a mode (`2d`, `3d`, `gui`, or `block`), scale, and offset. The
first matching layer owns the full profile:

1. Server overrides synced from `config/somestacks/server_item_overrides/*.json`
2. User overrides from `config/somestacks/item_overrides.json`
3. Resource-pack overrides from `assets/<namespace>/item_render_overrides/*.json`
4. Automatic model measurement

An override with no scale uses 1 and one with no offset uses zero; only an omitted mode falls through
to measurement. Resource files are named for the item namespace they cover, and files for absent
namespaces are skipped. Scale is limited to 0.01 through 20; each offset component to -1 through 1.

Automatic measurement mirrors Forge item rendering in FIXED or GUI context, including render
passes, transforms, and custom renderers. Flat geometry uses `2d`; suitable three-dimensional models
are fitted into the cell. The result is cached in `config/somestacks/measured_cache.json`. A manual
resource reload clears it; changed resource-pack selections invalidate the file; changed mod
versions invalidate that namespace. The cache is saved on logout, game shutdown, and profile dump.

Bar appearance comes from `assets/<namespace>/textures/bars/*.json`. A mapping selects a texture and
may supply a final tint. Missing tints and unmapped items derive a tint from the item's sprite and
registered item color. Bar texture data is client-only and is not synchronized by the server.

## Data and resource extension points

- `data/<namespace>/tags/items/...` plus `ingot_tags` determine Bar admission.
- `data/<namespace>/somestacks_sounds/*.json` layers deposit, extraction, and applicable rotation
  sounds on the logical server. Unknown entries fall back to bundled vanilla wood sounds.
- `assets/<namespace>/item_render_overrides/*.json` configures Storage/Singles presentation.
- `assets/<namespace>/textures/bars/*.json` configures Bar textures and tints.
- `config/somestacks/server_item_overrides/*.json` imposes the highest-priority item profiles on
  connected clients.

The project ships 231 namespace-specific item-render override files. `generated_overrides` is output
only: nothing loads it as an active layer.

## Maintenance checklist

Before adding or changing a path, check the relevant invariants:

- Keep block-local persistence separate from run-wide behavior; resolve the maximal same-type run.
- Preserve the difference between Storage's bag-like settlement and Singles/Bar positional slots.
- Preserve Singles visual-column mapping and carry per-item rotations through gravity.
- Preserve the difference between player Bar collapse and automated Bar backfill.
- Do not revalidate existing contents when internal movement follows a changed admission policy.
- Keep `disable_mods` in all handlers and `disable_items` limited to player gesture packets unless
  intentionally changing that policy.
- Simulate growth against the same height, protection, replaceability, and obstruction constraints
  used by the commit.
- Attribute automation growth/removal to the level fake player and fire ordinary Forge place/break
  events.
- Claim the displaced vanilla click only after every protection consult has run.
- Validate every C2S packet independently; do not trust client slot selection or placement support.
- Batch mutations and retain the deferred bottom-of-run publication model.
- Keep comparator output run-wide and reserve signal 0 for a genuinely empty run.
- Synchronize all persistent render-affecting block entity fields to tracking clients.
- Keep override precedence and JSON bounds identical on disk, commands, and the wire.
- Keep packet registration order append-only unless intentionally changing protocol version.

The implementation intentionally has no item registry, recipes, creative tab, menus, loot tables,
portable stack state, or optional-side mode. The topical files under `docs/` provide shorter player,
automation, server-administration, and pack-author guides.
