# Review: Protection

## Summary
The protection paths have two actionable gaps: a player can grow an existing Storage pile without the placement permission check used for a new pile, and Fabric does not consult ordinary interaction protection at a new placement's destination. Make those checks consistent before treating player placement as fully protected. Gallery commands and Fabric automation also have documented protection exceptions whose intended scope needs an owner decision.

## Findings

### 1. Growing a Storage pile bypasses Adventure placement restrictions
- **Severity:** High
- **Confidence:** Certain
- **Where:** common/src/main/java/com/github/crittscott/somestacks/server/StackInteractions.java:155; common/src/main/java/com/github/crittscott/somestacks/server/StackInteractions.java:196; common/src/main/java/com/github/crittscott/somestacks/block/StoragePile.java:166; common/src/main/java/com/github/crittscott/somestacks/block/StoragePile.java:301; common/src/main/java/com/github/crittscott/somestacks/block/StoragePile.java:328
- **In plain English:** An Adventure player who cannot place a new stack can still create another block by depositing into an existing Storage pile until it grows. Filling existing storage is allowed, but growing it also places a block and must pass the same placement permission check.
- **Consequence:** Adventure maps and servers can have new stack blocks placed by players whose held item does not permit placement there, on either loader. The first-placement restriction can be bypassed once a suitable existing pile is available.
- **Action:** Before player-driven Storage growth, call the depositing player's `mayUseItemAt` for the growth position, the placement face, and the offered item, matching the first-placement check. Thread the offered item into `canGrow`/`grow` as needed; leave automation attributed to its separate actor. Refuse growth while retaining any items already deposited into existing capacity.
- **Technical detail:** `StackInteractions.deposit` passes the actual player to `StorageStackBE.deposit`, which reaches `StoragePile.deposit`. Its growth loop calls `grow(placer)` after filling existing capacity. `canGrow` checks height, enablement, replaceability, `mayInteract` through `WorldEdits.isProtected`, and obstruction, but never `Player.mayUseItemAt`; `WorldEdits.placeChecked` and both loader authorities do not supply that missing check. In contrast, `StackInteractions.placeAndDeposit` explicitly checks `player.mayUseItemAt(pos, face, handStack)` before creating the first block. Ordinary use of an existing container does not impose the missing Adventure placement check for us.

### 2. Fabric checks adjacent interaction protection only for existing displays
- **Severity:** High
- **Confidence:** Likely
- **Where:** common/src/main/java/com/github/crittscott/somestacks/server/StackInteractions.java:81; common/src/main/java/com/github/crittscott/somestacks/server/StackInteractions.java:83; common/src/main/java/com/github/crittscott/somestacks/server/StackInteractions.java:232; fabric/src/main/java/com/github/crittscott/somestacks/server/FabricEditAuthority.java:43; fabric/src/main/java/com/github/crittscott/somestacks/server/FabricEditAuthority.java:53
- **In plain English:** On Fabric, clicking an allowed block beside a protected destination consults that destination's normal protection listeners when adding to an existing Singles or Bar display, but skips them when creating a new stack. The two explicitly integrated claim mods are checked separately; other protection listeners receive only the original clicked position.
- **Consequence:** A Fabric server whose protection listener denies interaction at the destination, but allows the originally clicked block, can have a new stack placed at the denied position. Which installed protection mods expose this depends on their listener behavior; the missing destination consultation is verified in the code.
- **Action:** Consult `UseBlockCallback` at the destination before committing player-driven Fabric placement, including player-driven Storage growth. Use the existing guarded destination consultation in `WorldEdits` so the consultation cannot execute another gesture; keep the FTB Chunks and OPAC placement checks as additional gates. Do not synthesize another destination interaction for Forge placement, which already has its native placement event.
- **Technical detail:** The adjacent-deposit branch calls `WorldEdits.mayUseAdjacent`, which invokes Fabric's `UseBlockCallback` for the destination under the recursion guard. The new-stack branch calls `placeAndDeposit` directly. That eventually enters `FabricEditAuthority.place`, whose only claim checks are `FtbChunksProtection.prevents` and `OpacProtection.prevents` before `level.setBlock`. `mayInteract` and `mayUseItemAt` provide vanilla permissions, not the skipped third-party interaction callback. Player-driven Storage growth enters the same authority without a destination callback as well.

## Drift from the expected way
Player-driven Storage growth omits a vanilla placement permission that first placement already uses (finding 1). Fabric's missing generic placement event has led to specific claim integrations, but the destination interaction consultation already used for adjacent deposits is not applied consistently to player-driven placement (finding 2).

## Open questions
1. Are the directly replacing gallery commands in `RenderGalleryGenerator.java:290` and `RenderGalleryGenerator.java:352` an intentional exception to CLAUDE.md's requirement that world edits on a player's behalf pass ordinary permissions and events, even when the configured gallery permission allows non-operators?
2. Is Fabric automation's documented support for only FTB Chunks and Open Parties and Claims acceptable, or should administrators have a policy to disable structural automation when using another claim mod?
