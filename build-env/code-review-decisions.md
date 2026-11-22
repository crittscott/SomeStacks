# Code review decisions

Settled review policy and accepted behavior for this project. Read this before reviewing or implementing a finding. These decisions override general preferences in the review prompt, CLAUDE.md, and code-conventions.md where they conflict. A new, demonstrated defect may justify revisiting a decision; preference, speculation, and a different architecture do not.

These decisions apply across release checkouts where the relevant behavior exists. API names, implementation observations, follow-up work, and verification claims below come from the checkout that recorded them unless explicitly scoped otherwise; they do not establish another checkout's implementation or verification status. Preserve that checkout's working APIs, target versions, and optional-mod settings, and consult its platform notes. An accepted integration placement does not require adding an integration to versions where it is absent.

## Review scope

- Preserve review findings, their original numbering, and follow-up items for cross-version handoff. Add a verdict to each assessed finding; when fixed, describe the implemented fix, relevant invariants, and verification status. Retain rejected findings with their reasons and qualify superseded claims explicitly rather than deleting or silently rewriting them. The verdict distinguishes the original review from the current code. A later verdict or correction takes precedence over the retained original summary, consequence, and proposed action; those original passages are not instructions to implement rejected work.
- Trust our code, Minecraft, loaders, and other mods unless a relevant behavior is known to be unreliable or unguaranteed. Filesystem operations and reflective integration boundaries can need failure handling. A client or server across the network remains untrusted.
- Keep effort proportional to a Minecraft mod. A theoretical edge case is not an actionable defect without a realistic consequence.
- A working deliberate choice is not a finding merely because another implementation is more conventional. General principles do not override the specific decisions below.
- Distinguish a demonstrated behavioral defect, a maintenance risk, and an implementation preference. State what the project code establishes and which additional platform fact the claimed consequence depends on. A confirmed code shape does not make a recalled runtime consequence confirmed.
- Keep implementation status separate from verification status. A fix may be implemented while its runtime consequence remains unverified. Compilation, callback tests, complete lifecycle tests, and multiplayer observation establish different things.

## Development and build

- Manual play-testing uses MultiMC, not the development runServer environment. The GameTest development world is disposable and may be deleted by the test helper.
- Keep build environments as close across Minecraft versions as reasonable. Inactive cross-version Gradle branches, plugin declarations, pack-format fields, and separate Mixin Java settings are acceptable consequences.

## Commands and player experience

- There is one command root: /ss. Keep the client/server routing needed to support it, including the client-side `/ss item` and `/ss write`. Do not propose a second root to simplify the code.
- Code complexity may support a simpler player interface.

## Data and networking

- Do not add required dependencies, including Forge Config API Port.

## Tests and logging

- Existing access to production logic retained for headless tests is accepted where the ordinary entry point requires a live client or connection. This permits access to the existing behavior, not test-only production behavior, configurable lifetimes, fake clocks, or permanent refusal listeners. Keep test fixtures and adapters in test source.
