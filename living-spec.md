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

There is no continuously ticking block entity. Work happens in response to interaction, capability access, configuration events, or resource reloads. The one deferred piece of work is the Storage pile settle, which an edit schedules as a block tick so that a burst of edits collapses into a single pass.

## The three stack types

| Type | Stored contents | Visual/physical arrangement | Distinct behavior |
| --- | --- | --- | --- |
| Storage Stack | 27 ordinary item stacks per block | A rotatable 3 x 3 x 3 grid with gaps between cells | A vertical run of blocks is a pile, and the pile is the unit of storage. Deposits fill it from the base upward and grow it, bounded by a configured maximum height. It sorts, consolidates and packs down over its whole height. |
| Singles Stack | 64 items, one per slot | A rotatable 4 x 4 x 4 grid of touching quarter-block cells | Accepts non-ingot items. Each item must be supported by the cell below, counting the top layer of the Singles Stack underneath as the layer below the bottom one. Removing an item shifts every occupied cell above it down one position in the same column, drawing items down out of the Singles Stacks above so a vertical run behaves as one stack. A vertical run is a column, bounded by the same maximum height as a Storage pile. The whole grid and each individual item have independent quarter-turn rotations. |
| Bar Stack | 64 items, one per slot | Eight two-pixel-high layers of eight bars; successive layers alternate east-west and north-south | Accepts the items in the item tags named by the `ingot_tags` server config list. A bar must overlap at least one bar beneath it, counting the top layer of the Bar Stack below as the layer beneath the bottom one. A vertical run is a column, bounded by the same maximum height as a Storage pile. Player extraction drops every bar the removal leaves unsupported, up the whole column. Automation instead fills the lowest supported position and backfills a hole from the column's top, so it never drops anything. |

Storage accepts any nonempty item not excluded by the disabled-mod list. Singles uses the same rule but excludes items valid for Bar Stack. Player-driven deposits also reject item ids in the server's disabled-item list.

What a Bar Stack accepts is decided in one place, the block entity's item validity test, which every deposit, column insertion and capability path consults. That test reads a set of items resolved from the `ingot_tags` server config list: an entry names an item tag, and may carry `*` wildcards matching a run of any characters. The shipped default is `forge:ingots*` and `somestacks:ingots`. The wildcard matters because `#forge:ingots` is not the union of the `forge:ingots/<metal>` tags beneath it — a mod that adds its ingots only to the child tag, as many do, is invisible to the parent — so the pattern covers the parent and every child alike. `#somestacks:ingots` is the mod's own tag, which does not declare `replace`, so a data pack still extends what counts as a bar by adding values to it.

The list names tags but the test asks about items, so the list is resolved to an item set whenever it can have changed: on config load and reload, on an `ss ingot` edit, and on tag binding, since item tags are data pack state that a reload rewrites under a config that has not changed. Between those points the test is a single set lookup, which is what the automation paths want. An entry matching no tag at all is logged when the list is resolved.

Because the Singles rule is the complement of the Bar rule, an item added to the ingot set stops being depositable into Singles; contents already stored there extract normally, since validity gates insertion only. Bar contents are server-authoritative, so unlike bar appearance the rule is the same for every player.

All three block entities expose Forge's item-handler capability on every side, and in each case every block of a vertical run exposes the whole run as one inventory, so a hopper under the base and an interface halfway up address the same contents. All three keep their structure under automation: a Storage pile fills from its base upward, a Singles column fills its lowest supported cell, and a Bar column fills its lowest supported position and backfills holes from its top.

All three also report comparator output, and in each case it describes the whole vertical run rather than the one block, so a comparator reads the same value anywhere along it. All three convert fill the way a vanilla container does — `floor(fill * 14) + 1`, or `0` when the run is empty. The bottom of the range is reserved rather than proportional, so signal 0 means empty and nothing else, which is what the standard emptiness circuit tests; a proportional conversion would break that at these sizes, a full-height run being 216 slots for Storage and 512 for the other two. What differs is only how fill is measured, and it differs because the structures differ: a Storage slot holds a stack and contributes the fraction of one it carries, while a Singles cell and a Bar position each hold exactly one item, so for those two fill is occupancy. Full therefore means every cell or position occupied, which a column a player built sparsely never is.

Because the value belongs to the run, so does the notification. An edit changes what every block of the run answers, including blocks whose own contents it never touched — and those are exactly the blocks a comparator may be sitting against, so the whole run is told to read again through the comparator-aware update that also reaches a comparator standing one block further off behind a solid block. Each type publishes only when the value has changed, holding the last published value on the run's bottom block, which is what keeps this from costing a run's length in neighbour updates for every item moved. Storage publishes from its settle; Singles and Bar publish from the same per-block finalization that syncs contents and recomputes light, which a gravity cascade reaches once at its end rather than at each state it passes through. A run that gains or loses a block publishes too, since both change the height the fill is measured against, and a removal from the middle leaves two runs that each answer differently than the one did.

A block resolves the run it belongs to once a tick and holds the answer. Reading a run through the capability resolves it once per position walked, and a storage network's external storage walks the whole handler every tick, so the same walk was being repeated hundreds of times a tick for one attached machine. The held answer is dropped by the block's own place and remove hooks, which is where every structural change passes, including the growth and trimming the mod does itself; the tick it was taken on drops it for anything that edits the world without them. Measured against one external storage per type, this took the walks from 164, 258 and 514 a tick down to one each.

A run advertises the positions it holds plus one block's worth of headroom, while the configured height allows another block. Neither extreme serves: advertising the full permitted height leaves most of the range permanently empty, and a caller that walks it — a storage network's external storage polls its inventory every tick — spends the great majority of every scan on positions that cannot exist; advertising only what is there means the common insertion helpers offer a stack to each position and stop, so a full run would never be offered the insertion that grows it. One block is what a single insertion can grow, so the advertised range is reachable capacity and nothing more.

A simulated insertion promises only what the commit can deliver. Existing space is counted exactly. Growth is credited for the one position directly above the run, and no further: the block-place event a real growth fires cannot be consulted without firing it, so a plan reaching past that position would be guessing. Planning and committing share one predicate per type, so that position answers to everything else a placement does — the configured height, the type's enable flag, build height, replaceability, the world border and vanilla spawn protection — rather than to a subset of it. Spawn protection exempts operators, so the answer depends on who is growing the run: a player's own deposit is weighed against that player, and a capability insertion against the level's fake player, which is never exempt. One block holds a whole stack in any of the three, so a single insertion never needs a second and the limit costs nothing. A run that automation is filling still grows as far as it likes — one block per insertion, over as many insertions as it takes.

## Interaction model

`V` is the default binding of the stack-modifier key, rebindable under its own Some Stacks category in the controls screen. It is a held modifier, not an ordinary press-to-cycle key. Client Forge interaction events run through ordered rule lists; the first match wins. World changes occur only after a packet reaches the server.

| Gesture | Result |
| --- | --- |
| Hold `V` and right-click air | Cycle Storage, Singles, Bar, and Toggle Permanent modes, showing the selected mode in the action bar. The code does not require Shift. Synced-disabled Singles and Bar modes are skipped. |
| Hold `V`, hold an item, and right-click an existing stack | Deposit into the clicked stack, irrespective of the currently selected placement mode. Clicking the top face of a Singles or Bar Stack whose targeted column is full instead places the current mode's stack in the space above and makes the first deposit there, growing the column upward. |
| Hold `V`, hold an item, and right-click another block | If the adjacent block on the clicked face is a Singles or Bar Stack, deposit there. Otherwise place the selected stack type in the replaceable adjacent position and make the first deposit. A newly placed block is removed again if that deposit fails. |
| Right-click a stack without `V` or Shift | Ray-select the nearest occupied rendered cell and extract it. Storage takes as much of the selected item stack as the player's hand can accept; Singles and Bar take one item. The hand must be empty or contain the same item and tags with free capacity. |
| Select Toggle Permanent, hold `V`, use an empty hand, and right-click a Storage Stack | Toggle whether that pile's blocks may disappear automatically when empty. The mode belongs to the pile, so any block of it toggles all of them. |
| Shift-right-click a Storage or Singles Stack with a redstone torch | Rotate the entire stored layout by 90 degrees. Bar layouts have a fixed alternating orientation. |
| Shift-right-click an item in a Singles Stack with a soul torch | Rotate that individual rendered item by 90 degrees. |

Shift plus `V` is not a general placement gesture. Placement and deposit rules require that Shift not be held.

All three blocks consume a plain right-click in their own use handler, whether or not a gesture rule matched it. No held item can be used against the face of a stack block: a bucket does not empty there, a torch does not go up. Sneaking skips the block's handler, as it does for any block, so a sneaking click still places what is in hand — which is what the two torch rules are, and why they need the same-tick suppression described below.

The torch rules do not apply to Bar Stack: block rotation matches only Storage and Singles, and item rotation matches only Singles. A torch click on a Bar Stack matches no rule and falls through to vanilla, so the torch is placed against the block as usual. A torch click a rotation rule does match never places the torch, including one whose ray finds no item to turn.

A matched rule reports the click as consumed, not merely cancelled. A cancelled interaction that reports no result reads to the client as unhandled, and the client goes on to use the held item where the gesture was aimed — an item whose own use edits the world, a bucket above all, then places a fluid over the position the gesture is working on. Because that placement is a client prediction, it also swallows the block entity sync of a block the gesture has just created, leaving a stack the server has filled drawn empty until the next deposit.

Cancelling a rule's interaction on the client does not stop the client from sending the vanilla use-item-on packet, so a gesture that vanilla would resolve as a use of the held item has to be suppressed server-side as well. The mod's own packet arrives first and marks its position for same-tick right-click suppression, which cancels the vanilla event that follows. Two gestures need the mark:

- Extraction, because the newly held item would otherwise be used at a position whose stack block may just have disappeared.
- The two torch rotations, because a sneaking click holding an item skips the block's use handler and goes straight to placing the torch. The rotation packet is sent whenever the gesture matches, even when it turns nothing, so the mark is set either way.

Deposit and Toggle Permanent need no mark. Neither holds Shift with an item, so the block's own use handler consumes the interaction before the held item is reached.

### Cell targeting and support

Extraction traces only occupied cells and chooses the closest hit. Singles and Bar deposit traces every possible cell along the view ray and chooses the last empty cell before the first occupied cell, or the farthest intersected empty cell when no occupied cell is hit. The server recomputes deposit targeting from the player's current eye position and look direction.

Grounding is enforced for player deposits:

- A Singles item is grounded by the same column in the layer immediately below, which for the bottom layer means the top layer of the Singles Stack underneath, matched in visual columns. A bottom-layer item in a block that does not stand on another Singles Stack is grounded outright.
- A Bar is grounded when its horizontal footprint overlaps an occupied bar in the layer immediately below, which for the bottom layer means the top layer of the Bar Stack underneath. A bottom-layer bar in a block that does not stand on another Bar Stack is grounded outright.
- When a new Singles or Bar block is placed above an existing block of the same kind, its first item must also be supported by the top layer of the lower block.

## Server-side storage behavior

### Storage piles

A pile is a maximal contiguous vertical run of Storage Stacks, and it is the unit of storage. Every operation on a Storage Stack resolves the pile it belongs to and acts on the whole column. Piles are one block wide; a stack placed beside a pile is unrelated to it.

A pile may be at most `max_pile_height` blocks tall. That ceiling governs creation only: placement is refused when the resulting contiguous column would exceed it — tested over the runs both below and above the target, so a block dropped into the gap between two piles cannot join them into an over-tall one — and a pile stops growing there. A pile left over-tall by a lowered configuration keeps working and simply cannot grow.

A deposit fills the pile from its base upward regardless of which block or slot it was aimed at: compatible partial slots first, then empty slots. While items remain and the height allows, it adds a block on top and continues. Creating that block runs the same protection path as any placement — build height, world border, spawn protection, and Forge's block-place event — so a pile never grows above the world or across a protected boundary. Automation-driven growth carries no player, so it is attributed to the level's fake player and checked the same way.

Any edit — a player deposit or extraction, a capability insertion or extraction — marks the pile for settling and schedules a block tick on its base. Edits anywhere in the pile mark the same base, so a burst of automation traffic settles once rather than once per item moved. The settle:

1. Copies all stored stacks over the pile's whole height.
2. Totals them by exact identity — item, damage value, and tags — so compatible stacks always merge no matter where in the pile they sat.
3. Re-cuts each total into whole stacks plus at most one partial, then orders the result by item registry id, damage value, tags, and count with the fullest stack first.
4. Writes the result from lower blocks and lower slot indices upward, skipping slots that already hold exactly what they should, so an edit that disturbs a few stacks resyncs a few blocks rather than the whole column.
5. Propagates the base block's permanent flag to every block in the pile.
6. Removes empty, non-permanent blocks from the top until it reaches a nonempty or permanent block. Packing has already pushed every item as far down as it goes, so the empty blocks are exactly the run at the top and nothing below can be stranded by this.

Because the settle covers the pile's full height, items always occupy a contiguous run from the base and gaps cannot survive an edit.

The pile's permanent flag is a property of the pile, held on its base block and normalized by every settle. A block that joins a pile takes the pile's mode and rotation rather than imposing its own: one grown by a deposit inherits from the base, and one a player places inherits from the block below it, or from the block above when there is none below. Placing a block beneath a permanent pile therefore does not silently make the pile temporary. A permanent pile never shrinks, so it keeps whatever height it once grew to.

Rotation remains a property of the individual block. Because settling moves items between blocks, an item can come to be drawn under a different rotation than the one it was deposited under.

A Storage pile measures its fill the way a vanilla container does: each slot contributes the fraction of a full stack it holds, and the total is divided by the pile's slot count. So a pile of part-filled slots reads lower than the same items packed, which is what the settle is for.

The settle is where a pile publishes that value, comparing the pile's signal before and after, so a burst of automation traffic costs one notification rather than one per item moved.

### Singles gravity

A vertical run of Singles Stacks behaves as one stack. Singles removal closes the gap in one vertical column: every occupied cell above the removed position moves down exactly one layer, preserving its per-item rotation. This is a deterministic column shift, not a dropped-item cascade, and it is positional rather than a compaction, so a gap in a column survives it. A cell left empty carries no rotation, so the next item deposited into it starts unrotated.

The shift always vacates the top cell of its column, so the Singles Stack above can hand its own bottom-layer item down into the space. Exactly one item crosses each block boundary per removal, and the receiving cell is always free. The block that gave the item up then shifts the same column and draws from the block above it, and so on; the walk ends at the first block with nothing to hand down.

Two stacked blocks may carry different block rotations, so columns are matched between them in visual coordinates — the run the player sees as continuous. Block rotation turns a cell's position but never the item drawn in it, so an item's stored rotation carries its facing across a change of frame unchanged.

The bottom layer of a stacked block rests on the seam, the top layer of the Singles Stack below, recorded in visual columns so it means the same thing to a block above of any rotation. A block standing on the world rather than on another Singles Stack has its bottom layer grounded outright. The seam governs deposits as well: a bottom-layer item may only be placed where the block below supports it, on every deposit into a stacked block rather than only the first one that creates it.

A column may be at most `max_pile_height` blocks tall, the same ceiling Storage piles use. That ceiling governs creation only: placement is refused when the resulting contiguous column would exceed it, tested over the runs both below and above the target, and a column stops growing there.

### Singles columns under automation

A vertical run of Singles Stacks is one inventory to the item-handler capability, addressed from any block in it, with positions running from the bottom block's first cell upward. A slot index is a place in a structure rather than a place in a bag, so insertion does not take the requested slot at face value:

- **Insertion** ignores the requested slot and takes the lowest empty cell in the column that is already supported, adding a block on top when none is left and the height allows. A column automation builds is therefore filled layer by layer from the bottom, and a gap lower down is filled before anything higher. Growth carries no player, so it is attributed to the level's fake player and runs the same protection path as any placement. A block grown this way is unrotated, as a player's own placement is; the seam is read in visual columns, so a grown block's frame need not match the one beneath it.
- **Extraction** is the player's own removal unchanged: the cell is emptied and the column falls down over it, drawing items out of the Singles Stacks above and removing any block the walk empties.

Extraction needs no separate automated path because the shift drops nothing. Backfilling a hole from the column's top, which is what a Bar column does, would move an unrelated item down the column: Singles cells hold different items where a Bar column's are all the same.

### Bar gravity

A vertical run of Bar Stacks behaves as one stack. After one bar is extracted, the block scans the remaining bars from the bottom layer up. Any bar without an overlapping support footprint beneath it is removed and dropped into the world. One pass suffices per block: a layer is only ever supported by the layer beneath it, which the pass has already settled.

The bottom layer rests on the seam — the top layer of the Bar Stack directly below. Layer 7 runs north-south and layer 0 east-west, so the alternation continues across the block boundary and the ordinary footprint overlap describes support at the seam unchanged. A block standing on the world rather than on another Bar Stack has its bottom layer grounded outright.

When a settle changes a block's top layer, the cascade continues into the Bar Stack above, carrying that top layer as its seam. Support crosses the seam per footprint, so the block above loses only the bars whose support actually went away; bars still standing on a surviving column below are untouched. The walk stops at the first block whose top layer it leaves intact, because only the top layer can hold up the block above. A block emptied along the way removes itself and passes on its now-empty top layer, so pulling the base out of a pile collapses it the whole way up without that being a separate rule — nothing overlaps an empty seam.

The seam also governs deposits: a bottom-layer bar may only be placed where the Bar Stack below supports it. This holds for every deposit into a stacked block, not just the first one that creates it. Grounding is checked in one place, the block entity's deposit, which is the only caller holding the seam beneath it; the packet handlers target a cell and leave support to it.

Breaking or replacing a Bar Stack collapses the column above it. The block above has lost the seam it stood on, and an absent Bar Stack hands on an empty seam exactly as a block a cascade empties does, so pulling one out with a pickaxe and pulling one out by emptying it give the same answer. A block that a cascade is itself removing does not start a second collapse of the column that cascade is already walking.

A column may be at most `max_pile_height` blocks tall, the same ceiling Storage piles use. That ceiling governs creation only: placement is refused when the resulting contiguous column would exceed it, tested over the runs both below and above the target, and a column stops growing there.

### Bar columns under automation

A vertical run of Bar Stacks is one inventory to the item-handler capability, addressed from any block in it, with positions running from the bottom block's first cell upward. A slot index is a place in a structure rather than a place in a bag, so neither automated operation takes the slot at face value:

- **Insertion** ignores the requested slot and takes the lowest empty position in the column that is already supported, adding a block on top when none is left and the height allows. A column automation builds is therefore filled layer by layer from the bottom. Growth carries no player, so it is attributed to the level's fake player and runs the same protection path as any placement.
- **Extraction** takes the bar at the requested position and moves the column's topmost bar into the hole. The topmost bar holds nothing up, and a position vacated by extraction keeps the support it had, so the result always stands. When the hole is in a top layer, the backfill restores that layer's occupancy exactly, leaving the seam under the block above untouched. Blocks the column empties at its top are removed.

Both operations preserve density: a column whose bars occupy a contiguous run of positions still does afterwards, so automation neither leaves gaps among filled positions nor drops anything on the floor. A column a player built sparsely is not made worse, and insertion fills its lower gaps first.

This is deliberately not what a player's own extraction does. A player pulling a bar out lets go of whatever it was holding up; automation lifts a bar off the top to fill the gap.

### Cascade publication

Singles and Bar gravity, like Storage deposits and pile settling, suppress per-slot client sync while they run and publish once at the end: one content update to clients and one light-level recomputation for the whole cascade. The Storage path additionally tracks which blocks a batch actually changed and publishes only those.

### Empty and broken blocks

- Empty Singles and Bar blocks remove themselves after player extraction, including Bar blocks that a gravity cascade empties higher up a column, and Bar blocks that automated extraction empties at the top of one.
- An empty Singles block is not removed while another Singles Stack sits directly above it, matching the rule Storage piles use: severing a column there would strand the run above with nothing to fall onto. A gravity pass that removes a Singles block clears any empty blocks it was covering, so blocks kept back for that reason do not outlive their purpose. That rule governs the mod's own removals only: a player breaking a Singles Stack out of the middle of a column severs it, and the run above simply stands where it is. Nothing falls, because a block with no Singles Stack beneath it has its bottom layer grounded outright — the same rule that lets a column start on the world. Bar answers this case differently, and deliberately: see below.
- Empty Storage blocks are removed by the pile's settle, from the top down, stopping at the first nonempty or permanent block. An empty temporary pile removes itself entirely. Storage needs no never-remove-under-a-stack rule, because packing the whole pile leaves its empty blocks contiguous at the top.
- Breaking or replacing any stack block drops every item still in its local item handler.

## World state, collision, light, and persistence

The visible inventories are authoritative block entity state and are included in save NBT and block entity update packets.

- Storage persists items, block rotation, and the permanent flag.
- Singles persists items, block rotation, and all 64 per-item rotations.
- Bar persists items.

Storage retains the normal full-block shape. Singles and Bar compute and cache an outline/collision union from occupied cells, so their physical shapes match their contents. Both still expose a full-block interaction shape so the player can right-click the block reliably through gaps.

Content changes update the client and recalculate emitted light. Each occupied slot containing a `BlockItem` contributes one quarter of that block's default light emission, integer-truncated; contributions are summed and capped at 15. Item count within a Storage slot does not increase that slot's contribution.

## Networking and synchronization

The logical packet directions are:

- Client to server: place-and-deposit, deposit, extract, rotate block, rotate item, and toggle permanent.
- Server to client: configuration synchronization, user render-override set/reset, and the write-overrides request.

These directions are registered with the network channel, so a packet arriving from the wrong logical side is rejected before it is handled.

The server owns all inventory and block mutation and does not trust client gesture state. Every mutation packet first clears a common boundary: a real sender, a loaded target position, and a target within the player's interaction reach.

Every mutation packet then consults protection: the world border and vanilla spawn protection, which exempts operators, and Forge's right-click-block event, so claim and protection mods can veto the operation exactly as they would access to a vanilla container. That event has to be fired here because the mod's packets replace the vanilla interaction the client suppresses, which would otherwise be a mod's only chance to see it. Rotation and permanent-mode changes answer to this boundary as deposits do: they mutate a block someone else may own, and the client has already cancelled the interaction a claim mod would have caught.

The event describes the interaction that is really happening rather than a placeholder standing in for one: the hand the gesture used, and the face and point the player's own view ray meets against the block's full-cube interaction shape, computed server-side rather than taken from the client. A mod that asks only who and where is unaffected either way, but one that records what was clicked, or that treats the hands differently, is told the truth. A ray that no longer meets the block — a player who has turned away since sending the packet — falls back to the block's centre, since the operation has already cleared its own reach check and a stale aim is not a reason to refuse it.

A gesture that places a block names the block the player clicked, the one the new block comes to rest against, rather than the empty position being filled. That position has nothing for a ray to meet and is not what was interacted with; the clicked block is what a claim mod is being asked about, and it is also the block the place event is told the new one rests against, so the two events describe the same placement. Vanilla fires the interaction event ahead of the item's own use, so a placement answers to both hooks there as it does here.

Operations whose gesture requires a specific held item or an empty hand — rotate block, rotate item, toggle permanent — then verify that item state server-side rather than trusting the client. Placement additionally checks that the target is replaceable, checks the player's permission to use the item there, fires Forge's block-place event so protection mods can veto or record it, enforces the selected block's enable flag, enforces the maximum pile height for Storage, validates blacklists, and removes a just-created block if its initial deposit fails. Deposit recomputes cell targeting and support. Extraction accepts the client-selected slot index, then validates the block entity, index, contents, and hand compatibility before changing state. Item rotation likewise ignores an index naming a cell that is empty by the time it arrives, since an empty cell carries no orientation to turn.

On player login and server-config reload, the server sends clients the three block-enable flags and the admin render overrides read from `config/somestacks/server_item_overrides/`. The client uses the flags for mode selection and the overrides as its top render layer. Blacklists and pile settings stay server-side.

## Rendering architecture

All stack blocks use `ENTITYBLOCK_ANIMATED` and are drawn by block entity renderers rather than ordinary world block models.

- Storage and Singles render each stored item through `CubeRenderHelper` inside the cell selected by their spatial-index utility. Block rotation changes the visual cell coordinates and ray-hit coordinates together. Singles per-item rotation turns the model within its existing cell and does not alter occupancy or collision.
- Bar does not render the original item model. It emits a fixed six-face cuboid for each bar using the configured texture and tint for that item.

### Item render modes

`CubeRenderHelper` has four modes:

| Mode | Behavior |
| --- | --- |
| `2d` | Draw a small `stack_cube` background and project the item's baked quads onto the cell faces the camera can see, applying item tint. Only the quads lying in the art's own plane are projected; the slivers an item model hangs off its outline are edge-on once flattened and are skipped, so a model that carries its art on some other plane draws nothing here. Each face carries the same layout under a proper rotation, so the art reads the same way round from every side, and it is lit by the normal of the face it lies on rather than the direction of the quad it came from. |
| `3d` | Use the normal item renderer in `FIXED` display context. |
| `gui` | Use the normal item renderer in `GUI` context with counter-rotation to fit the stack cell. |
| `block` | Render a `BlockItem`'s default block state directly. Only a block with an ordinary block model is drawn this way; one drawn by a block entity renderer instead falls back to `3d`, which reaches that renderer with the real stack and the `FIXED` transform rather than with a throwaway stack, and which cannot leave the shared pose stack unbalanced if it throws. A block model that throws also falls back to `3d`, and is recorded and logged the first time so the attempt is made once per bake rather than once per frame; the record is dropped on resource reload with the rest of the geometry gathered from those models. A non-`BlockItem` configured as `block` draws nothing. |

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

`mode` is one of `2d`, `3d`, `block`, or `gui`; `scale` is a number from `0.01` to `20`; `offset` has exactly three numbers, each from `-1` to `1`. For `2d`, only the x and y offset components are used.

The numeric bounds are the schema's, so every source of an override answers to them: the bundled corpus, a resource pack, a server's admin folder, the user's own file, the measured cache, and the `ss item` command. The scale range is the range measurement fits a model into, so a measured profile and an authored one are bounded alike and nothing the mod produces can fail its own parse. A field outside its range is reported and dropped, as a malformed one is, leaving the entry to take that field's default. The command declares the same bounds on its arguments, so an out-of-range value there is a parse error naming the offending word rather than a setting that reaches the render transform.

Fields an entry omits take plain defaults — scale `1` and zero offset — rather than measured values, because scale and offset mean different things from one mode to the next and a measured scale is only valid for the mode it was measured for. An omitted mode is the sole exception and is measured. Malformed entries and fields are logged and skipped.

A multiplayer admin makes overrides authoritative for all players by copying a client-written override file into the server's `server_item_overrides` folder verbatim; no format translation is involved.

### Bar texture data

Client resource reload also loads `assets/*/textures/bars/*.json`. Every namespace is scanned, so a resource pack adds or replaces mappings under its own namespace without shadowing the bundled file. Files are read in resource location order and later files win on duplicate items, as in the server override layer. Bar appearance is therefore ordinary client-side resource state: it is not synced, has no server-authoritative layer as the item render overrides do, and players running different packs see different bars. Because the auto-tint below reads the client's own sprites, a pack that retextures an ingot recolors its bars to match. A mapping may be a texture id string or an object with `texture` and optional `tint` fields. A mapped item without an explicit tint is auto-tinted from the two things that together make up an item's drawn color: the average of pixels with alpha greater than 127 in the particle sprite of the item's baked model, brightened ten percent toward white, multiplied by the color the item's own mod registers with `ItemColors` for the primary layer. Either half is white when the item does not use it, so a plainly textured item is tinted by its texture alone. An item whose model carries no texture at all, which is what a custom renderer's item looks like from the outside, has a particle that resolves to the missing texture; its registered color is then the only description of its color there is, and it is used alone. This is what makes a mod that draws every one of its ingots from one grayscale texture and separates them purely by registered tint come out as bars of the right colors rather than as one repeated shade. A mapping with `tint` uses the supplied RGB or ARGB hex color. An unmapped item gets the same treatment as a mapping with no explicit tint — the base ingot texture, auto-tinted — computed on first render rather than at reload and held until the next one, since the set of items that will need it is not known in advance. An item whose tint cannot be computed at all is drawn untinted.

### Sound data

`data/*/somestacks_sounds/*.json` maps each stack type's `deposit` and `extract` actions to registered sound ids. It is data pack rather than resource pack state, loaded on the logical server, which is the side that plays and broadcasts the sounds; a data pack reload replaces the six mutable runtime sound choices. Files are merged in resource location order and later files win on duplicate keys. Missing or invalid entries fall back to vanilla wood-place or wool-break sounds. The bundled configuration currently selects vanilla wood sounds.

## Server configuration

The Forge server config contains:

| Setting | Default | Operating effect |
| --- | --- | --- |
| Maximum pile height | 8 | Blocks in one vertical Storage pile, Singles column or Bar column. Placement producing a taller column is refused and a pile or column stops growing there. Bounds the work of settling a pile and of walking a column, both of which cover the whole height. |
| Enable Storage / Singles / Bar | `true` | Prevents new placement of the disabled type. Storage and Bar also stop growing their columns. Existing blocks remain present and their direct deposit/extract paths remain usable. |
| Disabled mods | `spartanfire`, `spartanweaponry` | Rejects new contents from those namespaces; existing contents can still be extracted. |
| Disabled items | empty | Rejects those ids on player packet deposit paths; existing contents can still be extracted. |
| Ingot tags | `forge:ingots*`, `somestacks:ingots` | Item tags whose contents a Bar Stack accepts, and by complement what a Singles Stack refuses. Entries may carry `*` wildcards. Resolved to an item set on config change and on tag binding. |
| `ss` command allow list | empty | Player names permitted to use the `ss` command's render subcommands. Empty means no one may use them. |
| Test wall placements per tick | 64 | Blocks `ss test` and `ss testingot` place per tick, counting both stacks and floor. A wall spanning every loaded mod is tens of thousands of placements; lower values spread it over more ticks. This is the only throttle on the most expensive thing the mod can be asked to do. |
| Gen mods | empty | Namespaces the `list` form of the two test-wall commands builds from, in the order given, which is the order of the wall's columns. Read on each invocation rather than baked, so an edit applies to the next wall. |
| Gen items | empty | Items the `items` form of `ss test` builds a row from. Read on each invocation and sorted at use, so the stored order does not matter and an edit applies to the next row. |

The six text lists are also editable in game by an operator, through `ss allow`, `ss gen`, `ss deny` and `ss ingot`. Those commands set the config value, which writes through to the config file, and re-bake the lookup sets, so the file and the running server cannot come to disagree and a later save cannot undo the change. Every consumer reads the baked sets on demand, so an edit takes effect at the next lookup with nothing to invalidate. An edit made to the file directly is applied when Forge reports the config reloaded, which re-bakes the lists and re-syncs every player; the mod has no way to re-read the file on demand, so `ss reload` does not attempt it.

The client mode cycler skips synced-disabled Singles and Bar modes. Storage remains in the client cycle even when disabled, but the server still refuses its placement.

## Render measurement

The client can measure the geometry an item actually renders in the `FIXED` display context and derive a complete render profile in the standard override vocabulary (`mode`, `scale`, `offset`). Measurement supplies the presentation of every item that no override layer mentions — the weird mod the user has that the bundled corpus has never seen — and the mode of any entry that omits one.

- Non-custom baked models are measured by resolving item overrides, applying the `FIXED` display transform (honoring model substitution) and the item renderer's origin shift, then accumulating the bounds of every render-pass quad. Flatness is decided by the model's own `isGui3d()`: a generated item sprite reports false and yields `2d`. Geometry whose thinnest axis is a small fraction of its longest also yields `2d`, catching models that claim depth but draw none. Volumetric models yield `3d` with a uniform scale fitting the fixed target fill fraction (0.9) of a stack cell and an offset that recenters the measured bounds. Fitting sizes every model to the same cell regardless of its real-world size, so block families that read too large against their neighbours carry a per-family scale factor applied on top of the fit; blocks deriving from `ButtonBlock` are fitted to three quarters. That factor list is an extension point alongside the presentation list below.
- Blocks deriving from `BasePressurePlateBlock` or `CarpetBlock` carry their art on the horizontal plane, which a flat projection reduces to a one-pixel edge. They are measured in the `GUI` display context behind that path's counter-rotation and yield `gui`, the inventory-slot presentation. This is the only case where measurement selects `gui`, and the list is the extension point for other horizontal-art block types.
- Custom-renderer (BEWLR) items are probed once by running their renderer against a vertex-capturing buffer. A thrown exception or an empty capture falls back to `2d` and logs the failure. Because measured scales describe true drawn size, no custom-renderer scale correction is applied anywhere in the render path.
- Profiles are cached per item and persisted to `config/somestacks/measured_cache.json` on the client, so an item is measured once ever rather than once per session. A measurement describes a baked model and is good only for as long as that model is, so the cache answers to all three things that can change one. A mod update is caught by the per-namespace mod versions the file records: entries from a namespace whose version changed are dropped and re-measured. A manual resource reload discards the cache entirely. A change of resource packs between sessions is caught by the enabled pack list, also recorded in the file, and a cache written under a different set is dropped whole — the reload that runs at startup is the ordinary one and cannot be allowed to throw the cache away, so it is the recorded list rather than the reload that notices.

The bundled `item_render_overrides/` corpus was generated by measuring every namespace with `ss write all` and hand-correcting the results in place. Because its entries are complete, editing one field of an entry leaves the others fixed rather than reverting them to measurement.

## The `ss` command

The `ss` command has two halves under one root, gated differently because they answer to different authorities, and a help subcommand over both that is gated by nothing.

The render subcommands act on the issuing player's own view, so they are player-only and gated by the server config's `ss_command_allowlist`: a player may use them only if their name is on that list. The list is empty by default, so no one — including the single player of a single-player world — can use them until a name is added; an attempt by a non-listed player fails with the exact operator command that would add them. They are the user-facing tool for correcting the rendering of items the bundled corpus does not cover, or covers wrongly:

- `ss item <item> <mode> [<scale> [<x> <y> [<z>]]]` sets the entry for that item in the issuing player's user override layer. The change renders immediately but lives only in memory until written. Only the mode is required: arguments left off take the same defaults a JSON entry omitting those fields takes, scale `1` and zero offset, so `ss item <item> gui` is the whole command for an item that needs nothing else. Scale and offset take the schema's ranges, so a value the override format would reject cannot be set from in game either. The entry the command sets is always complete, whichever form set it, so a written file records all three fields.
- `ss item <item> reset` removes that entry; the item returns to server, built-in, or measured behavior. Because the layers below are never modified, reset always restores original behavior, not a previous tweak.
- `ss write changed` makes the issuing player's client write its user override layer to `config/somestacks/item_overrides.json`. Only explicitly set entries are ever written; measured values never enter the file.
- `ss write <modid|all|list>` dumps a complete profile — mode, scale, and offset, resolved through every layer — for each item of those namespaces to `config/somestacks/generated_overrides/<namespace>.json`, one file per namespace. Namespaces are selected exactly as the test walls select them, so a review session can dump what it just looked at. The dump has no `items` form to match the wall's: it writes a file per namespace, and the way to record one item is `ss item` and `ss write changed`. This is how the bundled corpus is produced: the folder is a destination and never a layer, so nothing reads it back, and a file is ready to be hand-corrected and dropped into a resource pack's `item_render_overrides` or a server's `server_item_overrides` as it stands. Writing a whole pack into the user layer instead would freeze that pack's presentation there, above the corpus, where a later corpus or measurement change could never reach it and `ss item ... reset` would no longer restore original behavior.
- The dump measures every item no layer configures, so it is the measurement pass for a modpack rather than one item at a time. It runs on the issuing player's client, which is busy for as long as it takes, and it saves the measured cache when it finishes rather than waiting for logout. That cost is the reason to name a namespace, or to keep a review session's namespaces in `gen_mods` and dump `list`, rather than reaching for `all` in a large pack.

The administrative subcommands act on the server rather than on a view, and they are gated by operator permission level rather than by the allow list. That gate is what makes an empty allow list recoverable: a gate that consulted the list could never be opened from in game, since the list starts empty. Most need no player and run from the server console as well as in game. The two wall generators are the exception: they need a player, because a wall is built where the sender stands.

A wall generator sits on this side of the line rather than with the render commands because it does not act on a view. It overwrites a region of the world outright — the floor and every stack position — without the world border, spawn protection, permission and block-place consults a placement gesture answers to, and a wall spanning a large pack runs to a few hundred blocks across and as many rows as the biggest mod has items. Handing that to a player so they could tune how items are drawn for themselves would make the allow list a world-editing permission by side effect, which is not what it is for.

- `ss test <modid>` generates rows of Storage Stacks containing every item of that namespace, over a sandstone floor, for reviewing render settings in the world. `ss test all` does the same for every loaded, non-disabled namespace at once; in a large modpack that is thousands of rendered block entities and is meant for deliberate review sessions. `ss test list` builds the namespaces named by the `gen_mods` config list, which is the middle ground: the handful of mods a review session is actually about, kept between sessions. Generation writes directly into the world east of the player and replaces whatever blocks occupy the floor and stack positions.
- `ss test items` builds one row from the items named by the `gen_items` config list, for reviewing a handful of items from across a pack rather than a namespace at a time. The row is sorted by mod id and then item name, so the list itself may be kept in any order, and it is filled a stack at a time and run on north exactly as a namespace with many items is. There is no ingot counterpart: a pack has few enough ingots to review a namespace at a time.
- `ss testingot <modid|all|list>` is the same generator over Bar Stacks, for reviewing bar textures and tints. It covers only the items a Bar Stack accepts, and its namespace list and completions only offer namespaces that have one. Each block shows one bar per ingot, up to the eight of a single layer, and a namespace with more ingots than that runs on into further blocks down the row, exactly as a namespace with more than nine items does under `ss test`.
- Naming a single namespace fails when that namespace is disabled or has nothing to show, because the command names one thing and it did not happen. `all`, `list` and `items` instead skip such an entry and report it, so one bad entry does not withhold the rest of the wall. The two reasons are reported apart: an entry the server disabled is doing what the server was told, while an unloaded one — a namespace holding none of that kind's items, or an item the registry does not know — is usually a typo, or, in the mod list, an entry meant for the other kind, since that one list serves both wall kinds.
- `ss allow add|remove|list` edits `ss_command_allowlist`. Adding completes over the players currently online, because a name is usually added before its owner has needed it; removing completes over the list itself.
- `ss gen mod add|remove|list` edits `gen_mods` and `ss gen item add|remove|list` edits `gen_items`. They edit server config, and the walls they feed are operator commands too, so the whole of that path answers to one authority. Adding a namespace completes over every namespace that has items, not over one kind's, since the one list serves both `ss test list` and `ss testingot list`. The mod list alone is reported in stored order rather than sorted, because that order is the order of the wall's columns; the item list is sorted at use, so its stored order carries no meaning. An item id is checked against the registry when it is added, as the disabled-item list checks one, because a typo would sit in the list being skipped by every row.
- `ss deny mod add|remove|list` edits the disabled-mod list. A namespace is stored as typed without being checked against the loaded mods, because the shipped defaults name mods that need not be installed.
- `ss deny item add|remove|list` edits the disabled-item list. An item id is checked against the registry, because that list is consulted by exact id and a typo would sit in it looking effective.
- `ss ingot add|remove|list` edits the ingot tag list. An entry is read as a greedy string, since neither a wildcard nor a colon survives the word parser, and is stored as typed without being checked against the loaded tags: it may legitimately name a tag no data pack has declared, or match one by wildcard. What a typo would cost is covered where it shows instead — an edit reports how many items the list now accepts, and an entry matching no tag is logged when the list is resolved. Adding completes over the item tags the server knows.
- `ss reload` re-reads the server override directory and pushes the current server config to every player. It does not re-read the server config file.

Entries are stored as typed and matched without regard to case, which is the comparison the baked lookup sets use. Adding an entry the list already holds, or removing one it does not, fails with a message saying so rather than reporting a change that did not happen.

`ss help` lists every top-level subcommand with a one-line summary, and `ss help <command>` gives one of them its forms, its gate, and what it does. It has one entry per top-level subcommand and is the in-game counterpart of this section, so a subcommand added here needs an entry there.

Help is gated by nothing at all, unlike either half it describes. The player who cannot run a subcommand is the one most likely to be reading about it, and because each entry names the gate its subcommand answers to, being told what a command needs is also the answer to why it was refused. It changes nothing and answers the sender alone rather than broadcasting to the other operators.

## Automated verification

The automated suite has two deliberately separate layers, 47 JUnit tests and 63 Forge GameTests.

JUnit tests live under `src/test/java` and run as part of `build`. They cover logic that can execute without a bootstrapped game: coordinate and rotation invariants, support calculations over occupancy snapshots, packet codecs, render-mode cycling, override JSON parsing, bundled resource integrity, command registration, and server-config lifecycle behavior. A plain JUnit fixture must not touch `Items`, `Blocks`, or another entry point that initializes the vanilla registries; Gradle's test JVM has the mod classes but is not a running Minecraft instance.

Tests that need registered items or blocks, a level, block entities, capabilities, Forge events, or packet handling are Forge GameTests. Most live under `gametest/`, with packet boundary tests beside the packets they exercise. They cover pile and column behavior, growth, gravity and support, capability semantics, persistence and synchronization, protection events, stack sorting, test-wall generation, item-stack utility behavior, and server packet rejection. Extraction is the one mutation packet that acts on a cell the client chose, so its per-type handlers are exercised directly against indices and hand states no gesture could produce. Conservation across the internal relocations — the Singles shift and draw-down, and the Bar backfill — is covered by seeding a cell with an item that block would refuse from a deposit, which is the state a narrowed validity rule leaves behind. Growth answering to protection is staged with the world border, which a test moves off the structure and restores within one synchronous call; spawn protection, the other half of the same predicate, is implemented on `DedicatedServer` and cannot be reached from the test server at all. `runGameTestServer` boots Minecraft, enables the `somestacks` test namespace, runs all 63 required tests, and exits.

Every GameTest uses the same empty structure. `generateGameTestTemplate` decodes that structure from the `somestacks_empty_template_base64` Gradle property into generated resources before resource processing. GameTest classes, packet boundary test classes, and the generated structure are available to development runs but excluded from the release jar.

The two commands are complementary: `build` runs JUnit but does not execute GameTests, while `runGameTestServer` executes GameTests but not JUnit. A behavior that crosses both pure logic and game state should be divided at that boundary rather than bootstrapping Minecraft inside JUnit.

## Main extension points

- Interaction behavior: add or reorder a rule in `client/interaction/`, then add a packet when the result mutates server state. Rule order is semantic because only the first match runs.
- Stack invariants and persistence: the three block entities in `block/` are authoritative. Keep their NBT, update packet, collision cache, and renderer assumptions aligned.
- Storage pile behavior: `StoragePile` owns pile resolution, bottom-up filling, growth, the capability's flat slot range, settling, and trimming. A Storage Stack that needs to reach past its own 27 slots resolves a pile rather than walking the column itself.
- Singles column behavior: `SinglesColumn` owns column resolution, supported-cell placement, growth, comparator output and its publication, and the capability's flat slot range. Extraction routes back through the block entity's own removal, so the column has one gravity implementation rather than a player one and an automated one.
- Bar column behavior: `BarColumn` owns column resolution, supported-position placement, growth, comparator output and its publication, the capability's flat slot range, and the backfilling extraction. `BarCubeIdx` reasons over occupancy snapshots, so a placement can be weighed before it happens and a block can be settled in place.
- Spatial layout and targeting: `StorageCubeIdx`, `SinglesCubeIdx`, and `BarCubeIdx` are shared geometry contracts. `StorageCubeIdx` serves rendering and selection, which is all a full-block Storage Stack needs; `SinglesCubeIdx` and `BarCubeIdx` additionally serve collision, grounding, and cross-block support.
- Ordinary item compatibility: prefer an override entry — authored in game with `ss item` and `ss write`, or edited into the bundled `item_render_overrides/` corpus — before changing the global rendering paths.
- Bar appearance: extend `textures/bars/` and reuse the base ingot/brick textures where tinting is sufficient. A resource pack is the supported route for a pack author who wants a whole set of bars to look a particular way, and the only route to a consistent look across players, since nothing about bar appearance is synced. Adding a bar item needs no appearance work at all: an unmapped item is auto-tinted from its own sprite, and a mapping is worth adding only where that reads wrong.
- What a Bar Stack accepts: name another item tag in the `ingot_tags` server config list, or add values to the `#somestacks:ingots` item tag from a data pack. Keep the validity test the single decision point rather than resolving tags at a call site, and remember that widening it narrows Singles by the same amount. A caller that groups or filters items by ingot-ness caches against the config's ingot generation counter, so a data pack reload and a list edit both drop that grouping.
- Client-visible configuration: extend `ConfigSyncPkt` as well as the server config; purely server-side controls need no client copy.
- Automated verification: keep registry-independent contracts in JUnit and game-backed contracts in Forge GameTests. Add a test to the layer that owns the behavior; do not make the plain test JVM impersonate a Minecraft bootstrap.
