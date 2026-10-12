# Assessment: backporting the 1.21.4 build environment and architecture

## Recommendation and scope

Bring back the build organization and shared runtime foundations, but do not copy the 1.21.4 repo wholesale. Its improvements are mixed with deliberate behavior changes that would violate the requirement to preserve this repo's player-facing behavior.

This assessment covers `orientation-player.md`, `orientation-code.md`, `build-env.md`, both repos' instructions, the active Gradle files, and relevant implementation in this repo and the sibling `../1.21.4` repo. It is a source assessment; no implementation changes, builds, or tests were performed.

## Build environment

The starting point is closer than `build-env.md` suggests. Both repos already use **Gradle 9.5.1 and Shadow 9.4.3**. The plugin differences are small: Loom `1.17.491 → 1.17.493` and Architectury plugin `3.5.169 → 3.5.170`.

| Bring back | Assessment |
| --- | --- |
| Shared GameTest configuration | Replace duplicated loader setup with 1.21.4's root `configureGameTests` helper. Keep the **1.20.1 `structures/` resource path**, rather than copying 1.21.4's `structure/` path. |
| Shared metadata expansion | Centralize common mod properties, leaving loader-specific ranges in each loader script. |
| Centralized JSR-305 dependency | Declare it for every module instead of relying on Forge's transitive dependencies and repeating it in Fabric. |
| Dynamic loader selection | Have `settings.gradle` consume `enabled_platforms`, keeping only Fabric and Forge here. |
| Artifact naming | Include Minecraft version: `somestacks-<loader>-1.20.1-<mod-version>.jar`. This distinguishes releases across the three repos. |
| Documentation generation | Adopt `generateDocs` and separate production/GameTest Javadoc sections if generated API documentation is to be maintained. |
| Build cleanup | Remove unused Maven publication configuration and the changing Forge manifest timestamp. |
| Wrapper and plugin updates | Adopt the newer wrapper inputs and plugin pins as a coordinated change, subject to subsequent build verification. |

Keep **Java 17 compilation and Minecraft development execution**, Minecraft 1.20.1 mappings, Forge 47.x, and the chosen 1.20.1 Fabric API baseline. Treat the JVM that launches Gradle separately from the project toolchain.

Do not copy the new `common/build.gradle` unchanged: it omits this repo's JUnit setup. Preserve those tests. Similarly, preserve 1.20.1 resource expansion where its metadata still needs it.

The 1.21.4 Fabric script deletes `fabric/run/world` before GameTests. Adapt that to a dedicated GameTest run directory before adopting it.

## Architecture worth bringing back

These are the strongest candidates, in priority order.

| Priority | Backport | Why, and what must stay the same |
| --- | --- | --- |
| **1** | **Fabric storage lifetime and shared transaction ledger** | Here, every storage lookup creates a fresh `FabricRunItemStorage`, with independent staged contents. Multiple accesses to the same run can therefore promise conflicting mutations within one transaction. 1.21.4 caches an adapter per block entity and shares the bottom block's ledger across the run. Bring back both parts together, preserving the existing positional insertion and structural-extraction rules. |
| **2** | **`StackBlockEntity`, `StackRun`, and `StackRunItemAccess`** | They consolidate duplicated inventory publication, nested batching, run resolution, flat-slot addressing, headroom, and comparator mechanics. Keep Storage settlement, Singles gravity, and Bar support/backfill in their concrete classes. This matches the project's preference for coherent classes. |
| **3** | **Generic automation adapters** | Adopt the common run contract and generic Forge handler instead of repeated type dispatch. Keep Forge capabilities and Fabric Transfer API as the native outer interfaces. |
| **4** | **`PlatformServices` and shared server lifecycle** | Replace the few Architectury runtime calls for paths/mod versions with loader services, and consolidate duplicated startup, login, logout, and synchronization work. Architectury can then remain build-time only. Remove its runtime dependency only after replacing the actual calls and metadata requirements. |
| **5** | **Precomputed Storage settlement sort keys** | 1.21.4 sorts distinct identity groups before expanding them into legal stacks. Here, expanded stacks repeatedly perform registry lookups and NBT string comparisons during sorting. Port the optimization using **1.20.1 item/damage/NBT identity and ordering**, rather than Data Components. |
| **6** | **`CubeGrid` geometry foundation** | Share coordinates, rotation tables, and cached boxes between Storage and Singles while retaining their different grid spacing, rendering scales, and Singles seam rules. |
| **7** | **Server-safe render configuration types** | Adopt the `renderconfig` package boundary and immutable `RenderOffset` instead of mutable `float[]` offsets. Preserve the existing JSON vocabulary and server-override precedence. |
| **8** | **Immutable server policy** | Adopt a `Settings` record and persistence boundary, retaining this repo's JSON files, ingot patterns, defaults, commands, and reload semantics. Native TOML configuration is a separate behavior change. |
| **9** | **Selective rendering caches** | Port resolved-profile caching and Bar UV-region caching. Invalidate profiles on server sync, local edits, and resource reload. Preserve this repo's measurement decisions and visual output. |

The transaction issue is the clearest correctness reason to backport something beyond the build. The shared foundations are the largest maintenance improvement.

## Interaction and world-edit architecture

These changes need a separate, deliberate port.

The 1.21.4 interaction design uses ordinary block-use packets plus synchronized mode/modifier state, with common server handling in `StackInteractions`. That reduces custom action packets and the need to suppress a trailing vanilla click.

This direction is recommended, but it is the highest-risk architectural backport. Minecraft 1.20.1 has different block-use APIs; its callbacks, cancellation behavior, off-hand handling, and gesture ordering need an explicit adaptation.

One improvement can be made independently: **select extraction and item-rotation cells on the server**. Here, `ExtractPkt` accepts a client-selected cell index and checks it without reconstructing the aimed cell. 1.21.4 derives the cell from the interaction ray. Normal controls can remain the same.

Also port the **Forge placement snapshot transaction** selectively. Current `WorldEdits.placeChecked` publishes placement using `UPDATE_ALL`, then restores only the previous block state when protection rejects it. The newer implementation captures snapshots, restores denied state, and delays publication until acceptance. That better implements the existing promise that rejected placement leaves no effects behind.

Keep **Forge 1.20.1's `FakePlayerFactory`**. The custom Forge automation player in 1.21.4 addresses a facility missing there, not here.

Changing cleanup attribution, automation identity, or emitting new game events can affect claims, logging, and sculk behavior. Those changes should not ride along unnoticed.

## Changes to exclude under the behavior-preservation requirement

- Replacing configurable ingot-tag patterns and `/ss ingot` with one fixed tag, including new brick admission.
- Moving Forge policy from JSON to TOML or changing `/ss reload` semantics.
- Removing server render overrides or changing their precedence.
- Making `/ss item` and `/ss write` unrestricted client commands.
- Replacing data-pack sound configuration with resource-pack `sounds.json`.
- Replacing FTB Chunks/OPAC integration with Common Protection API without establishing equivalent 1.20.1 coverage.
- Moving layout rotation into block state, which also changes structure-rotation behavior.
- Importing Data Components, 1.21.1 world migration, or the newer item-rendering pipeline.
- Adding NeoForge or copying new denial defaults and gallery behavior.

## Recommended sequence and verification

First modernize the build organization while retaining existing dependencies and tests; then backport shared runtime foundations and Fabric transaction ownership; then take the behavior-preserving performance changes. Handle interaction and placement transactions as a separate pass with focused regression checks.

Bring back relevant automation, interaction, synchronization, and callback GameTests alongside their implementations. Keep the current JUnit suite and adapt assertions to **1.20.1 behavior**, rather than importing 1.21.4 expectations.

## Documentation corrections

Refresh the orientation documents during implementation: `build-env.md` incorrectly says there are no optional third-party integrations, despite the active FTB Chunks and OPAC dependencies. Its build snapshot also contains NeoForge files although NeoForge is not an active module here.
