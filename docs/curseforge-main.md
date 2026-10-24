![Some Stacks](https://raw.githubusercontent.com/crittscott/SomeStacks/1.21.1/images/somestacks-splash.png)
![Loaders: Fabric Forge Neoforge Quilt](https://img.shields.io/badge/Loaders-Fabric%20Forge%20Neoforge%20Quilt-5c7c8a?style=for-the-badge) ![MC: 1.20.1, 1.21.1/4/8/11, 26.*](https://img.shields.io/badge/MC-1.20.1%2C%201.21.1%2F4%2F8%2F11%2C%2026.*-8a5a9b?style=for-the-badge)

# Some Stacks

For Minecraft **1.20.1, 1.21.1/4/8/11, and 26.***. Install the file matching your Minecraft version and loader; loader availability varies by Minecraft version.

**Storage you can see.** Why keep that gold hidden away in a chest? Can't remember which shulker box has all your granite? Put it all out where you can see it using Bar Stacks or Storage Stacks. Like the look of that Mekanism Digital Miner but can't fit it in your study? Place a small copy and rotate it as you like with a Singles Stack. Need just a little light? Add torches, for a light level of 3 each.

An extension and complete rewrite of [Stackable](https://www.curseforge.com/minecraft/mc-mods/stackable) by [KidsDontPlay](https://www.curseforge.com/members/kidsdontplay/projects) from the good old days of 1.12.2.

## The three stacks

| | | |
| --- | --- | --- |
| **Storage Stack** | 27 item stacks in a 3×3×3 grid | The bulk option. A vertical run of them is one shared inventory that fills from the bottom, sorts itself, packs down, and shrinks when you empty it. |
| **Singles Stack** | 64 items, one per cell, in a 4×4×4 grid | The display case. Every item needs something under it, and pulling one out drops the rest of its column down a layer. Rotate individual items to line them up. |
| **Bar Stack** | 64 ingots or bricks as stacked bars | The vault. Eight alternating layers of eight bars, each resting on the ones below. Pull a bar out from underneath and everything it was holding up comes down. |

No items and no recipes. A stack exists because you put something in it, and it goes away when you take everything back out. Storage piles can be set permanent for connection to inventory management systems.

## Getting started

1. Hold **V** and **right-click the air** to cycle the placement mode: Storage, Singles, Bar, Toggle Permanent.
2. Hold **V** and **right-click a block** with something in your hand to create a stack.
3. Keep holding **V** and clicking to add more. The stack grows upward on its own when it fills, to a server-config-determined maximum height. Singles and Bar stacks require you to choose the placement location.
4. **Right-click without V** to take an item back out.

## Details

### Every gesture

| Gesture | Result |
| --- | --- |
| Hold `V`, right-click air | Cycle placement mode. Stack types the server has disabled are skipped. |
| Hold `V` + item, right-click a stack | Deposit into it. |
| Hold `V` + item, right-click any other block | Deposit into an adjacent Singles or Bar Stack, or place the selected type in the empty space and make the first deposit. |
| Right-click a stack, no `V`, no Shift | Take from the nearest item you are looking at. Storage gives you as much as your hand will hold; Singles and Bar give one. |
| Select Toggle Permanent, hold `V`, right-click a Storage Stack empty-handed | Stop that pile from removing itself when emptied. |
| Shift + right-click a Storage or Singles Stack with a **redstone torch** | Rotate the whole block 90°. |
| Shift + right-click an item in a Singles Stack with a **soul torch** | Rotate just that item 90°. |

## Automation and redstone

Every stack block exposes loader-native item storage on every side. A block in a vertical run exposes the entire run, so a hopper under the bottom and a pipe halfway up address the same inventory. The native API depends on your Minecraft version and loader; see the matching automation guide below.

- Slots are positions. A Singles or Bar slot names one cell and holds one item.
- A run advertises one block of headroom above what it holds, so **inserting into the top grows the column by itself** (up to the configured height, and only where it would be allowed to build).
- Automated extraction never leaves a mess: Singles shifts the column down, Bar backfills the hole from the top. Nothing gets dropped on the floor.
- **Comparators** read the fill of the whole run from any block in it.

## Server-friendly

Piles have a maximum height, stack types can be switched off, whole mods can be barred from storage, and single items can be barred from player deposits. Structural edits respect build limits, obstruction, spawn protection, and the world border. Claim integration depends on Minecraft version and loader; see the matching server administration guide below.

## Documentation

Choose the documentation for your Minecraft version. Pack formats, loader APIs, protection integrations, and render controls vary by version.

| Minecraft | Player guide | Automation | Server administration | Pack authors |
| --- | --- | --- | --- | --- |
| 1.20.1 | [Guide](https://github.com/crittscott/SomeStacks/blob/1.20.1/docs/player-guide.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/1.20.1/docs/automation.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/1.20.1/docs/server-admin.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/1.20.1/docs/pack-authors.md) |
| 1.21.1 | [Guide](https://github.com/crittscott/SomeStacks/blob/1.21.1/docs/player-guide.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/1.21.1/docs/automation.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/1.21.1/docs/server-admin.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/1.21.1/docs/pack-authors.md) |
| 1.21.4 | [Guide](https://github.com/crittscott/SomeStacks/blob/1.21.4/docs/player-guide.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/1.21.4/docs/automation.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/1.21.4/docs/server-admin.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/1.21.4/docs/pack-authors.md) |
| 1.21.8 | [Guide](https://github.com/crittscott/SomeStacks/blob/1.21.8/docs/player-guide.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/1.21.8/docs/automation.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/1.21.8/docs/server-admin.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/1.21.8/docs/pack-authors.md) |
| 1.21.11 | [Guide](https://github.com/crittscott/SomeStacks/blob/1.21.11/docs/player-guide.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/1.21.11/docs/automation.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/1.21.11/docs/server-admin.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/1.21.11/docs/pack-authors.md) |
| 26.1 | [Guide](https://github.com/crittscott/SomeStacks/blob/26.1/docs/player-guide.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/26.1/docs/automation.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/26.1/docs/server-admin.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/26.1/docs/pack-authors.md) |
| 26.1.1 | [Guide](https://github.com/crittscott/SomeStacks/blob/26.1.1/docs/player-guide.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/26.1.1/docs/automation.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/26.1.1/docs/server-admin.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/26.1.1/docs/pack-authors.md) |
| 26.1.2 | [Guide](https://github.com/crittscott/SomeStacks/blob/26.1.2/docs/player-guide.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/26.1.2/docs/automation.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/26.1.2/docs/server-admin.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/26.1.2/docs/pack-authors.md) |
| 26.2 | [Guide](https://github.com/crittscott/SomeStacks/blob/26.2/docs/player-guide.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/26.2/docs/automation.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/26.2/docs/server-admin.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/26.2/docs/pack-authors.md) |
| 26.3 | [Guide](https://github.com/crittscott/SomeStacks/blob/26.3/docs/player-guide.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/26.3/docs/automation.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/26.3/docs/server-admin.md) | [Guide](https://github.com/crittscott/SomeStacks/blob/26.3/docs/pack-authors.md) |

## Compatibility

Some Stacks accepts items from other mods, subject to server policy and stack type. Items whose models do not sit well inside a cell are measured automatically and can be corrected with render overrides. See the matching pack author guide above for local, resource-pack, and server controls.

## Issues and suggestions

Please report bugs and ideas on the [issue tracker](https://github.com/crittscott/SomeStacks/issues).

## License

[GNU General Public License v3.0](https://github.com/crittscott/SomeStacks/blob/1.21.1/LICENSE). You are free to use this mod in modpacks, public or private, and to redistribute and modify it, provided derivative works carry the same license and make their source available.
