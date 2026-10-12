# Backport task list â€” Some Stacks 0.8.3



One directory up is the 1.21.4 source code, for reference.



Target: bring the selected 1.21.4 build and architecture improvements into Minecraft 1.20.1 while preserving current player-facing behavior. See [backport-report.md](backport-report.md) for the assessment and exclusions.



Status: stages 1–3 and the Forge test-package correction passed owner builds and both loader GameTest suites. Stage 4 implementation and source review are complete; Stage 4 owner verification is pending. Stage 5 implementation and source review are complete; Stage 5 owner verification is pending. Stage 6 has not started. Check verification tasks only after the corresponding checks actually pass.

Builds, tests, and game runs are performed by the owner unless explicitly authorized otherwise, as required by `CLAUDE.md`. Commits and release tagging require separate authorization.



## 1. Modernize the build and release metadata



- [x] Set the active version authority in `gradle.properties` to `0.8.3` (currently `0.8.0`, despite the `v0.8.2` Git tag).

- [x] Update Loom to `1.17.493` and the Architectury plugin to `3.5.170`; adopt the assessed wrapper input updates while retaining Gradle `9.5.1` and Shadow `9.4.3`.

- [x] Centralize common metadata expansion, preserving loader-specific compatibility ranges and required 1.20.1 resource expansion.

- [x] Declare JSR-305 centrally for every module.

- [x] Centralize GameTest source-set setup and shared fixture generation; retain the 1.20.1 `structures/` resource path.

- [x] Make module selection follow `enabled_platforms`, retaining only Fabric and Forge.

- [x] Include Minecraft version in release artifact names: `somestacks-<loader>-1.20.1-0.8.3.jar`.

- [x] Remove unused Maven publication configuration and the changing Forge manifest timestamp.

- [x] Add production and GameTest documentation tasks with separate module output sections.

- [x] Give Fabric GameTests a dedicated run directory before adopting test-world cleanup.

- [x] Preserve Java 17 compilation and Minecraft development execution, 1.20.1 mappings and loader dependencies, the chosen Fabric API baseline, and the existing JUnit suite.

- [x] Update `build-env.md` and synchronize the build snapshot with the active build, excluding inactive NeoForge files.

- [x] Review the completed build changes by reading the scripts and metadata.

- [x] Owner verification: both loader artifacts report `0.8.3`, include shared code and resources, and exclude development tests; verify GameTest discovery and documentation tasks.



## 2. Introduce shared runtime foundations



- [x] Adapt `StackBlockEntity`, `StackRun`, and `StackRunItemAccess` to 1.20.1.

- [x] Consolidate inventory publication, nested batching, run caches, flat-slot addressing, headroom, and comparator mechanics.

- [x] Keep Storage settlement, Singles gravity, and Bar support/backfill in their concrete classes.

- [x] Preserve the existing saved-data format and local rotation/permanence semantics.

- [x] Adapt the generic Forge automation handler while retaining native capability exposure.

- [x] Adapt relevant foundation regression tests to current 1.20.1 behavior.

- [x] Update `orientation-code.md` to describe the resulting architecture.

- [x] Review mutation, publication, cache invalidation, and persistence paths.

- [x] Owner verification: persistence, settlement, gravity, support, lighting, and comparators retain their current results.



## 3. Fix Fabric transaction ownership



- [x] Cache one Fabric storage adapter per block entity.

- [x] Share transaction state across every access to the same run, including different sides and different blocks.

- [x] Preserve abort and nested-transaction behavior, positional insertion, and structural-extraction limits.

- [x] Port focused tests for multiple-side and multiple-block access within one transaction.

- [x] Update `orientation-code.md` to describe adapter ownership and transaction sharing.

- [x] Review staging, rollback, final commit, and structural mutation paths.

- [x] Owner verification: callers see consistent staged contents, conflicting accesses do not independently promise the same contents or capacity, and aborted transactions leave the world unchanged.



## 4. Consolidate services and configuration



- [x] Add loader-backed `PlatformServices` and shared server lifecycle handling.

- [x] Replace Architectury runtime calls for paths and mod versions.

- [x] Remove Architectury API runtime dependencies and metadata requirements once their uses have been replaced; retain Architectury build tooling.

- [x] Introduce immutable server settings and a persistence boundary while retaining JSON files, ingot patterns, defaults, commands, and current reload semantics.

- [x] Move shared render configuration into a server-safe `renderconfig` package.

- [x] Replace mutable offset arrays with immutable `RenderOffset` values while preserving the JSON vocabulary.

- [x] Preserve server render overrides and their precedence.

- [x] Adapt lifecycle, configuration, and render-configuration tests.

- [x] Update `orientation-code.md`, `orientation-player.md`, `build-env.md`, and the build snapshot wherever these changes affect their descriptions.

- [x] Review service installation, server-session cleanup, configuration publication, and override synchronization.

- [x] Owner verification: startup, reconnects, commands, policy lists, and server render overrides behave as before; both loaders run without Architectury API installed.



## 5. Port performance improvements



- [x] Precompute Storage settlement sort keys using the existing item/damage/NBT identity and ordering.

- [x] Introduce shared `CubeGrid` geometry while preserving grid spacing, render scales, rotations, and Singles seam mapping.

- [x] Cache resolved render profiles; invalidate on server sync, local edits, and resource reload.

- [x] Cache Bar UV regions and clear them on resource reload.

- [x] Preserve current automatic measurement decisions and visual output.

- [x] Adapt relevant sorting, geometry, and cache-invalidation regression tests.

- [x] Update `orientation-code.md` for the resulting geometry and caching responsibilities.

- [x] Review ordering equivalence and every cache invalidation path.

- [x] Owner verification: inventory ordering, targeting, rotations, and appearance remain identical, including after resource reload and server override synchronization.



## 6. Port interaction and placement improvements



- [ ] Make extraction and item rotation select cells on the server instead of accepting client-selected cell indices.

- [ ] Adapt Forge placement snapshot capture, denied-placement rollback, and accepted-placement publication.

- [ ] Adapt `StackInteractions` to ordinary 1.20.1 block-use handling with synchronized modifier/mode state.

- [ ] Preserve gesture ordering, cancellation behavior, off-hand handling, and existing protection integrations.

- [ ] Retain the current automation identity and Forge 1.20.1 `FakePlayerFactory`; avoid importing new cleanup attribution or game-event behavior.

- [ ] Remove superseded action packets and suppression machinery once the replacement is complete.

- [ ] Port focused interaction, protection, synchronization, and client callback tests using 1.20.1 expectations.

- [ ] Update `orientation-code.md` and `orientation-player.md` to match the final implementation.

- [ ] Review both loader callback paths, server targeting, placement atomicity, and protection denial.

- [ ] Owner verification: gestures, off-hand handling, sneaking, adjacent deposits, creative deposits, waterlogging, and protection denial work correctly on both loaders.



## Final verification and release readiness



- [ ] Review the complete changes against the behavior-preservation exclusions in `backport-report.md`.

- [ ] Confirm no changes to ingot classification policy, command permissions, sound extension points, server override precedence, configuration reload semantics, or gallery behavior were imported unintentionally.

- [ ] Confirm documentation and the build snapshot describe the final active implementation.

- [x] Owner verification: run the common JUnit suite.

- [ ] Owner verification: build both loader release artifacts and confirm version `0.8.3` and final artifact names.

- [x] Owner verification: run the Forge GameTest suite and confirm tests were discovered.

- [x] Owner verification: run the Fabric GameTest suite and confirm tests were discovered.

- [ ] Owner verification: complete client/server gesture and rendering checks on both loaders.

- [ ] Resolve all verification failures and record the outcomes below.

- [ ] After successful verification and separate release authorization, create the `v0.8.3` release tag.



## Progress notes



### Stage 1 — build and release metadata



Implemented version `0.8.3`, updated plugin pins and reference wrapper inputs, Minecraft-qualified

artifact names, shared metadata and JSR-305 declarations, dynamic Fabric/Forge module selection,

and the root GameTest helper with a shared Base64 fixture and 1.20.1 `structures/` output path.

Removed Maven publication setup and the Forge manifest timestamp. Added per-loader GameTest

Javadoc and root `generateDocs` output sections. Fabric GameTests use `run-gametest/world` for

cleanup, leaving ordinary development worlds outside that task's cleanup path.



Updated `build-env.md`, artifact paths in `orientation-code.md`, and the `build-env/` snapshot; removed

inactive NeoForge snapshot files. Java 17 compilation and development execution, loader versions,

Fabric API baseline, Architectury runtime dependencies, optional Fabric protection integrations,

and common JUnit configuration are preserved.



Source review covered task registration order, resource placeholders, test source-set isolation,

fixture ownership, loader classpaths, artifact naming, and snapshot consistency. No Gradle tasks,

builds, tests, documentation generation, or game runs were executed, in accordance with

`CLAUDE.md`. Stage 1 owner verification is recorded in its checklist above. IntelliJ needs a Gradle refresh to regenerate the Fabric GameTest run configuration. Stage 2 progress is recorded below.


### Stage 2 — shared runtime foundations

Implemented `StackBlockEntity`, `StackRun`, and `StackRunItemAccess` for Java 17 / Minecraft 1.20.1. The shared bases own inventory persistence, client updates, lighting, nesting-safe batches, per-tick run caches, structural invalidation, flat slots, headroom, and comparator publication. Storage settlement, Singles gravity and rotation-aware seams, and Bar support/collapse/backfill remain in their concrete classes. Saved `Items` (including `Size` and slot entries), `Rotation`, `Permanent`, and `CubeRotations` retain their existing format and scope.

Forge now attaches a generic `RunItemHandler` over `RunItemHandlerBase`, retaining all-side native capabilities, `LazyOptional` invalidation, positional simulation, and `RunEdit` reentrancy refusal. Removed the three superseded type-specific handlers. Fabric callers use the shared insertion/extraction signatures; adapter lifetime and independent transaction staging are unchanged for stage 3.

Added shared foundation GameTests for nested publication/light, same-tick run bounds and headroom, comparator/shape refresh after loading, and positional simulation plus each type's movement. Both existing loader capability/persistence test classes delegate to these checks; existing persistence, movement, protection, and native automation checks remain. Updated `orientation-code.md`.

Source review covered mutation callbacks, batch pairing during growth and collapse, scheduled publication, structural cache invalidation, slot mapping, NBT hooks, comparator contributions, native capability lifetime, and Fabric call sites. No builds, tests, or game runs were executed under `CLAUDE.md`. Owner verification of both loader builds and GameTest suites remains pending; stage 3 has not started.

Owner verification: `clean build` passed common production compilation, then stopped at common JUnit tests with 57 tests completed and one failure. `bundledIngotTagIsAppendOnlyAndNamesValues` assumed every tag value was a string, rejecting the bundled optional `forge:ingots` and `c:ingots` objects. Updated that test to validate string and object entries, their resource identifiers, and the optional boolean `required` field. The bundled tag is unchanged. Build rerun and loader/GameTest verification remain pending.

### Stage 3 — Fabric transaction ownership

Implemented a Fabric-only block-entity mixin and storage-owner interface that retain one adapter for each block entity's lifetime. Every sided lookup reuses that adapter, and all adapters addressing a run route transaction-visible reads and mutations through the bottom block's ledger. The adapter uses the shared run contract while retaining 1.20.1 item/NBT identity, item stack limits, positional insertion feasibility, deferred outer-commit replay, and RunEdit reentrancy refusal. Snapshot rollback restores the shared originals, staged contents, and Singles/Bar structural-extraction restriction.

Added and registered FabricTransferGameTests adapted from the 1.21.4 transaction tests, with expanded multi-block Singles/Bar coverage. They cover invisible live-world staging, whole-storage insertion/extraction, nested commit followed by outer abort, nested abort followed by outer commit, stable sided adapter identity, shared extraction remainder and insertion capacity across run blocks, and structural restriction rollback. Updated both orientation snapshots.

Source review covered mixin registration, adapter lifetime, bottom-block ledger resolution, transaction-visible reads, snapshot copying/restoration, final-commit ordering, shared structural limits, native test registration, and 1.20.1 API usage against existing project call sites. No builds, tests, or game runs were executed under CLAUDE.md. Stage 3 owner verification remains unchecked; build the Fabric artifact and run its GameTest suite to verify compilation, mixin application, test discovery, and transaction behavior.

### Owner verification and Forge GameTest package correction

Owner reports all builds and Fabric GameTests succeeded, completing Stage 3 verification and the common JUnit/Fabric GameTest checks. Forge GameTests stopped before discovery with a JPMS ResolutionException: somestacks and somestacks_gametest both exported com.github.crittscott.somestacks.block. The shared StackFoundationChecks helper occupied the production package to access package-private batching methods. Moved it into com.github.crittscott.somestacks.gametest and replaced those direct calls with test-only reflective access; production visibility and behavior are unchanged. Both loader wrappers use the relocated helper. Source review confirms GameTest sources no longer occupy production packages. No builds or game runs were executed by the agent; Forge launch/test verification and a Fabric rerun after this shared-test change remain pending.

### Owner verification after the Forge package correction

Owner reports all builds and both loader GameTest suites succeeded after relocating StackFoundationChecks. The Stage 3 implementation and shared-test correction are verified. Stage 4 changes below have not been built or run by the agent or owner.

### Stage 4 — services and configuration

Installed loader-backed PlatformServices before common initialization, replacing all Architectury API paths and consolidating existing native mod-version lookups. Shared ServerLifecycle handles world policy startup, login configuration, command resynchronization, logout pacing, and shutdown cleanup. Native loaders retain protocol gates, packet encoding/delivery, tag/reload hooks, and protection authorities. Removed Architectury API dependencies, metadata requirements, and unused API version properties while preserving Architectury build tooling; synchronized changed build-env snapshot inputs and runtime requirements in README/build documentation.

ServerConfig now publishes a Settings record with copied immutable lists. Named command lists replace the complete snapshot, bake admission sets, and save immediately. Its dedicated Persistence boundary retains world JSON sections/keys, loader-specific ingot patterns, defaults, numeric limits, and restart-only file reading; each world starts from loader defaults rather than another session's values. Shutdown releases policy paths, derived admission state, gallery jobs/indexes, and gesture pacing.

Moved modes, profiles, override entries, and the JSON codec into server-safe renderconfig. RenderOffset replaces mutable arrays across files, commands, packets, measurement, and renderers. Offset component order, numeric validation, JSON vocabulary, wire layout, server overrides, and server/user/resource/measurement precedence are preserved. ConfigSyncPkt owns an immutable override-map snapshot.

Adapted existing JSON, packet, render-mode, and measurement tests. Added policy persistence/immutability/default/limit checks, synchronized-offset validation, immutable packet snapshot coverage, and shared loader GameTests for service installation, logout pacing reset, and policy list publication. Source review covered loader installation order, start/stop hooks, registry baking, policy publication, JSON/wire vocabulary, override precedence, render math, and removal of Architectury API references. No builds, tests, or game runs were executed. Owner verification: build both loaders, run common JUnit and both GameTest suites, and check client/server startup, reconnects, commands, resource reload, and server override synchronization with Architectury API absent.


### Stage 5 — performance improvements

Storage settlement computes registry id, damage, and nullable NBT text once per exact identity group. Expanded legal stacks carry their group's immutable key during sorting, preserving the existing descending-count tie-breaker and stable ordering even when sort fields compare equal. Untagged and explicitly empty-tag variants remain distinct; grouping, legal stack sizes, packing, permanence, and protection behavior are unchanged.

Added shared CubeGrid coordinate conversion, precomputed rotation indices, and cached local boxes/shapes. Storage and Singles use it for targeting and render origins; Singles also uses its cached shapes and column mappings. Storage keeps its 1/6/11 pixel starts, 4.5 pixel boxes, and separate render scale; Singles keeps its 0/4/8/12 pixel starts, tiled 4 pixel cells, quarter-turn direction, and rotation-aware support seams. Ray traversal and tie ordering are unchanged.

ItemRenderOverrides now caches complete profiles per item, retaining server/user/resource/measurement precedence and current partial-profile defaults. Server synchronization, local edit/reset, resource override application, and model reload invalidate the cache. Override maps are private, and automatic measurement decisions, persisted-cache format, and initial-load handling are preserved. BarStackBER caches UV face regions by sprite identity; BarTextureStore clears them when applying reloaded textures, shared by both loaders. Vertex order, UV arithmetic, texture selection, and tint behavior are unchanged.

Added exhaustive Storage/Singles bounds regression checks across every cell and rotation, nullable-NBT sort-key checks, and client cache invalidation tests covering server replacement/clearing, user edit/reset, corpus replacement, initial/subsequent model reload, and Bar texture reload. Strengthened existing shared sorting GameTests with count-independent keys, source-NBT mutation, and explicit empty tags, and settlement coverage with interleaved identity groups. Existing loader wrappers run these shared scenarios without new registration. Updated orientation-code.md.

Source review covered ordering equivalence, legal-stack/count ties, cached bounds and render-origin equivalence, Singles seam inverses, profile precedence, all override mutation entry points, both loaders' resource reload paths, and sprite-cache lifetime. No builds, tests, or game runs were executed, as required by CLAUDE.md. Owner verification remains pending: build both loaders, run common JUnit and both GameTest suites, and check targeting, rotations, rendering, resource reload, and server override synchronization on both clients.


### Stage 5 — first owner build and cache-test setup correction

Owner's clean build compiled common production code, then stopped at common JUnit with 71 tests completed and four failures, all in RenderCacheInvalidationTest setup. Its dynamic PlatformServices.Backend proxy eagerly initialized Minecraft entity classes through proxy method-signature resolution before registry bootstrap; cleanup then attempted to initialize ItemRenderOverrides without an installed backend. Replaced the proxy with a direct test backend and guarded override cleanup, restoring platform state in finally. Production code is unchanged. Source review confirms the direct backend supplies only the temporary config path used by these tests, without proxy reflection over server/entity method types. No build or test was run by the agent; owner rerun and remaining Stage 5 verification are pending.
