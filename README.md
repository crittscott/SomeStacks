# Some Stacks

**Storage you can see.** Some Stacks turns the items you are holding into blocks made of those items. No screens, no recipes, no chests — hold a key, right-click, and what is in your hand becomes part of the world.

![Some Stacks](https://placeholder.invalid/somestacks/banner.png)

▶ **[Watch the one-minute tour](https://www.youtube.com/watch?v=REPLACE_ME)**

- **Minecraft** 1.20.1 · **Forge** 47+ · **required on both client and server**

---

## The three stacks

| | | |
| --- | --- | --- |
| **Storage Stack** | 27 item stacks in a 3×3×3 grid | The bulk option. A vertical run of them is one shared inventory that fills from the bottom, sorts itself, packs down, and shrinks when you empty it. |
| **Singles Stack** | 64 items, one per cell, in a 4×4×4 grid | The display case. Every item needs something under it, and pulling one out drops the rest of its column down a layer. Rotate individual items to line them up. |
| **Bar Stack** | 64 ingots as stacked bars | The vault. Eight alternating layers of eight bars, each resting on the ones below. Pull a bar out from underneath and everything it was holding up comes down. |

There are no block items and no recipes. A stack exists because you put something in it, and it goes away when you take everything back out.

## Getting started

1. Hold **V** (rebindable) and **right-click the air** to cycle the placement mode: Storage, Singles, Bar, Toggle Permanent.
2. Hold **V** and **right-click a block** with something in your hand. A stack of the selected type appears in the empty space you clicked toward, holding what you deposited.
3. Keep holding **V** and clicking to add more. The stack grows upward on its own when it fills.
4. **Right-click without V** to take an item back out.

That is the whole mod. Everything below is detail.

### Every gesture

| Gesture | Result |
| --- | --- |
| Hold `V`, right-click air | Cycle placement mode. Stack types the server has disabled are skipped. |
| Hold `V` + item, right-click a stack | Deposit into it, whatever mode is selected. |
| Hold `V` + item, right-click any other block | Deposit into an adjacent Singles or Bar Stack, or place the selected type in the empty space and make the first deposit. |
| Right-click a stack, no `V`, no Shift | Take from the nearest item you are looking at. Storage gives you as much as your hand will hold; Singles and Bar give one. |
| Select Toggle Permanent, hold `V`, right-click a Storage Stack empty-handed | Stop that pile from removing itself when emptied. |
| Shift + right-click a Storage or Singles Stack with a **redstone torch** | Rotate the whole block 90°. |
| Shift + right-click an item in a Singles Stack with a **soul torch** | Rotate just that item 90°. |

## Automation and redstone

Every stack block exposes a standard Forge item handler on **every side**, and a block in a vertical run exposes the **entire run** — so a hopper under the bottom and a pipe halfway up address the same inventory.

- Slots are positions, not a bag. A Singles or Bar slot names one cell and holds one item.
- A run advertises one block of headroom above what it holds, so **inserting into the top grows the column by itself** (up to the configured height, and only where it would be allowed to build).
- Automated extraction never leaves a mess: Singles shifts the column down, Bar backfills the hole from the top. Nothing gets dropped on the floor.
- **Comparators** read the fill of the whole run from any block in it.

## Server-friendly

Everything an admin would want to bound is bounded. Piles have a maximum height, stack types can be switched off, whole mods or single items can be barred from storage, and the render galleries are throttled per tick. Blocks that grow and remove themselves answer to spawn protection, the world border, and Forge's place/break events, so claim mods see the same events they would see from a player.

See **[Server administration](docs/server-admin.md)** for the config file and the `/ss` command.

## Documentation

- **[Player guide](docs/player-guide.md)** — every gesture, how each stack behaves, gravity and support rules
- **[Automation](docs/automation.md)** — item handlers, slot layout, comparators, growth
- **[Server administration](docs/server-admin.md)** — config settings and the full `/ss` command reference
- **[Pack authors](docs/pack-authors.md)** — data packs, resource packs, bar textures, and item render overrides
- **[Living specification](living-spec.md)** — the mod's behaviour in full, for the curious

## Compatibility

Some Stacks stores any item from any mod, and it does not need to know anything about that mod to do it. Items whose models don't sit well inside a cell are measured automatically and can be corrected by hand, by a resource pack, or by the server — see [Pack authors](docs/pack-authors.md).

It must be installed on both the client and the server; they negotiate a protocol version and refuse to connect on a mismatch.

## Issues and suggestions

Please report bugs and ideas on the [issue tracker](https://github.com/crittscott/SomeStacks/issues).

## License

All Rights Reserved. Please do not redistribute the jar or include it in a modpack without permission.
