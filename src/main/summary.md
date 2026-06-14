# SomeStacks — Codebase Summary

## What It Is

A Minecraft 1.20.1 Forge mod providing three specialized block-based item storage containers. Items are stored inside the block and rendered visually in 3D as a grid of items floating inside the block space. 118 mod-compatibility JSON files tune how items from other mods render.

---

## The Three Blocks

| Block | Slots | Grid | Special Mechanic |
|-------|-------|------|------------------|
| **Storage Stack** | 27 | 3×3×3 | Stacks vertically into "piles"; auto-consolidates; `resortAndPackPile()` |
| **Singles Stack** | 64 | 4×4×4 | One item per slot; gravity — removing an item cascades items above down |
| **Bar Stack** | 64 | 8 layers | Ingots only (`#somestacks:ingots`); alternating EW/NS layers; bars need support below |

---

## Package Layout

```
com.github.crittscott.somestacks/
├── block/                    3 Block classes + 3 BlockEntity classes + PileItemHandler
├── client/
│   ├── (renderers, events, key mappings, render overrides, sound config)
│   └── interaction/          ~12 rules + registry + context object
├── command/                  Debug/test commands
├── network/                  8 packet types + ModNetworking channel
├── server/                   RightClickBlockSuppressor
└── util/                     ItemOps, StackSort, BlockType, StackMode, 3 CubeIdx classes
```

---

## Core Architecture Patterns

**Server-authoritative / config-synced.** All block entity logic runs server-side. On login the server pushes `ConfigSyncPkt` to clients (feature flags, render overrides). There are no client-only state decisions about items.

**Client → Server via 8 packets.** Click events on the client are processed by `InteractionRuleRegistry` (rule-based dispatch), which fires packets:
- `PlaceAndDepositPkt`, `DepositPkt`, `ExtractPkt`
- `RotateBlockPkt`, `RotateItemPkt`
- `TogglePermanentPkt`
- `ConfigSyncPkt`, `RenderOverridePkt` (server→client)

**Spatial indexing per block type.** Each block has a `*CubeIdx` utility class that maps slot index ↔ 3D visual position, handles block rotation, and provides ray-tracing for click-to-slot targeting.
- `StorageCubeIdx`: 3×3×3, 1px margins, 4-way block rotation
- `SinglesCubeIdx`: 4×4×4, per-item cube rotation + `traceCubes()`
- `BarCubeIdx`: Alternating EW/NS layers, footprint overlap for gravity

**Extensible interaction rules.** `client/interaction/` contains ~12 `InteractionRule` implementations wired in a registry. Adding new click behavior = new rule class, no changes elsewhere.

**Item render overrides.** `ItemRenderOverrides` loads all 118 JSON files at startup. Lookup order: server overrides → JSON config → defaults. Each entry has optional `mode` (2d/3d/block/gui), `scale`, and `offset[3]`.

---

## Server Config

- Per-block feature toggles (storage/singles/bar)
- `PILE_SORT_COOLDOWN_TICKS` / `PILE_SORT_MAX_STACKS` — throttle consolidation
- `DISABLE_MODS` / `DISABLE_ITEMS` — item blacklists
- `RENDER_MODE_OVERRIDES` — server-pushed render corrections (prevents client crashes)

---

## Resources Summary

```
assets/somestacks/
├── blockstates/          3 files
├── models/block|item/    3 each
├── textures/bars/        bar texture overrides
├── sounds/               1 registry JSON (6 events: deposit+extract × 3 blocks)
├── lang/en_us.json       block names, mode names, keybinding label
└── item_render_overrides/ 118 mod JSON files (same format, one per mod family)

data/somestacks/tags/items/ingots.json   → delegates to #forge:ingots
```

---

## Notable Implementation Details

- **Light emission** — `ItemOps.calculateLightLevelFromItems()` sums light from any `BlockItem`s stored inside; the block property updates accordingly.
- **Pile management** — Storage Stack blocks form vertical columns ("piles") managed by `PileItemHandler`. `resortAndPackPile()` uses `StackSort.COMPARATOR` (sorts by registry ID → damage → NBT → count) to deterministically consolidate.
- **Gravity (Singles & Bars)** — Removal triggers `cascadeUnsupportedBlocks()` / `removeUnsupportedBlocks()` which iterates upward and drops items that lost support.
- **Permanent mode** — A block flag preventing auto-removal when emptied; toggled with V + empty hand in `TOGGLE_PERMANENT` mode.
- **Analog output** — Comparator reads fill level (0–15) from StorageStackBE.

---

## Per-File Descriptions

### Root package

**`SomeStacks.java`**
The mod entry point annotated `@Mod`. The constructor wires everything together: registers server config, calls `ModRegistry.init()` and `ModNetworking.init()`, and subscribes three Forge event handlers. `onPlayerLogin` parses `RENDER_MODE_OVERRIDES` from server config and pushes a `ConfigSyncPkt` to the logging-in player. `onConfigReload` does the same broadcast to all online players when the server config reloads. `onRegisterCommands` registers the three debug commands.

**`ModRegistry.java`**
Holds all `DeferredRegister` objects and `RegistryObject` constants for blocks and block entity types. Creates the three blocks with their `BlockBehaviour.Properties` (metal/wood color, 0.5/6 strength, no occlusion) and pairs each block with its block entity type. `init()` subscribes both registers to the mod event bus.

**`ModSounds.java`**
Registers six custom `SoundEvent` entries with Forge (deposit/extract × 3 block types) using `createVariableRangeEvent`. Also holds six mutable `static SoundEvent` fields (defaulting to vanilla wood/wool sounds) that `SoundConfig` overwrites at resource reload time — these are the sounds actually played at runtime.

**`ModTags.java`**
Single constant: the `TagKey<Item>` for `somestacks:ingots`, which the Bar Stack uses to validate items. The actual tag JSON delegates to `#forge:ingots`.

**`ServerConfig.java`**
Defines the Forge server config using a static initializer block. Contains pile-sorting tuning (`PILE_SORT_COOLDOWN_TICKS`, `PILE_SORT_MAX_STACKS`), per-block feature toggles, mod/item blacklists (`DISABLE_MODS`, `DISABLE_ITEMS`), and the `RENDER_MODE_OVERRIDES` list (format: `"modid:item,mode,scale,x,y,z"`). All values are server-authoritative and synced to clients.

**`TestModsConfig.java`**
Reads and writes a plain JSON array at `config/somestacks/testmods.json` using Gson. Used exclusively by `TestCommand` to know which mods to spawn test storage stacks for. Creates an empty array file if none exists.

---

### block/

**`StorageStackBlock.java`**
The block class for Storage Stack. Declares the `LIGHT_LEVEL` integer block state property (0–15). Returns `ENTITYBLOCK_ANIMATED` render shape so the BER renders it. Delegates light emission to the block state property, lets skylight pass through (full `getShadeBrightness`), and computes analog output signal from `StorageStackBE.calculateFillLevel()`. `onRemove` drops all stored items.

**`StorageStackBE.java`**
The block entity for Storage Stack — the most complex class in the mod. Owns a 27-slot `ItemStackHandler` whose `onContentsChanged` updates light level and triggers neighbor updates. Core operations: `deposit()` fills the handler using `mergeIntoHandler()` (partials first, then empties) and recursively creates/deposits into blocks above if needed; `extractAt()` removes items at a slot and triggers `resortAndPackPile()`; `resortAndPackPile()` finds the pile base, collects up to `PILE_SORT_MAX_STACKS` blocks, collects all items across them, sorts and consolidates with `StackSort`, redistributes bottom-up, then removes empty non-permanent blocks from the top. Uses `suppressSync` flag to batch client updates during resort. Persists rotation, lastSortTime, and permanent flag to NBT.

**`SinglesStackBlock.java`**
Block class for Singles Stack. Like `StorageStackBlock` but delegates `getShape` and `getCollisionShape` to `SinglesStackBE.getCachedShape()` so collision is per-occupied-slot. Uses a fixed full-block shape for `getInteractionShape` so clicking always registers even in empty areas.

**`SinglesStackBE.java`**
Block entity for Singles Stack. 64-slot handler with `getSlotLimit = 1`. Rejects ingots (defers those to BarStack). Maintains `cubeRotations[64]` for per-item visual rotation. `depositAt(index)` checks the slot is empty and grounded (`y == 0` or item below); `extractAt(index)` removes the item and calls `cascadeUnsupportedBlocks()` which shifts items above downward in the same X/Z column. `computeShape()` / `getCachedShape()` builds a `VoxelShape` union of all occupied slot boxes, applying block rotation; the cache is invalidated when contents change.

**`BarStackBlock.java`**
Block class for Bar Stack. Structurally identical to `SinglesStackBlock`: delegates collision shape to `BarStackBE.getCachedShape()`, uses a fixed full interaction shape, drops items on removal.

**`BarStackBE.java`**
Block entity for Bar Stack. 64-slot handler, `getSlotLimit = 1`, only accepts items tagged `somestacks:ingots`. `depositAt(index)` validates the item is a bar item and calls `BarCubeIdx.isGrounded()`. `extractAt(index)` removes a bar then calls `removeUnsupportedBlocks()`, which iterates all 64 slots in a loop-until-no-change pattern, checking `BarCubeIdx.isGrounded()` for each occupied slot and dropping unsupported bars into the world. No rotation stored (bar arrangement is fixed).

**`PileItemHandler.java`**
An `IItemHandler` wrapper around `StorageStackBE` that is exposed via the Forge capability. `insertItem` in non-simulate mode calls `blockEntity.deposit()` (which handles cross-pile growing) rather than inserting directly into a slot. `extractItem` in non-simulate mode calls `blockEntity.extractFromSlot()`. The simulate paths implement proper slot-level capacity math for external automation compatibility.

---

### client/

**`ClientSetup.java`**
Client-side initialization run only on `Dist.CLIENT`. Registers the three block entity renderers, the `STACK_MODE_KEY` key binding, and three resource reload listeners (`ItemRenderOverrides`, `BarTextureStore`, `SoundConfig`).

**`ClientEvents.java`**
`@Mod.EventBusSubscriber` on `Dist.CLIENT`. Handles all three right-click event types (`RightClickEmpty`, `RightClickItem`, `RightClickBlock`) by building an `InteractionContext` and dispatching to `InteractionRuleRegistry`. Holds the client-side `stackMode` state and provides `cycleModeAllFour()` (skips disabled block types) and `displayModeMessage()`. Also contains the six `send*` helper methods that fire packets to the server.

**`KeyMappings.java`**
Declares one keybinding constant: `STACK_MODE_KEY` bound to `V` in the "gameplay" category, used to trigger mode cycling and item/block placement.

**`RenderMode.java`**
Simple four-value enum (`TWO_D`, `THREE_D`, `BLOCK`, `GUI`) with string IDs used for serialization. `fromString()` returns `null` for unknown strings so callers can fall back gracefully.

**`StackState.java`**
Client-side store for which block types are currently enabled. Three private booleans updated by `ConfigSyncPkt`. `isBlockTypeEnabled(BlockType)` is consulted by `ClientEvents.cycleModeAllFour()` to skip modes whose blocks are disabled on the server.

**`ItemRenderOverrides.java`**
A `SimplePreparableReloadListener` that scans all resource packs for files under `item_render_overrides/`. Loads every JSON entry into `CONFIG_MAP` (optional `mode`, `scale`, `offset`). Holds a separate `SERVER_OVERRIDES` map populated by `ConfigSyncPkt`. Lookup methods `getMode()`, `getScale()`, `getOffset()` check `SERVER_OVERRIDES` first, then `CONFIG_MAP`, then defaults — this is the core of the mod's item-specific render tuning.

**`BarTextureStore.java`**
A `SimplePreparableReloadListener` for `textures/bars/*.json` files. Each entry maps an item ID to a `BarTextureData` (texture `ResourceLocation` + ARGB tint). If the entry specifies `AUTO_TINT_MARKER`, `apply()` computes the tint by loading the item's particle sprite texture as a `NativeImage`, averaging the RGBA of all opaque pixels, and brightening slightly to compensate for dark outlines. The result is cached in a static map for `BarStackBER` to look up at render time.

**`SoundConfig.java`**
A `SimplePreparableReloadListener` for `sounds/*.json` files. Merges all matching JSON objects and maps the six block-type/action combinations to `SoundEvent` registry lookups, writing results directly into the mutable static fields on `ModSounds`. Falls back to vanilla sounds on parse failure.

**`CubeRenderHelper.java`**
The central rendering utility for drawing items inside storage blocks. `renderItemInCube()` auto-detects the render mode (checking `ItemRenderOverrides`, then falling back to `isGui3d()`) and dispatches to one of four private methods: `render3DItem` (calls `ItemRenderer.renderStatic` with `FIXED` context and scale/offset from overrides), `render2DItem` (renders the item's quads onto all six faces of a small cube with a custom background sprite), `renderBlockItem` (calls `BlockRenderDispatcher.renderSingleBlock`, with a fallback to `render3DItem` for crash-prone models), and `renderGuiItem` (like 3D but counter-rotates the GUI transform). Also contains the low-level `emitCube()`, `quad()`, `extractQuadVertices()`, and `transformToFace()` geometry helpers used by the 2D path.

**`StorageStackBER.java`**
Block entity renderer for Storage Stack. Iterates 27 slots, skips empty ones, converts each storage index to visual XYZ via `StorageCubeIdx.rotateXYZ()`, translates the pose stack to the pixel-start position, scales to 0.5×, and calls `CubeRenderHelper.renderItemInCube()`.

**`SinglesStackBER.java`**
Block entity renderer for Singles Stack. Same pattern as `StorageStackBER` but uses `SinglesCubeIdx` geometry (4×4×4, 8/16 scale) and additionally applies per-item Y-axis rotation from `be.getCubeRotation(storageIndex)` by rotating around the cube center before rendering.

**`BarStackBER.java`**
Block entity renderer for Bar Stack. Does not use `CubeRenderHelper`; renders each bar directly as a textured box using `BarTextureStore.getTexture()` to get the sprite and tint color. The `emitBar()` method draws 6 quads with UV regions selected from the texture atlas, with different UV layouts for even (EW) and odd (NS) layers to correctly orient the top-face texture.

---

### client/interaction/

**`InteractionContext.java`**
Bundles all data from a Forge right-click event into a single object: player, level, hand, clicked position and block, face, and current `StackMode`. Three static factories build it from the three event types. Convenience boolean helpers (`isShift()`, `isVDown()`, `isAnyStackBlock()`, etc.) are used by rules to check preconditions. Holds a `shouldCancel` flag that `ClientEvents` reads after processing to decide whether to cancel the event.

**`InteractionRule.java`**
A minimal two-method interface: `matches(ctx)` tests preconditions, `execute(ctx)` performs the action. `getName()` defaults to the class simple name for logging.

**`InteractionRuleRegistry.java`**
Holds three static immutable lists of rules: `EMPTY_HAND_RULES` (just `ModeCycleRule`), `ITEM_RULES` (also just `ModeCycleRule`), and `BLOCK_RULES` (7 rules in priority order). `processFirstMatch()` stops at the first matching rule — only one rule fires per event.

**`ModeCycleRule.java`**
Matches when V is held, main hand, client side, and the player is not looking at a block. Calls `ClientEvents.cycleModeAllFour()` and `displayModeMessage()`. Only cancels the event if the player has an item in hand (to avoid canceling an air swing).

**`PlaceAdjacentGenericRule.java`**
Matches when V is held, not shift, has item, and mode allows placement. Sends `PlaceAndDepositPkt` targeting the block face's adjacent position — placing a new stack block at a clicked surface and immediately depositing into it.

**`PlaceAdjacentToStackRule.java`**
Matches when V + Shift, has item, clicked block is a stack, and mode allows. Same action as `PlaceAdjacentGenericRule` but requires clicking on an existing stack. In practice this rule is listed in `BLOCK_RULES` but `PlaceAdjacentGenericRule` appears there too with a wider match — the ordering means this one is currently unreachable.

**`DepositIntoClickedStackRule.java`**
Matches when V is held, not shift, has item, and clicked block is any stack. Sends `DepositPkt` to the server targeting the clicked stack.

**`DepositIntoAdjacentStackRule.java`**
Matches when V held, not shift, has item, and the block adjacent to the clicked face is a Singles or Bar stack. Useful for depositing into a stack by clicking the floor next to it. Sends `DepositPkt` to the adjacent block's position.

**`ExtractionRule.java`**
Matches when not shift, not V, and a stack block is clicked — the default right-click. Performs ray tracing against the appropriate `*CubeIdx.traceCubes()` to find the specific item the player is looking at, then sends `ExtractPkt` with that slot index. Cancels the event regardless to prevent vanilla block interactions.

**`RotateBlockWithRedstoneTorchRule.java`**
Matches when Shift is held, the player holds a Redstone Torch, and a stack block is clicked. Sends `RotateBlockPkt` to cycle the block's rotation 90° (applies to Storage and Singles stacks; not Bar).

**`RotateItemWithSoulTorchRule.java`**
Matches when Shift held, player holds a Soul Torch, and a Singles or Bar stack is clicked. Ray-traces to find the targeted slot index, then sends `RotateItemPkt` to rotate just that individual item's visual orientation.

**`TogglePermanentRule.java`**
Matches when V held, empty hand, mode is `TOGGLE_PERMANENT`, and a Storage Stack is clicked. Sends `TogglePermanentPkt`. This rule is listed first in `BLOCK_RULES`, giving it highest priority.

---

### network/

**`ModNetworking.java`**
Creates the `SimpleChannel` at `somestacks:main` with protocol version `"1"` and registers all 8 packet types with sequential integer IDs. Each packet provides static `encode`, `decode`, and `handle` methods.

**`ConfigSyncPkt.java`**
Server → Client. Carries three block-enable booleans and a map of item IDs to `RenderConfig` (mode + scale + offset[3]). The handler (client-side) pushes the booleans into `StackState` and converts the map into `ItemRenderConfig` objects for `ItemRenderOverrides.SERVER_OVERRIDES`.

**`PlaceAndDepositPkt.java`**
Client → Server. The most involved packet. Carries `blockType`, `face`, `hand`, and target `pos`. The server validates the position can be replaced, checks item blacklists, checks the config flag, then checks cross-block gravity (for Singles/Bars placed above an existing stack — must have support from below). Places the block, then calls the appropriate deposit method. If deposit fails, removes the just-placed block.

**`DepositPkt.java`**
Client → Server. Carries `hand` and `pos`. Server checks blacklists, identifies which block entity is at the position, and calls the appropriate deposit method. For Singles/Bars, re-runs `traceAllPositions()` server-side using the player's eye position and look direction to find the target slot.

**`ExtractPkt.java`**
Client → Server (record). Carries `hand`, `pos`, and `index`. Three private static helpers handle extraction for each block type. After extracting, calls `RightClickBlockSuppressor.suppress()` to prevent the vanilla use-item-on-block packet (sent by the client after the right-click) from misfiring. Removes empty non-permanent blocks. Gives extracted item to player or drops it.

**`RotateBlockPkt.java`**
Client → Server. Carries `pos`. Increments rotation by 1 (mod 4) on `SinglesStackBE` or `StorageStackBE` and displays the new angle in degrees as an action bar message. Also calls `resortAndPackPile()` for Storage stacks after rotating.

**`RotateItemPkt.java`**
Client → Server. Carries `pos` and `slotIndex`. Only applies to Singles stacks. Increments `cubeRotations[slotIndex]` by 1 (mod 4) and displays the new per-item angle.

**`TogglePermanentPkt.java`**
Client → Server. Carries `pos`. Flips `StorageStackBE.permanent` and sends an action bar message showing "Permanent" or "Temporary".

**`RenderOverridePkt.java`**
Server → Client. Carries one item ID with its full render config. The handler writes directly into `ItemRenderOverrides.CONFIG_MAP` (not `SERVER_OVERRIDES`). Used by the `/somestacks item` command for live per-item render tuning.

---

### server/

**`RightClickBlockSuppressor.java`**
Solves a vanilla interaction timing bug: after the client sends `ExtractPkt`, the Minecraft protocol also sends a use-item-on-block packet for the same click. `suppress(player, pos, level)` stamps the player's persistent NBT with the current game tick and block position. A `HIGHEST`-priority `RightClickBlock` event handler checks for that stamp on the following tick and cancels the event if it matches, then clears the stamp. This prevents items in hand from being placed/used at the just-emptied block position.

---

### util/

**`ItemOps.java`**
Stateless utility class for item manipulation. `canTakeIntoHand()` checks if an item can merge into the player's hand. `mergeIntoStack()` adds as much of `incoming` as fits into `hand`. `dropAllItems()` drops every non-empty slot. `giveToPlayerOrDrop()` gives to the hand first, drops any remainder. `calculateLightLevelFromItems()` sums `lightEmission / 4` for each `BlockItem` in a handler, capped at 15. Four methods (`isItemFromDisabledMod`, `checkDisabledModAndNotify`, `isItemDisabled`, `checkDisabledItemAndNotify`) check the server config blacklists and optionally send a denial message.

**`BlockType.java`**
Enum with three values (`STORAGE_STACK`, `SINGLES_STACK`, `BAR_STACK`). Provides switch-dispatch accessors for the associated block (`getBlock()`), deposit/extract sounds (`getDepositSound()`, `getExtractSound()`), server config flag (`getConfigValue()`), and corresponding `StackMode` (`toStackMode()`).

**`StackMode.java`**
Enum with four values (`STORAGE_STACK`, `SINGLES_STACK`, `BAR_STACK`, `TOGGLE_PERMANENT`). Each has a translation key for the action bar message. `isBlockType()` returns false for `TOGGLE_PERMANENT`; `toBlockType()` throws on `TOGGLE_PERMANENT`.

**`StackSort.java`**
Single public constant: `COMPARATOR` — a `Comparator<ItemStack>` that orders by registry ID alphabetically, then damage value, then NBT presence (no-NBT first), then count ascending. The ascending count ordering ensures partial stacks sort before full ones so `consolidate()` fills them first.

**`StorageCubeIdx.java`**
Geometry utility for the 3×3×3 Storage Stack grid. `STARTS = {1, 6, 11}` pixel offsets (1-pixel margin, 4-pixel cubes with 1-pixel gaps). `xyzFromIndex()` decomposes a slot 0–26 into (x, y, z) ∈ {0,1,2}³. `rotateXYZ()` applies 90° increments around Y. `traceCubes()` casts a ray against AABBs for all occupied slots and returns the index of the closest hit.

**`SinglesCubeIdx.java`**
Geometry utility for the 4×4×4 Singles Stack grid. `STARTS = {0, 4, 8, 12}` (4-pixel cubes, no gap). `traceCubes()` is for extraction (returns closest occupied hit); `traceAllPositions()` is for deposit (walks hits front-to-back, stopping at the last empty slot before the first occupied one — places on top of an existing pile). `isGrounded()` checks `y == 0` or slot below is occupied. `calculateDepositIndex()` returns the farthest hit slot (used when placing a new block with a first deposit).

**`BarCubeIdx.java`**
Geometry utility for the Bar Stack's 8-layer alternating grid. EW layers (even) have 2×4 positions with 6×3-pixel bars; NS layers (odd) have 4×2 positions with 3×6-pixel bars; all bars are 2 pixels tall. `xyzFromIndex()` decomposes differently depending on even/odd layer. `isGrounded()` checks layer 0 or any occupied bar in the layer below whose XZ footprint overlaps this bar's footprint (the cross-stacking constraint). `traceCubes()`, `traceAllPositions()`, and `calculateDepositIndex()` mirror the Singles equivalents but use bar-shaped AABBs.

---

### command/

**`ModCommand.java`**
Registers `/somestacks mod <modid>` (creative-only). Sets `ItemTester.targetModId` to the given mod and reports how many items that mod has. Autocomplete suggests all mod IDs that have registered items.

**`ItemCommand.java`**
Registers `/somestacks item <item> <mode> <scale> <x> <y> <z>` (creative-only). Sends a `RenderOverridePkt` back to the commanding player, which immediately updates `ItemRenderOverrides.CONFIG_MAP` on their client. Used during development to interactively tune how a specific item looks inside a stack without editing JSON files.

**`TestCommand.java`**
Registers `/somestacks test` (creative-only). Reads the mod list from `testmods.json`, filters to loaded/non-disabled mods, then calls `ItemTester.createStorageStacks()` for each, placing stacks of Storage Stack blocks east of the player filled with each mod's items in batches of 9.

**`ItemTester.java`**
Dev/test utility. Holds `targetModId` (default `"minecraft"`). `collectModItems()` filters the Forge item registry by namespace. `createStorageStacks()` places Storage Stack blocks at a position and fills them with 9 items each. Also registers a `@SubscribeEvent` for `RightClickBlock` with a Stick + Shift that immediately spawns stacks above the clicked position using the current `targetModId` — a manual in-game test shortcut.
