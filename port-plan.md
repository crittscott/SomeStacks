# Some Stacks: Minecraft 1.21.5 to 1.21.6 port

## Scope and current state

Port the existing mod to Minecraft 1.21.6 across Fabric, Forge, and NeoForge. This is the next stage toward 1.21.8. Implement against 1.21.6 APIs without anticipating later versions, adding multi-version paths, or redesigning behavior unrelated to the port.

The build configuration and its `build-env/` snapshot target Minecraft 1.21.6, Parchment 2025.06.29 for 1.21.6, Forge 56.0.9, NeoForge 21.6.20-beta, and Fabric API 0.128.2+1.21.6. Java remains 21. Gradle and plugin versions are unchanged. The user reports successful Gradle sync; production compilation, GameTest compilation, and runtime verification against this target remain pending.

This plan follows source inspection and published migration guidance. Confirmed API changes are separated from areas requiring verification. It is not a complete compiler-error inventory. Follow `CLAUDE.md`: do not run Gradle, compile, build, or launch tests unless explicitly asked; do not decompile, unarchive, inspect bytecode, or read Minecraft/Forge remapped or decompiled sources or caches. Resolve API questions through published documentation, permitted library source, or user-supplied signatures and compiler findings.

## Stage 1: Persistence, item serialization, and saved-world migration

Status: source implementation complete; compilation and runtime completion criteria remain pending user-run verification. No Gradle task, build, GameTest, or game launch was run for this stage.

Implemented the `ValueInput`/`ValueOutput` block-entity hooks and subtype metadata, registry-context item codec serialization, record-style version access, and value-input chunk/block update hooks. `StackDataMigration.readTag` is the explicit raw boundary: it preserves malformed field types and heterogeneous inventory entries before migration or client validation. Disk migration still upgrades ordinary and set-aside items and retains the released 1.21.1 unversioned assumption; deferred migration now marks the chunk dirty on attachment. Item encoding fails explicitly with slot, dimension, and position context rather than returning an incomplete inventory.

Adapted the existing shared persistence and synchronization checks, which are already registered on all loaders. Coverage includes current/1.21.4/1.21.5 round-trips, a literal handcrafted 1.21.5 item/component fixture independent of the current serializer, occupied-slot retention, retry precedence, encoding failure, deferred attachment, and malformed updates through both value-input loader hooks. The fixture covers custom data and the 1.21.5 attribute-modifier list without the later optional display field; it is not an exported world fixture. Actual copied-world migration and client chunk/packet behavior remain unverified.

Published loader documentation describes value-input `handleUpdateTag` and `onDataPacket` hooks; Fabric uses the vanilla value-input load lifecycle with the attached client-level branch. Verify initial chunk contents and subsequent packets on each loader at the runtime checkpoint. Public `saveWithoutMetadata(HolderLookup.Provider)` remains in use; load helpers now receive `TagValueInput`.

### Block-entity lifecycle

- Replace `StackBlockEntity.loadAdditional(CompoundTag, HolderLookup.Provider)` and `saveAdditional(CompoundTag, HolderLookup.Provider)` with the 1.21.6 `ValueInput` and `ValueOutput` hooks. Call the corresponding superclass methods.
- Adapt the shared `loadStackData` and `saveStackData` paths and their implementations in `StorageStackBE`, `SinglesStackBE`, and `BarStackBE`. Preserve permanence, Singles item rotations, pending block rotation, comparator contribution refresh, and load-time recovery reporting.
- Use the registry and problem-reporting context supplied by the new persistence API. Where raw tags are needed for migration or validation, use documented codecs and tag-backed input/output facilities at the boundary. Keep that boundary explicit rather than scattering conversions through the inventory and run logic.
- Review `getUpdateTag`, `getUpdatePacket`, `handleUpdateTag`, and `loadClientUpdate` together. Confirm the actual 1.21.6 loader hooks for chunk initialization and later block-entity packets; do not assume the old client-side branch of `loadAdditional` remains sufficient on Fabric.
- Audit save/load convenience methods individually. A change to the protected lifecycle hooks does not establish that every public helper returning a `CompoundTag` has disappeared.

### Item storage

- Replace removed `ItemStack.save` and `ItemStack.parse` calls in `StackItemStorage` with registry-aware `ItemStack.CODEC` encoding and decoding. The existing client decoder already uses `RegistryOps` and the item codec.
- Preserve the saved inventory envelope and integer `Slot` fields unless a demonstrated API constraint requires a format change. Loading old contents must continue to bypass new-item admission rules.
- Keep disk recovery distinct from client validation. Disk loading retains unreadable items, invalid or missing slots, duplicate occupied slots, and non-compound entries in `SetAside`; retry entries after loading ordinary entries. Do not let a typed list reader silently filter malformed entries before recovery sees them.
- Handle encoding failures explicitly so a failed item encode cannot silently become an omitted saved item. Preserve recoverable data through the dedicated persistence/migration path and report useful location context.
- Keep client decoding staged: validate bounded list size, compound entries, unique integer slots, item decoding, version, and Singles rotations before replacing any live state. Reject server-only fields and leave existing contents and rotations untouched on failure.
- Continue stripping `Permanent` and `SetAside` from update data. Client updates must not invoke disk migration, recovery retention, or saved-item warning logging.

### Migration and version access

- Update old `SharedConstants.getCurrentVersion().getDataVersion().getVersion()` usages for the new record-style accessors in `StackDataMigration`, `StackBlockEntity`, and the tests.
- Keep DataFixerUpper item upgrades in `StackDataMigration`, before current-format inventory decoding. Upgrade both ordinary and set-aside item lists, preserve slots around item conversion, and stamp the current version on saves.
- Preserve migration of legacy block rotation into block state, along with dirty marking and session reporting. Preserve state when the level is not yet attached and deferred application is required.
- The changelog currently identifies 0.8.2 for 1.21.1 as the preceding release. Do not replace its unversioned-data assumption or remove its migration merely because 1.21.5 was the preceding development stage. Reassess the supported input only if release information changes.
- Add explicit coverage for data written by the 1.21.5 development baseline. Use an actual prior-format fixture when component structure differs; relabeling current data with an older version alone does not demonstrate conversion of an older representation.

Completion criteria: current saves round-trip; supported released-world data and the 1.21.5 baseline upgrade without lost contents; set-aside entries survive and retry; permanence and rotations persist; client initialization and later updates preserve atomic rejection and expose no server-only fields.

## Stage 2: Server API access and interaction paths

Status: source implementation complete; compilation and runtime verification remain pending user-run checks. No Gradle task, build, GameTest, or game launch was run for this stage.

Replaced all 10 `ServerPlayer.serverLevel()` calls with the covariant `level()` accessor in the four common server classes and Fabric's two protection adapters. Source inspection confirmed that world-edit and protection calls continue receiving `ServerLevel` without added casts. Production and GameTest sources contain no remaining `serverLevel()` calls or old current-version accessor chains.

Reviewed existing-stack, sneaking-rotation, and adjacent-click handling plus the loader callbacks and gesture-state payload. Vanilla hit results, server-side ray selection, main-hand and spectator/protection checks, actor attribution, and mode/modifier synchronization remain unchanged. Destination consultation still refuses recursive consultation, clears its thread-local guard in `finally`, and rejects canceled or block/item-denied Forge/NeoForge events. Fabric still requires `PASS` from destination callbacks and honors optional Common Protection API denial. Existing protection GameTests cover destination cancellation/use denial and guard cleanup; command GameTests cover administrative permission enforcement. They were inspected, not run.

The published 1.21.6 permission-source abstraction retains `hasPermission(int)` on `CommandSourceStack` through `PermissionSource`; the existing gallery and administrative predicates require no migration. Command trees and permission levels are unchanged.

- Replace `ServerPlayer.serverLevel()` with the 1.21.6 covariant `level()` accessor in `WorldEdits`, `StackInteractions`, `StackSounds`, `RotationSoundThrottle`, Fabric's `FabricEditAuthority`, and `CommonProtectionQueries`. Search production and GameTest sources for remaining occurrences.
- Review the types at each replacement, especially protection queries and world edits, so callers continue receiving `ServerLevel` without unnecessary casts.
- Retain ordinary vanilla block-use hits, server-side cell selection, main-hand restrictions, and mode/modifier synchronization. The accessor migration does not require changing the packet protocol or moving actions out of vanilla interaction handling.
- Recheck destination permission consultation and its scoped reentrancy guard. Canceled interactions and block/item-use denial must still prevent neighboring-stack deposits.
- Review command permission call sites against the new permission-source abstraction. Change only signatures or semantics that require migration; do not rewrite functioning command trees merely to use a newly available convenience method.

Completion criteria: no removed server-level or version accessor remains; placement, extraction, rotation, sound throttling, commands, and protection paths use the target APIs with their existing authority and attribution rules.

## Stage 3: Rendering and client integration

Status: source implementation complete; compilation, mixin application, and visual/runtime completion criteria remain pending user-run verification. No Gradle task, build, GameTest, or client launch was run for this stage.

Removed all three redundant `shouldRenderOffScreen(BlockEntity)` overrides; Storage, Singles, and Bar now inherit ordinary renderer culling. The documented 1.21.6 renderer signature still accepts the block entity, partial tick, pose, buffers, light, overlay, and camera position, matching the existing shared render methods and loader registrations.

Reviewed `ItemCapture`, `CubeRenderHelper`, `ModelMeasurement`, `AutoRenderProfiles`, `BarTextureStore`, and the Bar renderer. Published 1.21.6 API signatures retain top-item model resolution, buffered item-state drawing, the baked-quad and individual-vertex consumer interfaces, vertex-format size/offset access, and the item-renderer overload used by FIXED/GUI rendering. The recorder continues to intercept those interfaces; actual geometry emitted by ordinary and special models still requires a client check. Existing transforms, item rotations, tint capture, light/overlay, block drawing, flat-art projection, background/error cubes, and failure handling need no identified source migration. Measurement semantics and cache format remain unchanged at version 3.

Reviewed resource-reload registration on all three loaders. Shared reload callbacks clear model-failure sets, resolved profiles, and Bar sprite UV regions; bar-texture application clears mapped and derived tints; measured profiles retain their initial-load handling and subsequent reload invalidation. No registration change was identified from the inspected code and published migration guidance. Verify model bake/reload ordering and resulting textures in each actual client.

Reviewed Fabric's empty-hand mixin and item/block callbacks, modifier-key registration on all loaders, and Forge/NeoForge client event cancellation. The documented no-argument, void item-use target matches the existing `startUseItem` HEAD injection and `CallbackInfo` handler. Main-hand rules, state synchronization, ordinary block-use packets, Fabric's consuming block result, and Forge/NeoForge denial of both block/item use remain unchanged. Existing gesture and callback checks are retained; mixin remapping/application and held-item suppression remain runtime checkpoints.

Signature review used published [Fabric 1.21.6 API declarations](https://maven.fabricmc.net/docs/yarn-1.21.6+build.1/net/minecraft/client/render/block/entity/BlockEntityRenderer.html), its item-render-state, item-model-manager, item-renderer, vertex-consumer, vertex-format, and client declarations, plus the migration guidance linked below. These checks establish available interfaces, not successful compilation or visual behavior.

- Adapt or remove the `shouldRenderOffScreen(BlockEntity)` overrides in `StorageStackBER`, `SinglesStackBER`, and `BarStackBER`; the 1.21.6 method no longer takes a block entity. All three existing implementations return `false`, so prefer the inherited behavior if it expresses the same result.
- Review renderer registration and the `render` implementation signatures against the target loader APIs. Keep shared rendering in common code where the APIs remain common.
- Review `ItemCapture`'s item-model resolution, render-state drawing, `VertexConsumer` interception, vertex-format offsets, tint capture, and treatment of loose geometry. Confirm that the recorder still observes the geometry used by ordinary and special item renderers.
- Review `CubeRenderHelper`'s FIXED and GUI item drawing, single-block rendering, background cube, and flat-art paths. Preserve transforms, per-item rotations, lighting, color, and failure handling.
- Verify `ModelMeasurement` and `AutoRenderProfiles` against the resulting capture behavior. Invalidate measured caches through the existing format/version mechanism if the port changes the meaning of cached bounds or profiles.
- Verify `BarTextureStore`'s first-quad sprite/tint capture and `BarStackBER`'s texture lookup, UV regions, light, and buffers.
- Do not equate the `gui` item display context with drawing through `GuiGraphics`: this mod draws those items in the world. Its inspected production sources do not use custom HUD drawing or `BlockRenderLayerMap`, so the published HUD and render-layer registration changes are not automatic work items.
- Check client resource reload registration and cache invalidation on all loaders. Confirm that failed-model state and texture-derived caches clear after reload.
- Verify Fabric's `MinecraftMixin` target and injection signature for empty-hand air clicks, plus item/block callbacks, key registration, and Forge/NeoForge client cancellation behavior. Preserve ordinary packets and prevent held-item use from also firing after a consumed stack gesture.

Completion criteria: the renderer overrides match 1.21.6; Storage and Singles render correctly in `2d`, `3d`, `gui`, and `block` modes; Bars retain textures and tints; special models, error cubes, rotations, measurement, and resource reload work on each loader; modifier cycling and block gestures still behave correctly.

## Stage 4: Loader authority, automation, networking, and lifecycle review

Status: source implementation and review complete; compilation, launches, and runtime completion criteria remain pending user-run verification. No Gradle task, build, GameTest, or game launch was run for this stage.

Forge 56.0.9 introduces EventBus 7. Migrated lifecycle, native config, client registration, gesture, capability, and protection adapters to per-event buses and the mod `BusGroup`. Deferred registries receive that group; server ticks use the `Post` event. Annotated listeners use the new listener package and byte priorities; cancellable gesture listeners return booleans. Destination consultation checks the bus's cancellation result and block/item-use denial. Break events receive an initial `Result.DEFAULT`, and removal refuses either cancellation or explicit `Result.DENY`. Capability attachment uses `AttachCapabilitiesEvent.BlockEntities` and retains invalidation. Client subscribers explicitly identify the mod and game bus. Added the pinned MDK's `eventbus-validator:7.0-beta.10` annotation processor for production and GameTests, plus strict EventBus checks in development runs; synchronized `build-env/forge/build.gradle`.

Checked placement against the loaders' own published `Level`/`LevelChunk` patches, snapshot implementations, and placement helpers at the pinned release commits. Capture suppresses `onPlace` and `markAndNotifyBlock`; accepted publication explicitly calls each once, matching the loader helpers. Retained that acceptance path. NeoForge denial now keeps capture enabled during reverse restoration and discards rollback snapshots, preventing the restored block's `onPlace` from scheduling work. Forge already used this approach. Both restore native snapshot block-entity data and clear their placement guard in `finally`. Absence of transient light/comparator changes, drops, and game events still needs runtime verification. Retained first-deposit preflight and the subsequent inventory mutation.

Reviewed shared `preRemoveSideEffects`, the three `affectNeighborsAfterRemoval` hooks, and Bar's drop/cascade path. Content drops still precede surviving-run repair; same-block state updates retain their guards. Reviewed whole-run capability/Transfer API exposure, positional support, headroom growth, simulation, and `RunEdit` refusal. Fabric still shares a bottom-block ledger, restores nested snapshots, and replays net changes only at outer commit. No automation algorithm or slot-numbering change was made. Forge actor cleanup remains wired to level unload and server stop through the new buses; no Forge FakePlayer dependency was added. NeoForge's native unload handler clears its factory; Fabric's factory uses weak values. Player/automation attribution is unchanged.

Reviewed receiver threading: Forge `addMain` queues work on the receiving main thread; the pinned NeoForge registrar defaults to `MAIN` and still accepts a client handler directly in `playToClient`; Fabric's object payload receivers run on the server/client threads. Do not adopt the newer client-handler registration in the combined 1.21.6–1.21.8 documentation for this pinned NeoForge release. Fabric's configuration-phase marker/checks only query networking and disconnect. Login sync, disconnect clearing, bounded config decoding, atomic staging, and change-driven gestures remain unchanged.

The pinned Forge block-entity interface takes an additional `HolderLookup.Provider` argument on both update hooks, unlike NeoForge. Added those actual Forge signatures so chunk updates and later packets reach staged validation directly, using the registry context carried by `ValueInput`. Extended existing synchronization checks to exercise valid and rejected updates through both loaders' signatures. Migrated Forge protection/callback checks to EventBus 7 predicates, result denial, and returned listener handles. Extended the existing Forge/NeoForge rollback check to restore an empty permanent Storage Stack without scheduling its placement/settlement tick. Checks were inspected, not run.

Reviewed native SERVER config registration, load/reload dispatch onto the server thread, cached policy rebuilding, and resynchronization; Fabric still reloads per-world JSON through `/ss reload`. Common Protection API 1.0.0's published source retains the placement/interaction query signatures used here and declares no mixin, entrypoint, or Minecraft metadata restriction. Its adapter remains isolated behind the loaded-mod check; development runs and provider-backed/absence-specific checks remain wired. Kept version 1.0.0. Actual execution with and without it remains required; metadata and signature agreement do not establish runtime compatibility.

Source references: [Forge EventBus 7 migration guide](https://gist.github.com/PaintNinja/ad82c224aecee25efac1ea3e2cf19b91); [Forge 56.0.9 source commit](https://github.com/MinecraftForge/MinecraftForge/tree/d5c5df1b64b0f87be4b033663e024b033d89c353); [NeoForge 21.6.20-beta source commit](https://github.com/neoforged/NeoForge/tree/bcb2b65d7035bd5c07cfa9a52491eeb551a1929c); [Fabric 1.21.6 API](https://maven.fabricmc.net/docs/fabric-api-0.128.2%2B1.21.6/); [Common Protection API 1.0.0 source](https://github.com/Patbox/common-protection-api/tree/0e4f603de719c20783c1c2248f846925d35a6742). Only published loader/library source and patches were inspected; no archives, bytecode, decompiled Minecraft/Forge source, or loader caches were read.

- Check Forge and NeoForge placement snapshot capture, denial rollback, and accepted publication against their published 1.21.6 facilities. In particular, verify the explicit `onPlace` and `markAndNotifyBlock` calls still match the loader lifecycle and do not publish changes twice.
- Verify that rejected placement restores block-entity contents without unintended drops, neighbor updates, comparator changes, light changes, or sculk events. A successful placement and first deposit must still commit together.
- Review `preRemoveSideEffects` and `affectNeighborsAfterRemoval` integration for all stack types. Exercise ordinary breaking, replacement, Bar cascades, and state-only updates; retain the existing separation of content drops from surviving-run repair unless the target lifecycle requires a change.
- Verify Forge's synthetic automation player and NeoForge/Fabric fake-player factories, including actor cleanup and player-versus-automation attribution. Do not introduce a Forge FakePlayer dependency.
- Verify Forge/NeoForge capability exposure and Fabric Transfer API adaptation. Preserve whole-run slot numbering, supported positional insertion, headroom growth, simulation without mutation, reentrancy refusal, and Fabric outer-transaction commit behavior.
- Review loader payload registration, receiver threading, configuration-phase protocol checks, login/disconnect handling, and reload synchronization. Retain bounded packet decoding, atomic config publication, and the existing small change-driven gesture payload.
- Verify Fabric with and without Common Protection API 1.0.0. Its lack of a Minecraft metadata restriction is not runtime verification. Check placement and adjacent-use queries; change the dependency only if an actual API or runtime incompatibility requires it.
- Recheck native Forge/NeoForge server-config lifecycle and Fabric JSON reload behavior. Preserve per-world policy and resynchronization after changes.

Completion criteria: each loader compiles and launches independently; ordinary and automated edits honor denial and rollback; automation remains transactional; clients receive policy and inventory updates correctly; Fabric passes both optional-integration configurations.

## Stage 5: Pack metadata, resources, and GameTests

Status: source/resource implementation and review complete; pack loading, GameTest compilation, discovery, and execution remain pending user-run verification. No Gradle task, build, GameTest, or game launch was run for this stage.

Updated the common resource pack to format 63 and Forge/NeoForge test data packs to format 80. Fabric packages test data through its development mod and has no separate test pack declaration; its metadata inherits the target through the required production mod. Strict parsing checked all 373 source JSON/metadata files, with duplicate-key and non-finite-number rejection, including both expanded Fabric optional-protection entrypoint variants. No JSON content conversion was needed. These checks establish syntax, not successful game loading.

Reviewed the shared persistence/synchronization checks and scaffolding: stage 1 already replaced removed item-save/version calls and supplied `TagValueInput` to `loadWithComponents`/`loadCustomOnly`; public `saveWithoutMetadata(HolderLookup.Provider)` remains the save entry point. Existing checks retain malformed types, duplicate slots, invalid rotations, server-only fields, set-aside retention/retry, current and previous formats, and the handcrafted 1.21.5 component fixture. Stage 4 already adjusted loader update-hook and rollback coverage. No additional Java migration or behavior change was identified in this stage.

Reviewed development source-set/run wiring, Forge annotations, Fabric entrypoint classes in both protection configurations, and NeoForge deferred functions/test instances. All 123 NeoForge instance definitions refer one-to-one to registered functions and retain the shared structure, default environment, and positive timeouts. Fabric test entrypoint source references resolve in both configurations. Existing denial, state-only, removal, automation, comparator, lighting, and callback checks remain registered; no binary fixture was inspected or unpacked. Test sources remain separate from ordinary production compilation. Actual discovery and execution are still required.

- Change `common/src/main/resources/pack.mcmeta` from resource-pack format 55 to 63.
- Change Forge and NeoForge GameTest `pack.mcmeta` files from data-pack format 71 to 80. Review Fabric's packaged test resources and loader metadata for any additional target declarations.
- Check shipped JSON resources and test-instance definitions against strict JSON parsing. Convert any incompatible repository data deterministically and in place; do not add readers for obsolete formats.
- Adapt removed item-save calls, version accessors, and `loadWithComponents` calls in `CapabilityAndPersistenceChecks`, `StoragePileChecks`, and other affected checks. Review `GameTestScaffold`'s save helpers against the actual target signatures.
- Keep existing persistence and synchronization checks exercising the real disk and wire entry points after Stage 1. Cover malformed types, duplicate slots, invalid rotations, server-only fields, set-aside retry, and current/previous-format saves.
- Review loader GameTest discovery and registration: Fabric annotations/entrypoints, Forge annotations, and NeoForge deferred functions plus test-instance JSON. Keep the existing shared structure unless the target format actually requires a change; do not unarchive or manually inspect binary fixtures.
- Retain behavior checks for denial rollback, state-only changes, removal cascades, automation, comparators, lighting, and callback handling. Add or adjust tests where the port changes a behavior path, with reproduction instructions in their Javadocs.
- Keep GameTest compilation separate from ordinary production compilation; the development-only test source sets are excluded from the normal build.

Completion criteria: packs load without stale format declarations or JSON errors; all loader test source sets compile; tests are discovered and executed on each loader, including both Fabric protection configurations.

## Stage 6: Documentation and verification record

Status: documentation implementation complete; verification record updated from available evidence. No new user-run compilation or runtime results were supplied during stages 5–6.

Updated both orientation snapshots, build environment description, unreleased changelog, README/CurseForge branch status, server administration, and pack-author guidance for the 1.21.6 target and pack formats. Fixed orientation headers are preserved and content remains within the size limits. No active Gradle inputs changed in stages 5–6; their `build-env/` copies need no update. Generated Javadoc was not regenerated because that invokes Gradle.

Verification record: Gradle sync was reported successful before stage 4’s Forge validator/run changes. Source/resource review and strict JSON/reference checks are complete. Production compilation, separate GameTest compilation for all loaders, GameTest execution (including both Fabric protection configurations), copied-world migration/save/reload, client/dedicated-server launches, networking, protection rollback, rendering, and resource reload remain pending. No failures from those unexecuted checkpoints can yet be reported; stages 1–5 retain their runtime completion criteria.

- Update `orientation-code.md` and `orientation-player.md` as implementation changes alter their descriptions, preserving their fixed headers and size limits. Their current resource-format and pending-port statements must reflect the completed work.
- Update `build-env.md` for any build or pack integration details changed during the port. Synchronize any changed active Gradle input with its matching `build-env/` copy.
- Revise the unreleased changelog and relevant README/server/resource-pack documentation for the 1.21.6 target and actual changes. Do not describe sync success as source or runtime verification.
- Record each stage's implementation status and actual user-run verification results in this plan. Keep unresolved failures explicit rather than marking a stage complete after source inspection alone.

## Verification sequence

The user runs builds and tests unless execution is explicitly delegated. Before that checkpoint, inspect each change and its callers. Do not clear caches, change the machine's JDK configuration, kill processes, or alter the development environment as a speculative fix.

1. After the production API changes, compile common and all three loader production modules. Resolve actual compiler findings in the appropriate shared or loader-specific implementation.
2. Compile the GameTest source sets for all three loaders separately. A successful production build does not establish their correctness.
3. Run server GameTests for Fabric, Forge, and NeoForge. Run Fabric both without Common Protection API and with `-PwithCommonProtectionApi=true`.
4. Load a copied world from the supported preceding release and a copied 1.21.5 development world. Check inventory/components, permanence, block and item rotations, set-aside recovery, comparator/light state, and a subsequent save/reload.
5. Launch client and dedicated-server configurations for each loader. Check join, initial chunk contents, subsequent inventory/config updates, disconnect/reconnect, and resource reload.
6. Exercise modifier cycling, deposits, extraction, adjacent deposits, layout/item rotation, waterlogging, shapes, sound, lighting, Bar collapse, and automation growth/removal. Include protection denial and rollback.
7. Exercise all four item rendering modes with ordinary items, tinted items, enchanted items, blocks, player heads, and other special models. Check measured profiles, saved overrides, missing resources, and the existing error-model behavior where a reproducible fixture is available.
8. Update documentation and stage status from the observed results. Gradle sync is already reported successful; compilation, GameTest execution, saved-world migration, and client behavior are distinct remaining checkpoints.

## Published references

- [Minecraft 1.21.5 to 1.21.6 porting primer](https://docs.neoforged.net/primer/docs/1.21.6/): persistence abstraction, removed item serialization methods, server-player and version accessors, renderer overrides, and other API changes.
- [NeoForge 21.6 release guidance](https://neoforged.net/news/21.6release/): value input/output and tag-backed serialization facilities.
- [Fabric for Minecraft 1.21.6, 1.21.7, and 1.21.8](https://fabricmc.net/2025/06/15/1216.html): Fabric API changes and the Minecraft persistence/rendering transition. Its examples use Yarn names; this project uses Mojang mappings.
- [Minecraft Java Edition 1.21.6 technical changes](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-6): resource-pack format 63, data-pack format 80, strict JSON parsing, and item/resource changes.
