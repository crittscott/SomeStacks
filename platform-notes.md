# Platform notes

Facts about how specific Minecraft versions, loaders, and libraries behave, learned the hard way. General rules belong in `CLAUDE.md` and `code-conventions.md`; this file holds what is true only of a particular platform. Nothing here is about one mod.

Every fact is scoped to the version it was learned on unless it says otherwise. Do not assume it holds on another version; check it when porting.

Each fact ends with how it is known:

- **[tested]**: a build, gametest, or in-game check depended on it.
- **[source]**: read from published source (Fabric API, a library, a JVM spec).
- **[recalled]**: from memory, unconfirmed. Treat as a hypothesis.

Upgrade the mark when a build or test settles a fact, and correct or delete a fact that turns out wrong.

## Minecraft

### Rendering

- 1.20.1 and 1.21.1 draw items immediately and have no use for item render state code; 1.21.4 and later use it. [tested on 1.20.1]
- 1.20.1: the render pose holds the view rotation `V = Rx(pitch) Ry(yaw + 180)`, and `cameraOrientation` is `Ry(-yaw) Rx(pitch)`. The true inverse of `V` is `cameraOrientation * Ry(180)`; without the 180 degree turn, pitch doubles instead of canceling. [tested in game; the matrices themselves are recalled]
- The view convention differs by version, so derive what each version's pose holds instead of copying a fix. 1.21.1, 1.21.4, and 1.21.8 pass `cameraOrientation`; 1.21.11 and later have been treated as world-space poses. [recalled]
- 26.3: `PoseStack.rotate(quaternion)` multiplies the pose by the quaternion without modifying it, so one cached orientation can be passed on every draw. [recalled]
- `ItemColor` is asked once per tinted quad, and `item/generated` makes a quad for each edge pixel run, so a tint callback can run dozens of times per item per frame. [recalled]

### Interaction and items

- 1.20.1: `Player.mayUseItemAt(pos, face, stack)` tests the block at `pos.relative(face.getOpposite())`. Callers pass the position next to the clicked block: `clicked.relative(face)`. [tested]
- 1.20.1: `ServerPlayer.canHarmPlayer` already includes the server's PvP setting. [tested]
- 1.20.1: the server swings the arm when the interaction result is `SUCCESS`, not `CONSUME`. Server paths return `CONSUME`. [recalled]
- `ItemStack.useOn` awards `Stats.ITEM_USED`, and `ServerPlayerGameMode.useItemOn` fires `ITEM_USED_ON_BLOCK`. A cancelled click event returns before both, so a handler that takes over must award them itself. [recalled]
- `LivingEntity.heal` is called without a health check by some vanilla code, for example horse regeneration. [recalled]
- `Player` overrides `LivingEntity.actuallyHurt` without calling super, so an injection on `LivingEntity` misses players. [tested]
- `dropCustomDeathLoot` and the loot table drop run only inside the `shouldDropLoot() && doMobLoot` gate in `dropAllDeathLoot`. A drop rolled by mod code is not covered by that gate. [tested]
- `LivingEntity.hurt` returns false on the client before `actuallyHurt`, and the client's trade menu uses `ClientSideMerchant`, not an `AbstractVillager`. [recalled]
- `ItemStack.isSameItemSameTags` is the 1.20.1 name; later versions need the component equivalent. [tested on 1.20.1]
- The name of `DamageSource.isDirect` appears to differ between release lines. [recalled]

### Data and block entities

- 1.21.x and later: the client ignores a block-entity data packet whose tag is empty (vanilla's handler and NeoForge's `onDataPacket` both check `isEmpty`), so state that clears to "save nothing" must still put something in the update tag or clients keep the old state. 1.20.1 behaves the same: an empty update tag reaches its client as no tag, which the client skips. [recalled; head-shearing fix tested in game on 1.21.1/1.21.4]
- 26.3: `ClientboundBlockEntityDataPacket.create(blockEntity)` takes its tag from `getUpdateTag` with the block entity's level registries, and the client applies a non-empty tag through `loadWithComponents`. [recalled]
- 1.20.1: `BlockItem` merges a stack's `BlockEntityTag` into the block entity and calls `load`, and heads are not an op-only block entity. A creative player needs no operator rights to supply that NBT, so a `load` that keeps a whole compound stores and rebroadcasts whatever was sent. [recalled]
- A non-special recipe needs an unlock advancement, or `doLimitedCrafting` blocks it and the recipe book never offers it. The path is `data/<ns>/advancements/recipes/...` on 1.20.1 and uses the `advancement` directory on later versions. [tested on 1.20.1]
- 26.2: `CriteriaTriggers` moved from `net.minecraft.advancements` to `net.minecraft.advancements.triggers`, and `BlockPredicate` from `net.minecraft.advancements.criterion` to `net.minecraft.advancements.predicates`. [source, NeoForged 26.2 primer]
- 26.3: the `minecraft:recipe_unlocked` trigger reads `conditions.recipes`, a recipe holder set, in place of `recipe`. The old key fails the whole registry load, so no server starts. [tested]

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
- 1.20.1: `onPlayerDestroyItem` is called from `Player.interactOn` and similar callers, not `hurtAndBreak`. A cancelled interaction that breaks a tool must call it itself. [tested]
- 1.20.1: `RightClickBlock` fires before vanilla's spectator check; so does `EntityInteractSpecific`. [recalled]
- 1.20.1: `EntityInteractSpecific` fires before an armor stand's own `interactAt`. [tested]
- NeoForge 26.2.0.57: `PlayerInteractEvent.EntityInteractSpecific` does not exist, so `EntityInteract` is the entity-click event. [tested]
- 1.20.1: `getPersistentData` has no non-creating read. [recalled]
- 1.20.1: `SimpleChannel` prepends a one-byte handler index to the encoded body, which counts against the vanilla packet limit. `IndexedMessageCodec.consume` validates packet direction before decoding. [settled during review; not re-verified]
- 1.20.1: a loot modifier is the normal way to change existing loot, because Forge has no way to add a function to an existing pool. [recalled]
- 1.20.1: `AddLayers.getRenderer` casts generically and throws `ClassCastException` for non-living renderers. Walk `EntityRenderDispatcher.renderers`, widened by an access widener, instead. [tested]
- There is no Forge `FakePlayer` after 1.20.1.

## Fabric

- `ServerLivingEntityEvents.MOB_CONVERSION` fires only from `Mob.convertTo`. On 1.20.1 villager-to-witch and pig-to-zombified-piglin lightning conversions, and the mooshroom shear, build the new mob by hand and need their own hook. The villager path ends in `addFreshEntityWithPassengers`, the pig path in `addFreshEntity`. [tested]
- `ClientPacketListener`'s send-command methods are injected and run through the client dispatcher, so calling `sendCommand` from a client command recurses without end. To reach the server from a client command, throw one of the two exception types Fabric forwards, such as `BUILT_IN_EXCEPTIONS.dispatcherParseException()`. [source]
- Fabric API's client-command merge (`ClientCommandInternals.copyChildren`, all versions through 26.x) adds each copied node to the received server tree before copying its children. When the server also sends that root literal, Brigadier's same-name merge keeps the server node and the client subtree is attached to a discarded copy, so chat parsing and completion lack it while execution through Fabric's own dispatcher is unaffected. Add whole client trees to `ClientPacketListener.getCommands()` after `handleCommands` instead. [source]
- `UseEntityCallback` fires for both the positioned and the plain interaction packet. [source]
- The use callbacks fire before vanilla's spectator check. [recalled]
- Fabric API loads mod language files on the server, so `Language.getInstance()` can supply fallback text there. [source]
- `ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS` is the counterpart of Forge's `OnDatapackSyncEvent` and fires per player on reload; `END_DATA_PACK_RELOAD` is the once-per-reload signal. [tested]
- `getAttachedOrCreate` on a persistent attachment makes a read write to the save. Use `getAttached` for reads. [tested]
- For a tracking broadcast, build one packet with `ServerPlayNetworking.createS2CPacket` and send it with `ServerChunkCache.broadcastAndSend`, the mechanism Forge's `TRACKING_ENTITY_AND_SELF` uses. [tested]
- 26.3: the tracking-and-self send is `ServerChunkCache.sendToTrackingPlayersAndSelf(Entity, Packet)`; 26.1.2 has no `broadcastAndSend`, and 26.3 is assumed to match. [recalled]
- Fabric API 0.163.0+26.3: `LootTableEvents.MODIFY_DROPS` runs after a loot table rolls, with `(table, context, drops)` and a mutable drop list, so a listener can change individual stacks instead of adding a function to every pool. [recalled]
- 26.3: `EntityTrackingEvents.START_TRACKING` fires at the tail of `ServerEntity.addPairing` (fabric-networking-api-v1 `ServerEntityMixin` at Fabric API 0.163.0+26.3, `@At("TAIL")`), after the entity's spawn packet is sent, so a packet about the entity sent from it reaches a client that already has the entity. `STOP_TRACKING` fires at the head of `removePairing`. [source]
- `ClientPlayConnectionEvents.JOIN` passes the live `ClientPacketListener`; use it instead of looking one up. [tested]
- A login query reply can carry a version, which distinguishes a version mismatch from a missing mod. [tested]
- Fabric API 0.163.0+26.3: networking classes use `createClientboundPacket`, packet contexts expose `packetListener()`, and `PayloadTypeRegistry` uses `clientboundPlay`, `serverboundPlay`, `clientboundConfiguration`, and `serverboundConfiguration`. [source]
- Fabric API 0.163.0+26.3: `SYNC_DATA_PACK_CONTENTS` passes `(player, joined)` and fires for joining players as well as successful reloads. [source]

## Mixin

- In a multi-target `@Mixin({A.class, B.class})`, every injection must exist on every target. A method only `A` declares fails at launch, so split the class. [reasoned, not run]
- An injector with a method list counts matches across the list, so a name absent on one loader does not fail `defaultRequire: 1` while another name matches. [tested]
- `@Pseudo` tolerates a missing target class only, not a missing method in a class that exists. [recalled]
- A Mixin config's package may hold only Mixins. [tested]
- `require`, not `@Pseudo`, decides what a miss does. A config with `required: false` and `defaultRequire: 0` lets an optional integration degrade to nothing. [tested]

## Architectury and Loom

- 26.3: the build applies `dev.architectury.loom-no-remap`, so nothing is remapped and no refmap exists. Mixin configs carry no `refmap` key, common's `build.gradle` sets no refmap name, and Mixins target the unobfuscated names directly. [source: active build scripts and Mixin configs]
- `@ExpectPlatform` needs a per-loader class, so its one-line forwarding is forced. [tested]
- 26.3: the no-remap build uses one common access widener. Fabric injects it into `shadowJar` with `loom.injectAccessWidener`; Forge and NeoForge convert it on their Shadow tasks through `loom.getForge().convertAccessWideners` and `loom.getNeoForge().convertAccessWideners`. There is no `remapJar` task. [source: active loader build scripts; owner reports successful builds, production rules not independently inspected]
- GeckoLib publishes no common artifact for 1.20.1. Common compiles against the Fabric artifact with `modCompileOnly`. Only `GeoRenderEvent` differs by loader. [tested]

## Libraries and other mods

- Maven `VersionRange` reads a bare version as a soft recommendation that matches everything, so it is not a drop-in reader for data where a bare version means an exact one. [recalled]
- GeckoLib's 26.1 branch: `GeoEntityRenderer.createRenderState(entity, partialTick)` is `@ApiStatus.Internal` and final, but not marked for removal. [source]
- Twilight Forest's `AbstractTrophyBlock.use` always consumes the click, so an item's `useOn` never runs on a trophy unless the player sneaks. [recalled]
- GeckoLib 5.4 runs `PerBoneRender` tasks after `RenderUtil.transformToBone`, which ends at the bone's pivot; GeckoLib 5.2 gave them the bone's frame at the model origin. A pose set in a renderer's `preRenderPass` (such as a model scale) is included. [GeckoLib source; tested on 1.21.11]
