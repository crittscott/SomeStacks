# Review: Legacy

## Summary
The current code does not contain a general old-format reader or protocol compatibility ladder. The saved-inventory reader does silently discard entries it cannot place, which violates the project's explicit rule for unrecognized player data. There is also an unnecessary trip through loader-specific command adapters for a broadcast already implemented entirely in common code.

## Findings

### 1. Unrecognized saved inventory entries are discarded without preservation
- **Severity:** High
- **Confidence:** Certain
- **Where:** common/src/main/java/com/github/crittscott/somestacks/util/StackItemStorage.java:157; common/src/main/java/com/github/crittscott/somestacks/util/StackItemStorage.java:163; common/src/main/java/com/github/crittscott/somestacks/util/StackItemStorage.java:141; common/src/main/java/com/github/crittscott/somestacks/block/StackBlockEntity.java:219; common/src/main/java/com/github/crittscott/somestacks/block/StackBlockEntity.java:230
- **In plain English:** When a saved stack contains an item in a position the current mod does not recognize, loading skips that item without keeping a recovery copy or reporting it. Saving the block afterward writes only the items that survived loading.
- **Consequence:** Players and server admins lose the original saved entry when an unrecognized inventory reaches this reader and the block is subsequently saved; they receive no warning or recoverable record. This is a conditional data-loss path, not evidence that the ordinary current format or the preceding release actually contains such entries.
- **Action:** Validate the inventory envelope and slot entries at the saved-data boundary before replacing the live inventory. Preserve rejected original tags in a dedicated recovery record that survives subsequent saves, and log their block position and rejection reason. Keep any conversion for the immediately preceding release in a dedicated migration layer; do not add readers for arbitrary older formats.
- **Technical detail:** `deserializeNBT` clears the current array, ignores the saved `Size`, and only assigns entries whose `Slot` is in bounds. Out-of-range entries are silently skipped, duplicate slots overwrite earlier entries, and there is no rejected-data output. `StackBlockEntity.saveAdditional` later replaces `Items` with `serializeNBT()` of the accepted array, so the original omitted entries cannot survive that save. This is specifically the project's required unrecognized-player-data preservation case, rather than a request to support additional legacy formats.

### 2. Shared configuration broadcasts take an unnecessary loader detour
- **Severity:** Low
- **Confidence:** Certain
- **Where:** common/src/main/java/com/github/crittscott/somestacks/command/SsCommand.java:653; common/src/main/java/com/github/crittscott/somestacks/command/CommandNetwork.java:13; common/src/main/java/com/github/crittscott/somestacks/command/CommandNetwork.java:26; forge/src/main/java/com/github/crittscott/somestacks/command/ForgeCommandNetwork.java:14; fabric/src/main/java/com/github/crittscott/somestacks/command/FabricCommandNetwork.java:14; common/src/main/java/com/github/crittscott/somestacks/ServerLifecycle.java:42
- **In plain English:** The reload command goes out to Forge or Fabric just to return to the same shared broadcast function. Neither loader adds behavior at that step.
- **Consequence:** A maintainer changing the broadcast contract must update a common interface and both loader implementations even though the actual behavior has one shared implementation; following the reload path also requires crossing three forwarding methods before reaching that implementation.
- **Action:** Have `SsCommand` call `ServerLifecycle.syncAllPlayers` directly. Remove `syncAllPlayers` from `CommandNetwork`, its handler interface, and both command adapters, retaining their genuinely loader-specific packet-delivery methods.
- **Technical detail:** The call chain is `SsCommand.reload` -> `CommandNetwork.syncAllPlayers` -> `ForgeCommandNetwork.syncAllPlayers` or `FabricCommandNetwork.syncAllPlayers` -> `ServerLifecycle.syncAllPlayers`. Both adapter methods contain only `return ServerLifecycle.syncAllPlayers(server);`. The actual broadcast already uses `PlatformServices.sendConfig` for the required loader transport seam.

## Drift from the expected way
The identified drift is localized: the saved-data boundary omits the project's migration/recovery rule (finding 1), and the command seam retains a forwarding operation after its implementation moved into shared lifecycle code (finding 2). Necessary loader adapters and type-specific geometry facades are not themselves legacy support.

## Open questions
1. What is the immediately preceding released version and its authoritative world-data format, so the supported migration input can be checked without inferring release history from the current code?
