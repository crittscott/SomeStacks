# Code review work split

This file divides the verified findings from the `review-*.md` files into lanes that can run at the same time. Each lane owns a set of files. Lanes in the same wave do not edit each other's files except where a row below says so. A later wave starts once the lanes it depends on are done. The per-finding detail lives in each review file's verdict and `## Plan` section, and a lane's brief should point at those.

Finding IDs: Build, Corr (Correctness), Dead (Dead-code), Eff (Efficiency), Exp (Expected-way), Guard (Guarding), Jdoc (Javadocs), Leg (Legacy), LD (Loader-divergence), Log (Logging), Mag (Magic-numbers), MLO (Multiloader-organization), Net (Networking), Org (Organization), Prot (Protection), Rein (Reinvention), Test (Test-completeness). "Mag TOML" is the extra TOML-range item in the Magic-numbers plan.

## Shared files

Several lanes touch these files. Each lane edits only its own entries, and re-reads the current text immediately before editing.

- `en_us.json`: lane 5 owns the file. Lane 1 adds one new key (`type_disabled`).
- `orientation-code.md`, `orientation-player.md`, `docs/*`, `CHANGELOG.md`: each lane updates only the rows and paragraphs that describe the code it changed.
- `build-env/`: lane 7 owns it. Any other lane that changes a build file it snapshots tells lane 7.

## Wave 1: six code lanes and the build lane, all parallel

### Lane 1: Gestures and gesture state (client and server)
Owns: `server/StackInteractions`, `server/StackSounds`, `client/interaction/*` (rules, `InteractionRuleRegistry`, context), `client/ClientGestures`, `client/ClientServerSettings`, `StackMode`, `GestureStatePkt`, `ConfigSyncPkt`, Forge `ClientEvents`, `StackInteractionEvents`, `ModNetworking`, Fabric `FabricClientEvents`, `FabricStackInteractionEvents`, `mixin/MinecraftMixin`. Tests: `ClientGestureChecks`, `NetworkPolicyChecks`, `InteractionChecks`, Forge `ClientCallbackGameTests`.

Order:
1. Net #4: stable wire ids and an explicit cycle order (protocol bump).
2. Corr #2, #5: keep the selected mode valid, and explain silent refusals.
3. Corr #1: one modifier item-use rule shared by client and server (needs owner OK).
4. Leg #1, #3: trim the client classifier and share each gesture condition.
5. Guard #3, Guard #6 (the `cycleMode` half only), Corr #4.
6. Net #5, Test #10.
7. Corr #3 (owner decision).

### Lane 2: Server config
Owns: `ServerConfig`, `TomlPolicyFile`, `ServerConfigMigration`, Forge `ForgeServerConfig`, Fabric `FabricServerConfig`, the config sections of `docs/server-admin.md` and `CHANGELOG.md`. Tests: `TomlPolicyChecks`, `PolicyParityChecks`, `ServiceAndConfigChecks` (config cases).

Order:
1. MLO #1, #2 with Leg #2: one settings declaration, then the Fabric-only TOML and migration move to Fabric.
2. Mag TOML: share the range constants.
3. Log #4, Log #5.
4. Test #8.
5. Mag #10 (`ServerConfigMigration` import only).

Removing night-config's `compileOnly` from `common/build.gradle` goes to lane 7, after lane 2 finishes moving the code.

### Lane 3: Blocks, block entities, storage, and automation adapters
Owns: `block/*StackBlock`, `blockentity/*` (`StackBlockEntity`, `StorageStackBE`, `SinglesStackBE`, `BarStackBE`, `StoragePile`, `StackRun`, `BarDropBatch`, `StackKey`, `StackItemStorage`), `ItemOps`, `StackPlacement`, Forge `RunItemHandler`, Fabric `FabricRunItemStorage`, `AutomationActor`. Tests: `StoragePileChecks`, `BarColumnChecks`, `SinglesColumnChecks`, `StackFoundationChecks`, `WorldEditChecks`, and the Fabric Transfer tests.

Order:
1. Prot #2: attribute break-driven cleanup to the breaking player (bug, with a failing test).
2. Dead #1 with Eff #1: one group-and-re-cut helper, without needless copies.
3. Leg #5 with LD #2, then Eff #2 and LD #3.
4. Exp #3, Guard #2, Mag #8, Org #7.
5. Org #1: the `StackBlock` base class.

Leg #5 renames methods that `ClientGestureChecks` calls. Edit those call sites after lane 1 has finished rewriting that file, or hand them to lane 1.

`StackRun`'s growth gate (Org #2) is wave 2 work, not this lane's.

### Lane 4: Client rendering and measurement
Owns: `CubeRenderHelper`, the block entity renderers (`BarStackBER` and others), `ModelMeasurement`, `ClientRenderPlatform`, `CubeGrid`, `*CubeIdx` (constants and `footprintsOverlap`), Forge `ModelMeasurer`, `ForgeRenderPlatform`, Fabric `FabricModelMeasurer`, `FabricClientRendering`.

Order:
1. Mag #2, #3.
2. Exp #2, Exp #4.
3. MLO #3.
4. Mag #1.
5. Guard #6 (`ModelMeasurer` half), Rein #3.

There are no gametests. The owner checks the result visually in MultiMC.

### Lane 5: Render overrides, commands, and player text
Owns: `ItemRenderOverrides`, `OverrideJsonCodec`, `BarTextureStore`, `AutoRenderProfiles`, `ClientRenderCommands`, `SsCommand`, `SsHelp`, `RenderGalleryGenerator`, `en_us.json`.

Order:
1. Log #1: never overwrite an unreadable override file. This is the data-loss bug, and the owner decides whether malformed entries are preserved.
2. Rein #2.
3. Mag #4, #6 with Guard #5.
4. Mag #5, #7.
5. Org #5, Exp #5.
6. Mag #9.
7. Jdoc #2 (owner decision on list order).

### Lane 6: Platform services, networking transport, lifecycle, protection adapters
Owns: `PlatformServices`, Forge `ForgePlatformServices` and Forge `SomeStacks`, Fabric `FabricPlatformServices`, `FabricNetworking`, `SomeStacksFabric`, `SomeStacksFabricClient`, `ServerLifecycle`, `PlayerReach`, `ViewRay`, `ForgeEditAuthority`, `FabricEditAuthority`, `FtbChunksProtection`.

Order:
1. Net #3: encode the policy broadcast once.
2. Exp #1: player reach through the platform backend.
3. Log #2, #3.
4. Prot #3.
5. Net #2 (owner decision).
6. LD #1: after the owner's in-game check on Fabric. It adds one client mixin.

### Lane 7: Build
Owns: every `build.gradle`, `settings.gradle`, `gradle.properties`, `fabric.mod.json`, `mods.toml`, `build-env/`, `orientation-build.md`.

Order:
1. Snapshot sync: `build-env/code-review-decisions.md`.
2. Build #5, #6.
3. Build #3.
4. Build #7 (the owner deletes the files).
5. Build #1, #2, #4 (owner decisions).
6. Lane 2's night-config line, once lane 2 has moved the code.

Every item needs an owner-run build.

## Wave 2: core refactor and test scaffolding, parallel

### Lane 8: One growth gate and one deposit/extract path
Depends on lanes 1, 2, 3, and 6. Owns: `StackInteractions`, `StackSounds`, `StackRun`, `WorldEdits`, `BlockType`, `CommonRegistry`, the sound registration in Forge `ModRegistry` and Fabric `FabricRegistry`, `ViewRay`, the `*CubeIdx` box functions, and the block entity deposit and extract methods.

Order:
1. Org #2 with Leg #4, then Prot #1: one growth and placement gate. The owner decides the player build-ability rule.
2. Guard #1: one owner per precondition.
3. Org #4: shared targeting.
4. MLO #5: the sound enum.
5. Org #3 with Org #6: one deposit and one extract path, with `StackSounds` folded in. The owner decides whether the sound volume moves to `sounds.json`.

Every click must keep its current outcome, per `code-review-decisions.md`.

### Lane 9: Test scaffolding and standalone tests
Depends on lanes 2 and 3. Owns test source only, outside the files lane 8 edits: `GameTestScaffold`, `CapabilityAndPersistenceChecks` and both loaders' `CapabilityAndPersistenceGameTests`, `ItemOpsChecks`, `StackSortChecks`, the comparator checks, `PolicyParityChecks`, and the Fabric gametest `fabric.mod.json` entrypoints (coordinate with lane 7).

Order:
1. Test #9: the settings helper, plus replacing the adaptive `maxHeight() > 1 ? 2 : 1` expectations. Lane 8's Org #2 deletes `maxHeight()` after this.
2. Test #3.
3. Test #7.
4. Test #6 (owner decision).
5. Jdoc #1.
6. Mag #10 (the gametest imports).

## Wave 3: after lane 8, parallel

### Lane 10: Loader renames and folds
Owns the Forge and Fabric wiring files being renamed or folded, `somestacks.mixins.json`, and `StackBlockEntityMixin`.

Order:
1. MLO #4 with Org #8. The owner decides whether this happens in this checkout only or in every release checkout.
2. MLO #6.
3. Rein #4 (owner decision).
4. The Jdoc #5 entries for the Forge handlers and the Fabric entry points it touches.

Lane 11 switches the Forge gametest holders off `SomeStacks.MODID` first, then lane 10 deletes `SomeStacks.MODID`.

### Lane 11: Real-trigger tests
Owns gametest source.

Order:
1. Test #1, which builds on lane 9's helper.
2. Test #2.
3. Test #4.
4. Test #5 (move the duplicated loader scenarios into common).

This lane also writes the regression tests that lanes 1, 3, and 8 did not already write.

### Lane 12: Javadoc and forwarder sweep
Owns the common production files that lane 10 does not.

Order:
1. Jdoc #3, #4, and the remaining Jdoc #5 entries.
2. Leg #6.
3. Guard #4 (optional).
4. Net #1 (owner decision on the update tag).

## Not scheduled
- Rein #1 was rejected.
- Build #1's mixin `compatibilityLevel` and JSR 305 parts are accepted decisions.
