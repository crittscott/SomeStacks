# Behavioral differences: 1.20.1 vs 1.21.4

Scope: everything a player, server admin, pack maker, or other mod can observe. NeoForge and the 1.21.1 world/config migration are excluded. Method: every Java file present in both trees was diffed (common, forge, fabric `src/main`), files present in only one tree were read, and the resource trees, lang file, and loader metadata were compared. Paths are relative to each version's `common/src/main/java/com/github/crittscott/somestacks/` unless a loader is named.

Each item lists what 1.20.1 does, what 1.21.4 does, and where. "Version-forced" items at the end are differences that come from the Minecraft/loader API and need no alignment.

## Goal and owner decisions

The goal is for all three versions to behave the same. 1.20.1 is being brought in line with 1.21.4 first; 1.21.1 follows. The work happens in 1.20.1 only. Do not change 1.21.4 or record fixes for it; the owner tracks 1.21.4 issues separately.

Decisions already made by the owner:

- **Known 1.21.4 bugs — keep the 1.20.1 behavior:** 1.7 (a grown Storage block copies the base's rotation) and the gallery session cleanup in 2.6 (jobs and indexes cleared on server start and stop).
- **Automation identity (4.1):** 1.20.1 should appear as `[SomeStacks]`, using the same profile as 1.21.4 (`FakePlayerFactory.get(level, PROFILE)` on Forge, `FakePlayer.get(level, PROFILE)` on Fabric).
- **Fabric protection (4.2, 4.3):** the protection systems differ between versions by design. On 1.20.1, FTB Chunks and Open Parties and Claims are the dominant claim mods and must stay supported through their direct integrations. Common Protection API does exist for 1.20.1, so it can be added alongside them; it does not replace them.
- **Render-profile corpus (5.2):** do not modify any `item_render_overrides` JSON. Other mods change over time, and the 1.21.4 values are valid for 1.21.4; the 1.20.1 values stay as they are.

## 1. Gameplay

### 1.1 Vibrations for ordinary stack use
- **1.20.1:** only placement, growth, and cleanup/cascade removal emit game events (`BLOCK_PLACE`/`BLOCK_DESTROY` in `server/WorldEdits`).
- **1.21.4:** additionally emits `GameEvent.BLOCK_CHANGE` with the player as source after every successful deposit, extraction, block rotation, item rotation, and permanence toggle (`server/StackInteractions.emitBlockChange`, `emitSurvivingBlockChange`). Extraction emits it only when the block survives, so removing the last Single/Bar emits only the destruction event.

### 1.2 Removing a waterlogged stack
- **1.20.1:** automated removal (empty-top trim, Singles cleanup, Bar cascade) uses `level.setBlock(pos, AIR)`, so a waterlogged stack's water disappears with it.
- **1.21.4:** uses `level.removeBlock(pos, false)` (`WorldEdits.removeChecked`), which leaves the water source behind.

### 1.3 Who performs cleanup a player caused
- **1.20.1:** every automated removal (Storage trim after extraction or permanence toggle, Singles empty-block cleanup, Bar cascade removal) is attributed to the automation actor.
- **1.21.4:** removal caused by a player's extraction or permanence toggle is attributed to that player: `StorageStackBE.noteCleanupActor`/`takeCleanupActor`, `StoragePile.markDirtyBy`, `SinglesStackBE.extractAt(index, actor)`, `BarStackBE.extractAt(index, actor)`. Mixed or automation causes fall back to the automation actor. The player's identity reaches protection checks, break events, and the destruction game event. Removal under a Bar block broken by a player still uses automation (`collapseAbove(..., null)`).

### 1.4 Unreadable saved items
- **1.20.1:** `util/StackItemStorage.deserializeNBT` uses `ItemStack.of`. An item whose mod is gone becomes empty and is lost on the next save.
- **1.21.4:** any entry that can't be parsed, or whose slot is out of range or already taken, is kept verbatim under `SetAside`, saved back, and retried on every load. The contents return when the mod returns, and the event is logged with dimension and position (`StackItemStorage`, `SomeStacksServer.reportSavedItems`). CLAUDE.md requires this behavior.

### 1.5 Bar admission source
- **1.20.1:** the server setting `compatibility.ingot_tags` (glob patterns over item-tag names, default `forge:ingots*` or `c:ingots*` plus `somestacks:ingots`) is baked into an item set (`ServerConfig.bakeIngotItems`, rebaked on tag reload). It is editable with `/ss ingot add|remove|list`. The shipped `somestacks:ingots` tag holds only optional `#forge:ingots` and `#c:ingots`; bricks arrive through child tags matched by the wildcard.
- **1.21.4:** `stack.is(somestacks:ingots)` only (`BarStackBE.INGOTS`). No config key and no `/ss ingot`. The shipped tag holds optional `#forge:ingots`, `#c:ingots`, `#c:bricks`, and about 50 explicitly listed modded ingots and bricks. Child tags count only through normal tag composition (no wildcard).
- **Effect:** the set of items Bar accepts, and so Singles refuses, can differ between versions in the same pack. Bricks work in both, but by different routes.

### 1.6 Structure rotation and mirroring
- **1.20.1:** Storage/Singles block rotation is block-entity NBT (`Rotation`). Structure blocks, jigsaws, and other `BlockState.rotate`/`mirror` callers leave the layout unchanged.
- **1.21.4:** rotation is the block-state property `horizontal_facing`, and `StorageStackBlock`/`SinglesStackBlock` override `rotate` and `mirror`, so the layout turns with the structure. Per-item Singles rotations stay in NBT and are not rotated.

### 1.7 Facing of a block grown onto a Storage pile
- **1.20.1:** `StoragePile.grow` copies the base block's rotation onto the new top block (`grown.setRotation(blocks.get(0).getRotation())`).
- **1.21.4:** the copy is gone. A grown block takes the default `NORTH` (unrotated) state, although the method's comment still says "growth uses the base rotation".
- **Decision:** a 1.21.4 bug. Keep the 1.20.1 behavior. If 1.20.1 moves rotation into the block state (1.6), the grown block must still copy the base's facing.

### 1.8 Gesture validation and targeting
- **1.20.1:** one server handler (`StackInteractions.handleBlockClick`, called from Forge `RightClickBlock` at LOWEST / Fabric `UseBlockCallback` after the default phase) handles every gesture. It enforces one gesture per player per tick (`GestureThrottle.claimTick`), checks loaded chunks and reach (`withinReach`, reach plus 1 block) for the clicked block and for the neighbor in adjacent deposits, and traces cells along the server's current view of the player's look (`ViewRays.of(player)`).
- **1.21.4:** gestures on an existing stack run from the block's own `useItemOn`/`useWithoutItem` (`block/StackBlock`). Only the sneaking torch rotations and adjacent clicks run from the loader event. There is no per-tick gesture limit and no own reach or loaded check; it relies on vanilla's use-packet validation. Cells are traced along a reach-length ray through the hit point vanilla received (`ViewRays.through(player, hit.getLocation())`).
- **Effect:** under latency, 1.21.4 selects the cell the client was aiming at when it clicked; 1.20.1 selects what the server thinks the player is looking at now. 1.20.1 silently drops a second gesture in the same tick.

### 1.9 Fabric: cycling placement mode with an off-hand item
- **1.20.1:** `fabric/.../mixin/MinecraftMixin` cycles only when both hands are empty, so an off-hand torch or shield blocks empty-hand cycling.
- **1.21.4:** `ClientGestures.isEmptyMainHandAirClick` checks only the main hand. Forge behaves the same in both versions.

## 2. Server administration

### 2.1 `/ss reload`
- **1.20.1:** rereads `config/somestacks/server_item_overrides/` and resyncs. Does not touch policy. Fabric JSON edits need a restart.
- **1.21.4:** reloads server policy (`ServerConfig.reload()`; Fabric rereads the world JSON through `JsonServerConfig`, Forge reads the current TOML values), rebuilds the sync snapshot, and resyncs every player.

### 2.2 Live Forge TOML reload resync
- **1.20.1:** resyncs players only when an enable flag changed (`forge/.../ForgeServerConfig.queuePublication`).
- **1.21.4:** resyncs on any policy change (`SomeStacksServer.onPolicyReloaded`), because the sync now also carries `gen_mods` and `disable_mods` (see 3.1).

### 2.3 Default `disable_items`
- **1.20.1:** empty.
- **1.21.4:** `["evilcraft:broom_part"]` (`ServerConfig.DEFAULT_DISABLE_ITEMS`), an item whose model throws when drawn.

### 2.4 Forge TOML comments
- **1.20.1:** `ForgeServerConfig` defines values without comments, so the generated `somestacks-server.toml` has no explanations.
- **1.21.4:** every section and key carries a `.comment(...)` explaining it.

### 2.5 Config keys
- 1.20.1 has `compatibility.ingot_tags`; 1.21.4 does not (see 1.5). All other keys, ranges, and defaults match except 2.3.

### 2.6 Render galleries
- **Item order within a mod:** 1.20.1 uses registry order; 1.21.4 sorts by item id (`RenderGalleryGenerator.itemsByNamespace`).
- **Disabled items:** 1.21.4 omits items in `disable_items` from every gallery form (`Kind.itemsIn`) and from `/ss gallery items` (`SsCommand`). 1.20.1 omits only disabled mods.
- **`list` form order:** 1.21.4 sorts the `gen_mods` namespaces; 1.20.1 keeps the list's order.
- **`all` form:** 1.21.4 also drops namespaces that have no usable items for that gallery kind.
- **Logging:** 1.20.1 warns for each skipped placement. 1.21.4 logs one summary per gallery ("skipped N of M").
- **Session cleanup:** 1.20.1 clears queued jobs and indexes on server start and stop (`RenderGalleryGenerator.clear`). 1.21.4 has no such clear. **Decision:** a 1.21.4 bug; keep the 1.20.1 clearing. The other gallery differences in this section are 1.21.4 behavior to adopt.

### 2.7 Help text
- **1.20.1:** usage lines are lang keys (`somestacks.command.help.<cmd>.usage.N`) and include `/ss ingot` plus server-side `/ss item` and `/ss write` with the "operator, in game only" gate.
- **1.21.4:** usage forms come from the Brigadier tree plus the two client-only forms, and there is no `ingot` entry. The `reload` summary and details describe the new reload behavior.

## 3. Client tools and appearance

### 3.1 `/ss item` and `/ss write`
- **1.20.1:** server commands requiring permission level 2 and a player. The server sends `RenderOverridePkt`/`WriteOverridesPkt` and the client acts (`SsCommand`, `CommandNetwork`, `DefaultClientRenderPacketHandler`). Namespace forms use the server's item index.
- **1.21.4:** client-side commands (`client/ClientRenderCommands`, registered through Forge `RegisterClientCommandsEvent` / Fabric `ClientCommandRegistrationCallback` with a pass-through so other `/ss` forms still reach the server). Anyone can use them, they work on any server, and no server packet can trigger an override edit or file write. `write list`/`all` use the client's installed namespaces plus the server's `gen_mods` and `disable_mods`, which `ConfigSyncPkt` now carries in byte-bounded chunks (`ClientRenderToolState`).

### 3.2 Server-imposed render profiles
- **1.20.1:** `config/somestacks/server_item_overrides/*.json` is read by `ServerOverridesLoader`, synced in `ConfigSyncPkt`, and wins over the player's own overrides. Resolution order is server > user > resource pack > measurement.
- **1.21.4:** the server layer doesn't exist. Servers distribute profiles through a server resource pack. Resolution order is user > resource pack > measurement (`client/ItemRenderOverrides.resolve`).

### 3.3 Error cube for items whose model throws
- **1.20.1:** the `2d` path has no handling for a throwing model.
- **1.21.4:** `2d` art is captured through `client/ItemCapture`. If the item's model throws, the item is remembered in `FLAT_CAPTURE_FAILURES`, drawn with `textures/block/stack_cube_error.png` (red "ERR") until the next resource reload, and logged once (`CubeRenderHelper.captureFlatArt`). The texture is absent from 1.20.1.

### 3.4 Derived Bar tints
- **1.20.1:** average of the item model's particle sprite multiplied by the item's registered layer-0 color (`BarTextureStore`).
- **1.21.4:** average of the sprite of the first quad the item actually draws in the GUI context multiplied by that quad's tint. Usually the same result; items whose particle sprite differs from their drawn art get a different bar color.

### 3.5 Measured-profile logging
- 1.21.4 logs milestones when the measured cache is loaded, discarded (format or pack change), or cleared on reload. 1.20.1 logs a debug line per fallback instead. Cache format numbers differ (1 vs 2) but neither version reads the other's file.

## 4. Protection and integration

### 4.1 Automation identity
- **1.20.1:** Forge `FakePlayerFactory.getMinecraft(level)`; Fabric `FakePlayer.get(level)`. Claim mods see each loader's default fake player.
- **1.21.4:** one shared profile, `server/AutomationActor.PROFILE` (name `[SomeStacks]`, UUID from `somestacks:automation`). Fabric uses `FakePlayer.get(level, PROFILE)`. Forge uses a per-dimension `ServerPlayer` with that profile (Forge 1.21.4 has no FakePlayer).
- **Decision:** adopt the `[SomeStacks]` identity on 1.20.1 through the native fake-player facilities: `FakePlayerFactory.get(level, PROFILE)` on Forge and `FakePlayer.get(level, PROFILE)` on Fabric.

### 4.2 Fabric claim checks
- **1.20.1:** `FtbChunksProtection`/`OpacProtection` (optional FTB Chunks / Open Parties and Claims) are queried inside `FabricEditAuthority.place`. Other claim mods are not consulted.
- **1.21.4:** the optional Common Protection API (`CommonProtectionQueries`) is consulted for placement and growth (`mayPlace`, also called before automation promises headroom capacity) and for deposits into an adjacent Singles/Bar stack (`canInteractBlock`). FTB/OPAC are consulted only if they implement that API. Metadata: 1.20.1 `suggests` ftbchunks/openpartiesandclaims; 1.21.4 `suggests` common-protection-api.
- **Decision:** the protection systems are expected to differ between versions. On 1.20.1, keep the direct FTB Chunks and Open Parties and Claims integrations; they are the dominant claim mods there. Common Protection API exists for 1.20.1 and can be added alongside them, not in place of them.

### 4.3 Automation growth precheck
- **1.20.1:** `canGrow` checks vanilla protection only (`!WorldEdits.isProtected`). The loader veto comes at placement time.
- **1.21.4:** `canGrow` calls `WorldEdits.mayPlace`, which adds the loader query (Common Protection API on Fabric). A claimed headroom position is therefore not advertised as insertable on Fabric, instead of failing at insert.

### 4.4 Forge multi-snapshot placement event
- 1.20.1 posts `onMultiBlockPlace` when the placement captured more than one snapshot; 1.21.4 always posts `onBlockPlace` with the first snapshot (`forge/.../ForgeEditAuthority.place`). This matters only to mods that listen for `EntityMultiPlaceEvent`.

## 5. Resources and data

### 5.1 Sounds
- **1.20.1:** a custom data-pack format, `data/<ns>/somestacks_sounds/*.json`, maps each stack type and action to an existing sound event (`StackSoundData`, Fabric `FabricStackSoundData`). No subtitles. Data packs can change it.
- **1.21.4:** nine registered sound events (`somestacks:block.<type>.<action>`) with `assets/somestacks/sounds.json` entries and `subtitles.somestacks.*` lang keys. Resource packs replace them through `sounds.json`; data packs can't.

### 5.2 Render-profile corpus (`assets/somestacks/item_render_overrides`)
- `mcwstairs.json` exists only in 1.21.4.
- `another_furniture.json` (98 changed lines) and `betterend.json` (308 changed lines) differ in mode, scale, and offset for some items.
- **Decision:** not a difference to align. Mods change between versions, and each version's values are valid for that version. Do not modify any `item_render_overrides` JSON, and do not port `mcwstairs.json`.

### 5.3 Bar texture mappings
- 1.21.4 `textures/bars/default.json` adds `minecraft:resin_brick`, an item that exists only in 1.21.4. No other differences, so nothing to align.

### 5.4 Lang
- 1.20.1-only keys belong to `/ss ingot`, the server-side `/ss item`/`/ss write` help gate, per-command usage lines, and `skip.from_disabled_mods`. 1.21.4-only keys are the nine sound subtitles. Changed strings: `command.help.reload.*`, `command.help.write.detail.4`, and `command.reloaded`.

## 6. Version-forced differences (no alignment needed)

- Item identity and sorting use NBT tags in 1.20.1 and data components in 1.21.4 (`StackKey`, `StackSort`, `ItemOps`). Same rules.
- Model measurement: 1.20.1 uses loader backends (`forge/.../ModelMeasurer`, `fabric/.../FabricModelMeasurer`); 1.21.4 uses vanilla item-model capture. Results can differ for custom-rendered items because the rendering APIs differ.
- Render shape `ENTITYBLOCK_ANIMATED` vs `INVISIBLE`; the former no longer exists in 1.21.4.
- Networking: SimpleChannel / Fabric raw channels vs payload types; the Fabric handshake moved from play to configuration phase. Both refuse mismatched clients.
- Java 17 vs 21 runtime requirement.
- Client update packets in 1.21.4 are validated before applying (`StackBlockEntity.loadClientUpdate`, `StackItemStorage.deserializeClientUpdate`) and strip `Permanent` and `SetAside`. 1.20.1 applies update tags directly. Invisible to players with a well-behaved server, but it is a trust-boundary difference.

## 7. Areas compared and found equivalent

Movement and gravity for all three types (`StoragePile` settlement, merge, sort, and trim; `SinglesColumn`; `BarColumn`, including automation backfill), support and seam rules, cell geometry and ray selection (`CubeGrid`, `*CubeIdx`, `ViewRay`), comparator formula, light derivation, collision/outline/interaction shapes and pathfinding, piston behavior, waterlogging on placement, gesture routing order (toggle, rotate, deposit into clicked or neighbor, place-and-deposit, extract), deposit and extraction rules including creative mode, admission predicates (`isValid*Item`, `disable_mods` vs `disable_items` scope), automation views (slot numbering, headroom, slot limits, reentrancy guard, Fabric transaction staging), rotation-sound throttle (4 ticks), keybinding, enable-flag sync and mode cycling, gallery permission gate and throttle, `/ss gen` and `/ss deny`, user override file, generated-overrides output, measured-cache invalidation rules.
