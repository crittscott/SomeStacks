# Some Stacks: Minecraft 1.21.4 to 1.21.5 port

## Scope and current state

Port the existing mod to Minecraft 1.21.5 across Fabric, Forge, and NeoForge. This is the first stage toward 1.21.8; implement against 1.21.5 APIs without anticipating later versions or adding compatibility paths for multiple running Minecraft versions.

The build dependencies and runtime declarations target 1.21.5, and their `build-env/` reference copies and documentation have been updated. The user reports that Gradle builds. The source inspection nevertheless identified old API usages that require attention; establish what the successful Gradle operation covered before treating production compilation or GameTests as verified.

This plan is based on repository inspection and published migration guidance. It is not a complete compiler-error inventory. Follow CLAUDE.md: do not build or run tests unless explicitly asked, and do not inspect decompiled Minecraft or Forge sources or caches.

## 1. NBT, persistence, and saved-world migration

Implementation complete; reviewed by reading, with compilation and GameTest execution pending. NBT reads use the 1.21.5 API, disk recovery retains malformed list entries and missing/invalid slots, and client validation checks every entry before publication. Existing shared checks now cover optional getters, heterogeneous lists, invalid metadata, and current/1.21.4-versioned persistence. ItemStack serialization/parsing signatures remain available, so the saved-data layout is unchanged. CHANGELOG.md identifies the 1.21.4 port as unreleased and 0.8.2 for 1.21.1 as the preceding release; the 1.21.1 migration path remains. The 1.21.4 checks use version-stamped fixtures for fields whose layout is shared with the current format; an actual saved-world upgrade remains a runtime verification step.

- Update NBT reads and presence/type checks in `StackItemStorage`, `StackDataMigration`, `StackBlockEntity`, `StorageStackBE`, and `SinglesStackBE` for the 1.21.5 optional-returning API. Search the remaining production and GameTest code for the same usages.
- Review item serialization and parsing against the published 1.21.5 API. Keep the current saved-data layout unless an actual API or format change requires a conversion.
- Preserve the distinction between disk recovery and client validation. Disk loading must retain unreadable entries in `SetAside`; client updates must reject malformed inventory or rotations atomically. Do not replace required-field validation with permissive defaults.
- Keep item upgrades through DataFixerUpper in the dedicated migration layer. Verify loading data written by the 1.21.4 version, including components, slot identities, layout rotation, Singles item rotation, permanence, and set-aside entries.
- Confirm the immediately preceding released mod version before changing the existing 1.21.1 migration support. Do not assume that a porting branch was released. Update migration comments and fixtures to match the supported released input.
- Adapt the persistence and synchronization checks alongside the implementation; execution follows after the GameTest integration is ported.

Completion: current-format saves round-trip, supported previous-release saves migrate, unreadable items remain recoverable, and malformed client updates leave existing state untouched.

## 2. Block removal and run repair

Implementation complete; reviewed by reading, with compilation and runtime checks pending. StackBlockEntity drops local contents in the server-side preRemoveSideEffects callback; BarStackBE overrides content removal to batch its contents with the unsupported column and suppress nested cascades. The three block classes use affectNeighborsAfterRemoval for cache invalidation, native neighbor updates, and surviving-run scheduling/publication. State-only changes bypass run-removal repair. WorldEdits and loader edit-authority implementations remain the route for cascade cleanup. Existing shared removal scenarios now also check state-only updates; the full Bar removal scenario checks a three-block cascade and consolidated drops. All stack types retain their piston-blocking properties. GameTest registration remains Stage 4 work.

- Replace the old removal implementation in `StorageStackBlock`, `SinglesStackBlock`, and `BarStackBlock` with the 1.21.5 lifecycle. Use block-entity `preRemoveSideEffects` for work requiring the departing block entity and block `affectNeighborsAfterRemoval` for the appropriate surrounding updates.
- Preserve Storage content drops, run-cache invalidation, and scheduled settlement above and below a removed block.
- Preserve Singles content drops, publication, and scheduling on both surviving runs without introducing gravity across a broken block.
- Preserve Bar drop batching, cascade-removal state, support loss above a removed block, and final publication. Rework the ordering deliberately so one removal does not produce duplicate drops or a second cascade.
- Review interactions with `WorldEdits`, protected cleanup, rotation-only state changes, waterlogging, and comparator publication. Retain player/automation edit authority and native removal hooks.
- Update removal-related comments and existing behavioral checks with the implementation.

Completion: each removal drops contents once, surviving runs repair correctly, Bar support cascades remain correct, and ordinary state changes do not trigger content removal.

## 3. Client rendering and initialization

- Adapt `ItemCapture` to the 1.21.5 `ItemModelResolver` call signature. It already uses `ItemStackRenderState`; retain that design unless a concrete API change requires more.
- Review `CubeRenderHelper` calls for FIXED, GUI, and block rendering against the new interfaces.
- Inspect quad capture, bounds measurement, tinting, Bar textures, render buffers, and lighting for affected API changes. Keep render-profile precedence, per-pass capture reuse, reload invalidation, and the error-cube behavior coherent.
- Inspect loader client setup, particularly NeoForge's earlier mod initialization, for access to `Minecraft` before it is available. Move work to the appropriate lifecycle event only where needed.

Completion: all four display modes, measurement, Bar rendering, profile commands, and resource reload behave correctly on each loader.

## 4. GameTest integration

- Port loader test discovery and registration to their supported 1.21.5 facilities. Fabric supplies a replacement annotation; replace the old vanilla imports and adapt annotation options and entrypoints as required.
- Determine Forge and NeoForge registration requirements from their published APIs. Do not assume their integrations are identical to Fabric or to each other.
- Retain shared scenario bodies in `common/src/gametest`; adapt shared helpers and loader wrappers where APIs changed.
- Review test environments, instance definitions, structure identifiers, and loader test metadata. Convert repository fixtures deterministically if their format changes.
- Review the Gradle GameTest source sets, launch settings, and structure-generation helper. Synchronize every changed build input into `build-env/` and update `build-env.md`.
- Preserve Fabric's Common Protection API presence/absence coverage and the client callback smoke command.

Completion: the tests compile and are discoverable under each loader, and their launch configuration uses the supported 1.21.5 framework.

## 5. Remaining loader APIs and resource metadata

- Review networking, interaction callbacks, edit-authority adapters, automation capabilities/transactions, registration, and resource reload listeners for remaining 1.21.5 changes. Use compilation findings supplied by the user to make this pass concrete.
- Verify the optional Common Protection API dependency against 1.21.5, including development runs with and without it.
- Update `common/src/main/resources/pack.mcmeta`, which currently declares `pack_format: 61` and `supported_formats: [46, 61]`. Use the published target resource-pack format and only claim support actually intended for this port.
- Update orientation snapshots and other affected documentation to describe the resulting code. Remove pending-port language only after the corresponding work is verified.

Completion: loader integration and packaged resource declarations consistently target 1.21.5, with no obsolete implementation paths left beside replacements.

## Verification sequence

Builds and test execution remain user-run unless explicitly delegated. Read and reason about each change before handing it over.

1. Check production compilation for common code and all three loaders after the production API changes. Address remaining errors within the appropriate common or loader-specific implementation.
2. Compile the GameTest source sets separately; the ordinary build does not establish that these development-only sources compile.
3. Run the existing server GameTests for each loader, including both Fabric protection configurations.
4. Check a 1.21.4 saved-world upgrade: inventory/components, rotations, permanence, set-aside recovery, and a subsequent save/reload.
5. Exercise client controls, item appearance, resource reload, targeting/collision, sound, and lighting on each loader. Check placement denial, automation growth/removal, and Bar cascades where lifecycle changes could affect behavior.

Record actual results and unresolved failures as work proceeds. Successful Gradle import, production compilation, GameTest compilation, and runtime verification are separate checkpoints.

## Published references

- [Minecraft 1.21.4 to 1.21.5 porting primer](https://docs.neoforged.net/primer/docs/1.21.5/)
- [NeoForge 21.5 release notes](https://neoforged.net/news/21.5release/)
- [Fabric for Minecraft 1.21.5](https://fabricmc.net/2025/03/24/1215.html)
