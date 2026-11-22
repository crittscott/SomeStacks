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
- `PoseStack.mulPose` multiplies the pose by its quaternion without modifying the argument, so one cached quaternion can be passed on every draw. [recalled]
- `ItemColor` is asked once per tinted quad, and `item/generated` makes a quad for each edge pixel run, so a tint callback can run dozens of times per item per frame. [recalled]

### Interaction and items

- 1.20.1: `Player.mayUseItemAt(pos, face, stack)` tests the block at `pos.relative(face.getOpposite())`. Callers pass the position next to the clicked block: `clicked.relative(face)`. [tested]
- 1.20.1: `ServerPlayer.canHarmPlayer` already includes the server's PvP setting. [tested]
- 1.20.1: the server swings the arm when the interaction result is `SUCCESS`, not `CONSUME`. Server paths return `CONSUME`. [recalled]
- `ItemStack.useOn` awards `Stats.ITEM_USED`, and `ServerPlayerGameMode.useItemOn` fires `ITEM_USED_ON_BLOCK`. A cancelled click event returns before both, so a handler that takes over must award them itself. [recalled]
- `LivingEntity.heal` is called without a health check by some vanilla code, for example horse regeneration. [recalled]
- `Player` overrides `LivingEntity.actuallyHurt` without calling super, so an injection on `LivingEntity` misses players. [tested]
- 1.21.1: a hit a shield blocks still reaches `actuallyHurt`, with its amount set to zero. [recalled]
- `dropCustomDeathLoot` and the loot table drop run only inside the `shouldDropLoot() && doMobLoot` gate in `dropAllDeathLoot`. A drop rolled by mod code is not covered by that gate. [tested]
- `LivingEntity.hurt` returns false on the client before `actuallyHurt`, and the client's trade menu uses `ClientSideMerchant`, not an `AbstractVillager`. [recalled]
- `ItemStack.isSameItemSameTags` is the 1.20.1 name; later versions need the component equivalent. [tested on 1.20.1]
- The name of `DamageSource.isDirect` appears to differ between release lines. [recalled]

### Data and block entities

- 1.21.x: the client ignores a block-entity data packet whose tag is empty (vanilla's handler and NeoForge's `onDataPacket` both check `isEmpty`), so state that clears to "save nothing" must still put something in the update tag or clients keep the old state. 1.20.1 behaves the same: an empty update tag reaches its client as no tag, which the client skips. [recalled; matches in-game head-shearing behavior on 1.21.1/1.21.4]
- 1.21.1: an item's data component values are immutable; `ItemStack.set` stores a new value, and `get` returns the stored instance until then, so a weak identity-keyed cache by component value goes stale only by losing its key. [recalled]
- 1.20.1: `ResourceLocation.isValidNamespace` is private [tested on 1.20.1: compile error]; its visibility on 1.21.1 has not been checked, so namespaces are tested with `ResourceLocation.tryBuild(namespace, "x") != null`.
- 1.20.1: `BlockItem` merges a stack's `BlockEntityTag` into the block entity and calls `load`, and heads are not an op-only block entity. A creative player needs no operator rights to supply that NBT, so a `load` that keeps a whole compound stores and rebroadcasts whatever was sent. [recalled]
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
- 1.20.1: `onPlayerDestroyItem` is called from `Player.interactOn` and similar callers, not `hurtAndBreak`. A cancelled interaction that breaks a tool must call it itself. [tested]
- 1.21.1: Forge fires `PlayerDestroyItemEvent` from `hurtAndBreak` itself, but not from `ItemStack.consume`, so a mod path that uses up a stack calls `ForgeEventFactory.onPlayerDestroyItem(Player, ItemStack, InteractionHand)`. [the first half is what the Forge gametest `harvestBreakingShearsReportsThem` relies on; the method signature is recalled]
- 1.20.1: `RightClickBlock` fires before vanilla's spectator check; so does `EntityInteractSpecific`. [recalled]
- 1.20.1: `EntityInteractSpecific` fires before an armor stand's own `interactAt`. [tested]
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
- 1.21.1: `EntityTrackingEvents.START_TRACKING` fires at the head of `ServerEntity.addPairing` (fabric-networking-api-v1 `EntityTrackerEntryMixin`), before the entity's spawn packet is sent. A packet about the entity sent from it reaches the client before the entity exists there. [source]
- Fabric API 0.102.1+1.21.1: `LootTableEvents` declares only `REPLACE`, `MODIFY`, and `ALL_LOADED`; there is no `MODIFY_DROPS`, so a loot function added through `MODIFY` applies to every item its pool rolls. [source]
- 1.21.1 `ByteBufCodecs` has no fixed-width `LONG` (only `VAR_LONG`); an eight-byte long field uses `StreamCodec.of(ByteBuf::writeLong, ByteBuf::readLong)`. [compiled]
- `ClientPlayConnectionEvents.JOIN` passes the live `ClientPacketListener`; use it instead of looking one up. [tested]
- A login query reply can carry a version, which distinguishes a version mismatch from a missing mod. [tested]

## Mixin

- In a multi-target `@Mixin({A.class, B.class})`, every injection must exist on every target. A method only `A` declares fails at launch, so split the class. [reasoned, not run]
- An injector with a method list counts matches across the list, so a name absent on one loader does not fail `defaultRequire: 1` while another name matches. [tested]
- `@Pseudo` tolerates a missing target class only, not a missing method in a class that exists. [recalled]
- A Mixin config's package may hold only Mixins. [tested]
- `require`, not `@Pseudo`, decides what a miss does. A config with `required: false` and `defaultRequire: 0` lets an optional integration degrade to nothing. [tested]

## Architectury and Loom

- Common Mixins compile in `common/src/main/java` and need their own refmap, named by `loom.mixin.defaultRefmapName` in common and by the `refmap` key in the common Mixin config: Fabric resolves them against intermediary names, and the Fabric module's refmap covers only Mixins compiled there. Forge and NeoForge run on Mojang names, so their jars exclude that refmap and their dev runs set `mixin.env.disableRefMap`. A wrong refmap surfaces only at runtime, so check a production-jar launch after changing this. [tested]
- `@ExpectPlatform` needs a per-loader class, so its one-line forwarding is forced. [tested]
- Forge and NeoForge receive the Fabric access widener converted to an access transformer at remap time; one file serves every loader. [tested] Loom's `forge { convertAccessWideners = true; extraAccessWideners.add <widener name> }` does the conversion by filling `remapJar`'s `atAccessWideners`; NeoForge sets `atAccessWideners` directly because whether its extension has the same switches is unknown. [recalled]
- GeckoLib publishes no common artifact for 1.20.1. Common compiles against the Fabric artifact with `modCompileOnly`. Only `GeoRenderEvent` differs by loader. [tested]

## Libraries and other mods

- Maven `VersionRange` reads a bare version as a soft recommendation that matches everything, so it is not a drop-in reader for data where a bare version means an exact one. [recalled]
- Twilight Forest's `AbstractTrophyBlock.use` always consumes the click, so an item's `useOn` never runs on a trophy unless the player sneaks. [recalled]
