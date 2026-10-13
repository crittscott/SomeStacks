# Review: Logging

## Summary
The existing informational messages report client resource loading and server admission-list/tag resolution, but omit completion of loader initialization and the effective world policy. Add a small number of startup milestones that identify those stages. Remove the remaining per-item debug message from automatic render measurement.

## Findings

### 1. Startup messages omit initialization and the active world policy

- **Severity:** Low
- **Confidence:** Certain
- **Where:** forge/src/main/java/com/github/crittscott/somestacks/SomeStacks.java:40; fabric/src/main/java/com/github/crittscott/somestacks/SomeStacksFabric.java:25; common/src/main/java/com/github/crittscott/somestacks/ServerLifecycle.java:17; common/src/main/java/com/github/crittscott/somestacks/ServerConfig.java:156; common/src/main/java/com/github/crittscott/somestacks/ServerConfig.java:317
- **In plain English:** The log reports how many banned items and ingots were found, but does not say when the mod finishes its initial setup or which world's rules it is using. It also does not show which stack types are enabled or the maximum stack height.
- **Consequence:** When an administrator investigates a stack type refusing placement or a height limit taking effect after restart, the normal log cannot establish which world config supplied those rules or their effective values. A maintainer checking a startup log must infer setup completion from unrelated later messages.
- **Action:** Use the existing `SomeStacksCommon.LOGGER` to emit one INFO message at the end of each loader's initialization, identifying the loader and describing completed registration rather than claiming the server is ready. Emit one shared INFO policy summary after `ServerConfig.load` publishes settings, such as `World policy active from {}: max height {}, storage {}, singles {}, bar {}, gallery {}`. Preserve the existing read/write failure warnings so this summary describes effective values even when defaults were used; do not add logs for clicks, packets, or player logins.
- **Technical detail:** Neither `SomeStacks()` nor `SomeStacksFabric.onInitialize()` logs after installing services and registering networking, commands, and lifecycle hooks. `ServerLifecycle.starting()` resolves the per-world JSON path and calls `ServerConfig.load()`, whose only successful-load output comes from `bakeServerLists()` and `bakeIngotItems()`. Those messages describe derived item sets, not the source path, enable flags, or height policy, and also run on command edits; they cannot serve as a clear world-policy startup summary.

### 2. Automatic model measurement retains per-item debug logging

- **Severity:** Low
- **Confidence:** Certain
- **Where:** common/src/main/java/com/github/crittscott/somestacks/client/measure/AutoRenderProfiles.java:241; common/src/main/java/com/github/crittscott/somestacks/client/measure/AutoRenderProfiles.java:246; common/src/main/java/com/github/crittscott/somestacks/client/measure/AutoRenderProfiles.java:278
- **In plain English:** When the mod cannot measure an item's appearance, it uses a default appearance and records a debug message for that item. These messages remain in production code even though the project calls for milestone logs and removal of debug logging.
- **Consequence:** With debug logging enabled, loading or displaying many previously unmeasured items produces per-item diagnostic output, including ordinary fallback cases. Resource reload clears the measurement cache, allowing the same output to recur; this adds noise when a maintainer is looking for lifecycle events and actual failures.
- **Action:** Remove `logFallback()` and its two call sites. Preserve the existing fallback profiles and fitting behavior; do not promote the messages to INFO or WARN.
- **Technical detail:** `compute()` invokes `logFallback()` for a reported measurement failure or a failed 3-D fit, and `logFallback()` calls `LOGGER.debug` with the item id and reason. The resulting profile is cached, so this is not per-frame traffic, but it is still residual per-item debug output and repeats after cache invalidation.

## Drift from the expected way
Logging uses the existing named Log4j logger rather than a custom logging system. The drift is confined to missing startup milestone summaries on both loaders (finding 1) and one remaining per-item debug helper in shared client code (finding 2).
