# Platform notes

Facts about how specific Minecraft versions, loaders, and libraries behave, learned the hard way. General rules belong in `CLAUDE.md` and `code-conventions.md`; this file holds what is true only of a particular platform. Nothing here is about one mod.

Every fact is scoped to the version it was learned on unless it says otherwise. Do not assume it holds on another version; check it when porting.

Each fact ends with how it is known:

- **[tested]**: a build, gametest, or in-game check depended on it.
- **[source]**: read from published source (Fabric API, a library, a JVM spec).
- **[recalled]**: from memory, unconfirmed. Treat as a hypothesis.

Upgrade the mark when a build or test settles a fact, and correct or delete a fact that turns out wrong.

## Minecraft

### GameTests

- 1.20.1: the default GameTest timeout is 100 ticks; tests that wait for a longer cooldown need an explicit timeout. [tested: owner-run Fabric report]
- Fabric 1.20.1 headless GameTests use the physical SERVER environment and reject loading `net.minecraft.client.Minecraft`; simulation code invoked by those tests must receive GUI context without calling the client singleton. [tested: owner-run Fabric stack trace]


### Rendering

- 1.20.1 and 1.21.1 draw items immediately and have no use for item render state code; 1.21.4 and later use it. [tested on 1.20.1]
- 1.20.1: the render pose holds the view rotation `V = Rx(pitch) Ry(yaw + 180)`, and `cameraOrientation` is `Ry(-yaw) Rx(pitch)`. The true inverse of `V` is `cameraOrientation * Ry(180)`; without the 180 degree turn, pitch doubles instead of canceling. [tested in game; the matrices themselves are recalled]
- The view convention differs by version, so derive what each version's pose holds instead of copying a fix. 1.21.1, 1.21.4, and 1.21.8 pass `cameraOrientation`; 1.21.11 and later have been treated as world-space poses. [recalled]
- `ItemColor` is asked once per tinted quad, and `item/generated` makes a quad for each edge pixel run, so a tint callback can run dozens of times per item per frame. [recalled]

### Interaction and items

- 1.20.1: `Player.mayUseItemAt(pos, face, stack)` tests the block at `pos.relative(face.getOpposite())`. Callers pass the position next to the clicked block: `clicked.relative(face)`. [tested]
- 1.20.1: `ServerPlayer.canHarmPlayer` already includes the server's PvP setting. [tested]
- 1.20.1: the server swings the arm when the interaction result is `SUCCESS`, not `CONSUME`. Server paths return `CONSUME`. [recalled]
- `ItemStack.useOn` awards `Stats.ITEM_USED`, and `ServerPlayerGameMode.useItemOn` fires `ITEM_USED_ON_BLOCK`. A cancelled click event returns before both, so a handler that takes over must award them itself. [recalled]
- 1.20.1: `Minecraft.startUseItem` acts only when `!gameMode.isDestroying()`, and walks the hands only when `!player.isHandsBusy()` (true while steering a boat). For each hand it tries the block hit first and falls through from a `PASS` block use to item use, which reaches `MultiPlayerGameMode.useItem` (where Fabric fires `UseItemCallback`) only for a nonempty stack. A `HEAD` injection runs before both gates. [recalled]
- `LivingEntity.heal` is called without a health check by some vanilla code, for example horse regeneration. [recalled]
- `Player` overrides `LivingEntity.actuallyHurt` without calling super, so an injection on `LivingEntity` misses players. [tested]
- 1.20.1: a shield-blocked hit still calls `actuallyHurt`, with zero damage. [recalled]
- 1.20.1: there are no interaction-range attributes. The client's crosshair reaches blocks at 4.5 (5 in creative) and entities at 3 (6 in creative). [recalled]
- `dropCustomDeathLoot` and the loot table drop run only inside the `shouldDropLoot() && doMobLoot` gate in `dropAllDeathLoot`. A drop rolled by mod code is not covered by that gate. [tested]
- `LivingEntity.hurt` returns false on the client before `actuallyHurt`, and the client's trade menu uses `ClientSideMerchant`, not an `AbstractVillager`. [recalled]
- `ItemStack.isSameItemSameTags` is the 1.20.1 name; later versions need the component equivalent. [tested on 1.20.1]
- 1.20.1: `ItemStackLinkedSet.TYPE_AND_TAG` is private, so mods cannot reuse it as a fastutil hash strategy; a mod writes its own over item and tags (`ItemStack.isSameItemSameTags`). Damage lives in the tag, so that groups exact stack identities. [compiled]
- 1.20.1: vanilla `Minecraft` has no public `getItemColors()`; Forge adds one, so common code cannot call it. Fabric reaches item tint providers through `ColorProviderRegistry.ITEM.get(item)`. [compiled]
- 1.20.1: `Block.playerWillDestroy(Level, BlockPos, BlockState, Player)` returns `void` and runs on the server before the block is removed on both loaders (Forge through `onDestroyedByPlayer`), so a block entity can record the breaker for `onRemove`. Explosions, commands, and `Level.destroyBlock` do not call it. It also runs on the client during the local break prediction. [recalled]
- The name of `DamageSource.isDirect` appears to differ between release lines. [recalled]
- `DamageSources.thorns(wearer)` names the wearer as both causing and direct entity; a player's melee hit is the `PLAYER_ATTACK` damage type. [recalled]
- Tamable animals and horses broadcast `EntityEvent.TAMING_SUCCEEDED` when tamed. [recalled]

### Data and block entities

- 1.20.1: the loot-function interface extends `BiFunction<ItemStack, LootContext, ItemStack>`; apply its return value to the generated loot. [source: Fabric's published [Yarn API documentation](https://maven.fabricmc.net/docs/yarn-1.20.1+build.10/net/minecraft/loot/function/LootFunction.html), signatures only]

- 1.20.1: `BlockItem` merges a stack's `BlockEntityTag` into the block entity and calls `load`, and heads are not an op-only block entity. A creative player needs no operator rights to supply that NBT, so a `load` that keeps a whole compound stores and rebroadcasts whatever was sent. [recalled]
- 1.20.1: an empty block-entity update tag reaches the client as no tag, and the client then skips the load, so state that clears to "save nothing" must still put something in the update tag or clients keep the old state. [tested in game on Fabric: a sheared plain head kept its eyes; the packet's empty-to-null step is recalled]
- 1.20.1: `ItemStack.save` writes `id`, `Count`, and a copy of the stack's data under `tag`. A filled container block item (a shulker box) carries its contents in `tag.BlockEntityTag.Items`, a banner item its `Patterns` in `tag.BlockEntityTag`, and written and writable books their text in `tag.pages`. `BlockItem.BLOCK_ENTITY_TAG` and `WrittenBookItem.TAG_PAGES` name those keys, and `LightEngine.MAX_LEVEL` is the light maximum, 15. [recalled]
- 1.20.1: `ResourceLocation.isValidNamespace` is private; test a namespace with `ResourceLocation.tryBuild(namespace, "x") != null`. [tested: compile error]
- 1.20.1: `PackMetadataSection` is a class built from a description and one `int` pack format, with no codec; its `TYPE` serializer is a `MetadataSectionType` whose `toJson` writes the section and whose `getMetadataSectionName` is `pack`. Later versions replace it with a codec record carrying supported-format ranges. [recalled]
- A non-special recipe needs an unlock advancement, or `doLimitedCrafting` blocks it and the recipe book never offers it. The path is `data/<ns>/advancements/recipes/...` on 1.20.1 and uses the `advancement` directory on later versions. [tested on 1.20.1]

### Commands

- 1.20.1: a distant command spawn can return true from `tryAddFreshEntityWithPassengers` while an immediate `getEntity(UUID)` does not return that entity. Treating this lookup as an insertion failure discards accepted distant spawns. [tested in game: fixed-distance cutoff in Minecraft and Animania authoring grids; diagnostic confirmed this branch]
- Brigadier evaluates `requires` at parse time for each execution, but the client's copy of the tree is only resent on login, reload, and permission change, not on `/gamemode`. Mutable state in `requires` gives stale tab completion. [recalled]
- `StringArgumentType.word()` stops at `:`, and `FloatArgumentType` accepts a dot only, whatever the locale. [recalled]

### Text

- `Component.translatable` sends only the key, and the receiver resolves it. A client without the mod sees the raw key; use `translatableWithFallback`. [tested]
- Lang files may not be available on a dedicated server under Forge, so resolving a lang key at reload time can yield the raw key. [recalled]

## Forge

- 1.20.1: `LivingDropsEvent` fires after the loot gate in `dropAllDeathLoot`, whatever `doMobLoot` says, because it also carries equipment drops. A listener must check the game rule itself. [tested]
- 1.20.1: `LivingDamageEvent` fires from both `actuallyHurt` implementations. `LivingHealEvent` fires before the heal. [tested]
- 1.20.1: `ForgeEventFactory.onLivingConvert` is patched into the hand-built lightning conversions, so `LivingConversionEvent` covers them. [tested]
- 1.20.1: `TradeWithVillagerEvent` is posted from `AbstractVillager.notifyTrade`. [tested]
- 1.20.1: `ModConfigEvent.Reloading` fires on the config file watcher's thread, not the server thread. [recalled]
- 1.20.1: `LivingConversionEvent.Post` is posted when a tadpole grows into a frog. [recalled, unverified]
- 1.20.1: `onPlayerDestroyItem` is called from `Player.interactOn` and similar callers, not `hurtAndBreak`. A cancelled interaction that breaks a tool must call it itself. [tested]
- 1.20.1: the event bus skips a listener for an event an earlier listener canceled unless it subscribes with `receiveCanceled = true`. A canceled client `RightClickBlock` still lets vanilla send the block-use packet. `RightClickEmpty` fires only on the client. [recalled]
- 1.20.1: `RightClickBlock` fires before vanilla's spectator check; so does `EntityInteractSpecific`. [recalled]
- 1.20.1: `EntityInteractSpecific` fires before an armor stand's own `interactAt`. [tested]
- 1.20.1: `getPersistentData` has no non-creating read. [recalled]
- 1.20.1: `SimpleChannel` prepends a one-byte handler index to the encoded body, which counts against the vanilla packet limit. `IndexedMessageCodec.consume` validates packet direction before decoding. [settled during review; not re-verified]
- 1.20.1: a loot modifier is the normal way to change existing loot, because Forge has no way to add a function to an existing pool. [recalled]
- 1.20.1: `AddLayers.getRenderer` casts generically and throws `ClassCastException` for non-living renderers. Walk `EntityRenderDispatcher.renderers`, widened by an access widener, instead. [tested]
- There is no Forge `FakePlayer` after 1.20.1.

## Fabric

- 1.20.1: Fabric API's `FabricPacket`/`PacketType` play receivers decode using the type's reader and dispatch handlers on the server/client game thread. Typed direct sends exist, but `ServerPlayNetworking.createS2CPacket` takes an id and buffer, not a `FabricPacket`. [source: published [server](https://github.com/FabricMC/fabric/blob/1.20.1/fabric-networking-api-v1/src/main/java/net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking.java) and [client](https://github.com/FabricMC/fabric/blob/1.20.1/fabric-networking-api-v1/src/client/java/net/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking.java) API source]

- `ServerLivingEntityEvents.MOB_CONVERSION` fires only from `Mob.convertTo`. On 1.20.1 villager-to-witch and pig-to-zombified-piglin lightning conversions, and the mooshroom shear, build the new mob by hand and need their own hook. The villager path ends in `addFreshEntityWithPassengers`, the pig path in `addFreshEntity`. [tested]
- `ClientPacketListener`'s send-command methods are injected and run through the client dispatcher, so calling `sendCommand` from a client command recurses without end. To reach the server from a client command, throw one of the two exception types Fabric forwards, such as `BUILT_IN_EXCEPTIONS.dispatcherParseException()`. [source]
- Fabric API's client-command merge (`ClientCommandInternals.copyChildren`, all versions through 26.x) adds each copied node to the received server tree before copying its children. When the server also sends that root literal, Brigadier's same-name merge keeps the server node and the client subtree is attached to a discarded copy, so chat parsing and completion lack it while execution through Fabric's own dispatcher is unaffected. Add whole client trees to `ClientPacketListener.getCommands()` after `handleCommands` instead. [source]
- `UseEntityCallback` fires for both the positioned and the plain interaction packet. [source]
- 1.20.1: `EntityTrackingEvents.START_TRACKING` fires at the head of `ServerEntity.addPairing`, before the entity's spawn packet is sent. A packet about the entity sent from it reaches the client before the entity exists there. [source]
- The use callbacks fire before vanilla's spectator check. [recalled]
- Fabric API loads mod language files on the server, so `Language.getInstance()` can supply fallback text there. [source]
- `ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS` is the counterpart of Forge's `OnDatapackSyncEvent` and fires per player on reload; `END_DATA_PACK_RELOAD` is the once-per-reload signal. [tested]
- 1.20.1: Fabric API's `LootTableEvents` (loot v2) has only `REPLACE`, `MODIFY` and `ALL_LOADED`; there is no event over a roll's finished drops. [source: [published API source](https://github.com/FabricMC/fabric/blob/1.20.1/fabric-loot-api-v2/src/main/java/net/fabricmc/fabric/api/loot/v2/LootTableEvents.java)]
- `getAttachedOrCreate` on a persistent attachment makes a read write to the save. Use `getAttached` for reads. [tested]
- For a tracking broadcast, build one packet with `ServerPlayNetworking.createS2CPacket` and send it with `ServerChunkCache.broadcastAndSend`, the mechanism Forge's `TRACKING_ENTITY_AND_SELF` uses. [tested]
- `ClientPlayConnectionEvents.JOIN` passes the live `ClientPacketListener`; use it instead of looking one up. [tested]
- `BuiltinItemRendererRegistry` renders a registered item from a cancelling `HEAD` injection into `BlockEntityWithoutLevelRenderer.renderByItem`, so another mod's `RETURN` injection there never runs for that item. [recalled]
- A login query reply can carry a version, which distinguishes a version mismatch from a missing mod. [tested]

## Mixin

- In a multi-target `@Mixin({A.class, B.class})`, every injection must exist on every target. A method only `A` declares fails at launch, so split the class. [reasoned, not run]
- An injector with a method list counts matches across the list, so a name absent on one loader does not fail `defaultRequire: 1` while another name matches. [tested]
- `@Pseudo` tolerates a missing target class only, not a missing method in a class that exists. [recalled]
- A Mixin config's package may hold only Mixins. [tested]
- `require`, not `@Pseudo`, decides what a miss does. A config with `required: false` and `defaultRequire: 0` lets an optional integration degrade to nothing. [tested]

## Architectury and Loom

- 1.20.1: common Mixins compile in `common/src/main/java` and need their own refmap, named (e.g. `<modid>-common.refmap.json`) by `loom.mixin.defaultRefmapName` in common; each loader module's refmap covers only Mixins compiled there. The common Mixin config names no `refmap` key, so development runs resolve it by Mojang names; Architectury's production transforms add the refmap to the config, and Forge's transform rewrites it to SRG names, which Forge runs on. A wrong refmap surfaces only at runtime, so check a production-jar launch after changing this. [tested]
- `@ExpectPlatform` needs a per-loader class, so its one-line forwarding is forced. [tested]
- Forge receives the Fabric access widener converted to an access transformer at remap time; one file serves both loaders. [tested] Loom's `forge { convertAccessWideners = true; extraAccessWideners.add <widener name> }` does the conversion by filling `remapJar`'s `atAccessWideners`. [recalled]
- GeckoLib publishes no common artifact for 1.20.1. Common compiles against the Fabric artifact with `modCompileOnly`. Only `GeoRenderEvent` differs by loader. [tested]

## Libraries and other mods

- Maven `VersionRange` reads a bare version as a soft recommendation that matches everything, so it is not a drop-in reader for data where a bare version means an exact one. [recalled]
- Twilight Forest's `AbstractTrophyBlock.use` always consumes the click, so an item's `useOn` never runs on a trophy unless the player sneaks. [recalled]
- GeckoLib 4: the entity compile-render-layers event fires for every `GeoEntityRenderer`, non-living entities included. `BakedGeoModel` is a record and `GeoBone` defines `equals`, so two baked models of the same structure compare equal; key caches by identity. [recalled]

- Twilight Forest 1.20.x: `twilightforest.client.model.tileentity` contains `UrGhastTrophyModel` with face part `body`, `AlphaYetiTrophyModel` with `main`, and `KnightPhantomTrophyModel` with `helmet`. [source: published model files in the [1.20.x source tree](https://github.com/TeamTwilight/twilightforest/tree/1.20.x/src/main/java/twilightforest/client/model/tileentity)]
