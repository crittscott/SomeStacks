# Automation

Every stack block exposes loader-native storage on every side: Forge `IItemHandler` and Fabric Transfer API `Storage<ItemVariant>`. A block that is part of a vertical run exposes the **whole run**. A hopper under the bottom block and a pipe attached halfway up address the same inventory and see the same contents.

## Slots are positions

This is the one thing to understand before wiring anything up.

| Type | A slot is | Holds |
| --- | --- | --- |
| Storage | one ordinary inventory slot | up to a full item stack |
| Singles | one cell of the 4×4×4 grid | exactly one item |
| Bar | one bar position | exactly one item |

Slots are numbered from the bottom block of the run upward: 27 per Storage block, 64 per Singles or Bar block.

Because a slot names a *place*, Singles and Bar insertion **refuses** an empty position that nothing is holding up, rather than quietly putting the item somewhere else. A caller that walks the slot range from the bottom fills supported positions in order, which fills the structure layer by layer — each placement is standing by the time the next position is offered.

Forge simulations use the structure's real current occupancy. Fabric stages changes inside the caller's Transfer API transaction and applies structural edits only when its outer transaction commits. Aborted Fabric transactions leave the world unchanged.

## Growth

A run advertises the positions it holds **plus one block of headroom**, as long as the configured maximum height allows another block.

Inserting into that headroom grows the run by a block and stores the item there. Growth happens only if:

- the stack type is enabled in the server config,
- the run is below the maximum height,
- the target is inside build height, replaceable, and not obstructed by an entity,
- and the position passes the world border and spawn protection checks.

Automated growth is attributed to a loader-provided automation actor, which is never exempt from spawn protection. Forge additionally fires its block-place event, so claim mods using that hook can refuse growth. Fabric has no equivalent placement event for automation to trigger, so Fabric growth answers to the vanilla checks only — unlike Fabric player gestures and automated removal, which now fire Fabric API's own protection events (see [Server administration](server-admin.md)).

The advertised range is deliberately "what is there plus one block" — not the full potential height, which would leave a caller re-deriving a mostly empty range every tick, and not only what exists, which would mean a full run never gets offered the insertion that grows it.

## Extraction

Automated extraction leaves the structure standing and drops nothing:

- **Storage** — takes from the slot named; the pile packs down over the gap on the next tick.
- **Singles** — takes the item and shifts that visual column down one layer, exactly as a player's extraction does.
- **Bar** — takes the requested bar and moves the column's **topmost** bar into the hole. The topmost bar is holding nothing up and the vacated position keeps its own support, so the result always stands.

This is the one place automation and players differ: a player pulling a bar out lets everything above it fall.

On Fabric, Singles and Bar accept one structural extraction position per Transfer API transaction. A pipe requesting more should commit and retry, as Transfer API callers normally do when draining one-item views.

## Comparators

A comparator reads the fill of the **entire vertical run** from any block in it:

```
0 when empty, otherwise floor(fill × 14) + 1
```

- **Storage** fill is the sum of each slot's fraction of a full stack, over all slots in the pile.
- **Singles** and **Bar** fill is occupied positions over total positions.

Signal `0` therefore means *empty* and nothing else, which is what the usual emptiness circuit tests. Comparator neighbours are notified only when the run's value actually changes, so moving one item through a long column does not cost a run-length of block updates.

## Performance notes

Stack block entities **do not tick**. An inventory edit schedules one deferred pass on the bottom block of the affected run, and that pass publishes contents, light, and comparator changes for the whole run at once. A machine making hundreds of storage calls in a tick pays for one pass, not hundreds.

Two things follow that are worth designing around:

- A run's resolved shape is cached for the tick it was taken on, so a caller walking a long slot range does not re-walk the world per slot.
- A storage mutation that arrives **while another one is already running** is refused outright: the insertion keeps its stack, the extraction yields nothing. This only happens when a neighbour woken by the mod's own world edits reaches straight back into the same run mid-call. Both answers are ones every caller already handles; retry on the next tick.

## Wiring patterns

- **Hopper feeding a pile.** Point a hopper into the bottom Storage Stack. It fills the named slot, and the settle packs everything down a tick later. The pile grows upward on its own as it fills.
- **Filling a display wall.** Walk a Singles run's slots ascending and insert one item at a time; supported cells accept, unsupported ones refuse, and the structure builds from the bottom.
- **Ingot bank.** A Bar run with insertion at one end and extraction at the other stays dense in both directions — insertion fills the lowest supported positions, extraction backfills holes from the top.
- **Fill readout.** Put a comparator against any block of the run. You do not need to find the bottom.
