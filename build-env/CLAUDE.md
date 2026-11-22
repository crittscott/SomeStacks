###### v21

YOU ARE NOT TO DECOMPILE OR UNARCHIVE ANYTHING, EVER. NO EXCEPTIONS. YOU ARE NOT TO TREAT EVERY TASK AS OF WORLD-ENDING IMPORTANCE TO GET RIGHT. YOU ARE NOT TO SEARCH THE ENTIRE INTERNET IN AN ATTEMPT TO MAKE SURE YOU HAVE AN IRREFUTABLE PROOF THAT EVERY WORD OF YOUR AS YET UNWRITTEN ANSWER IS PERFECTLY CORRECT. DO NOT CHECK EVERY THING YOU DID MULTIPLE TIMES. DO IT AND MOVE ON.

# Language
Use American English, not British English. Be concise.

# Scope of analysis and work
Your domain of interest is the project at hand. Only if you cannot answer the question by looking here are you to look outside.

Do not decompile Minecraft or the loaders (Forge, NeoForge), and do not read, extract, or search any decompiled/remapped source for them either — this includes Loom-cached "sources" jars, *_mapped_* jars, javap/bytecode inspection, or anything under .gradle/caches or similar. If you need to know a Minecraft or loader API signature, ask me or find it in that library's own published API docs/source (e.g. Fabric API's own sources are fine — Minecraft's, Forge's, and NeoForge's are not).

You are a programmer; you write code. Do not build unless asked. Everything not explicitly a command to build is a discussion.

Without those sources, what you know about a Minecraft or loader API comes from memory, and memory is often wrong. Treat a recalled signature, event order, or call flow as a hypothesis. Do not replace working code on the strength of one, and find out why existing code has its shape before calling it a mistake.

# Reference files

- Read code-review-decisions.md before reviewing or implementing review findings. It records accepted non-issues and owner decisions; specific decisions there override general preferences here.

- `code-conventions.md` holds the rules for writing code, organized by what you are doing. Read it before writing or changing code.
- `platform-notes.md` records how specific Minecraft versions, loaders, and libraries actually behave. Read the parts that apply before writing a hook, Mixin, packet, or port. When you learn such a fact, add it there, marked with how it is known. This file stays free of version-specific and loader-specific detail.

# Project Principles

- This is a Minecraft mod, not a mission-critical enterprise suite. Only one version of the mod ever runs at a time, so it never interoperates with earlier versions of itself. When code or behavior changes, remove the old path entirely: no shims, fallbacks, deprecated aliases, or readers for formats that never shipped.
- Repository data (resources, data packs, test fixtures) changes format in place. When a format changes, convert all of it at that time, using deterministic tools (CLI utilities, python, etc.) unless the conversion needs an AI.
- Player worlds are the exception, and they are handled as migration, not compatibility. A world saved by the previous released version must load. Convert its saved data to the current form as soon as the mod first reads it, and write only the current form afterward. Keep this conversion in a dedicated migration layer so the rest of the code sees only current state. Support only the immediately preceding released version. Never silently discard saved player data that can't be recognized; set it aside and log it.
- Player simplicity takes priority over implementation simplicity. There is one command root, /ss; keep the client/server routing needed for this interface.
- Boring beats clever. Do things the vanilla, Forge, NeoForge, and Fabric way: if Minecraft or the loader has a facility for a goal, use it, even when a home-grown version would be shorter or already works. An expert Minecraft modder should look at our code and find nothing surprising. Specific accepted choices in code-review-decisions.md take precedence; do not replace them solely for conventionality.
- The mod acts as the player. Anything the mod does to the world on a player's behalf passes the same permission checks and fires the same events vanilla would if the player did it by hand, so protection, claims, and other mods' hooks apply without special handling.
- Retained state has an owner and a lifetime. Its identity, creation, transfer, and cleanup belong to that owner. Share rules without merging state that belongs to different endpoints, entities, hands, worlds, or clocks.
- Each concept has one home. A behavior lives in exactly one place; when code is replaced, the old version is deleted, not left beside it. Anything two places must agree on is one function or one declaration that both use: a client hint and the server action it predicts, an action and its undo, the same rule on two loaders. A missed line that compiles and silently does nothing is a design defect.
- An event means what vanilla means. Hook the point where the thing actually happened, not a method that merely ran on the way. A hook standing in for a missing loader event must cover every case that event covers. Intercepting vanilla's flow makes you responsible for everything vanilla would have done on the path you skipped.
- Share what is the same; avoid unnecessary loader differences. Do not contort code to share a few lines. Optional integration support may remain in common to share the existing pipeline even when it is inert on one loader.
- Work happens when something changes, not on a schedule. Prefer events and maintained lists over per-tick polling, broad area scans, and repeated sorting. Send state changes and the initial or catch-up state a recipient needs; do not repeatedly send unchanged state.
- Nothing crosses a trust boundary unchecked. The server validates everything a client sends; the client validates everything a server sends. Trusting Minecraft and the loader does not extend to the other end of the connection. Validate structure and intrinsic bounds on decode, once. Check authorization and conditions that depend on current game state at execution. Do not repeat checks already guaranteed by the decoded type.
- Everything visible is deliberate. Player-facing text lives in the lang file; tunable numbers are named constants or config options; logs record milestones, not traffic, and debug logging does not stay in the code; every player-visible behavior has a gametest whose javadoc tells a person how to reproduce it in-game.
- When assessing code for safety issues, do not worry that Minecraft or the loader itself may misbehave; it is not our job to protect against every conceivable error. Trust our code, Minecraft, loaders, and other mods unless the relevant behavior is known to be unreliable or unguaranteed. Filesystem operations and reflective integration boundaries may need failure handling; another mod being involved does not by itself justify speculative checks. Do not guard against what the caller, a codec, or the type system already excludes.
- The mod should be server friendly; we need to pay attention to how much work we require the server to do (and the client, of course, but that's a much smaller problem). The configuration should provide the server admin with the abilities an admin would find useful.
- Conceptual efficiency is more important than lines-of-code efficiency: I prefer deeper classes of coherent content to an atomized single-function-per-class architecture. Some duplication is better than yet one more small class. That tolerance covers mechanics, not decisions: repeated wiring may stay, a repeated rule, formula, or tunable value may not.
- Code fits its target. A version checkout holds only what its target Minecraft version can use. Shared cross-version build machinery is an explicit exception.
- Shipped code serves the shipped mod. Do not add production behavior, parameters, or alternate lifecycles solely for tests. Test helpers live in test source; accepted access to existing production logic for headless tests is recorded in code-review-decisions.md.
- A test proves what a player would see. Drive the real trigger, not the service behind the hook, and do not derive the expected value from the same assumption as the code. A bug fix comes with a test that fails without it wherever a gametest can reach the bug.
- Deliberate surprises are explained. When code departs from the expected way for a good reason, a comment at that spot says what the expected way fails at.
- Do not add a required dependency without my approval.

# Orientation files

`orientation-player.md`, `orientation-code.md`, and `orientation-build.md` are snapshots of the mod and its build as they currently are, written so an agent can orient itself without reading the whole codebase. They are not specifications, requirements, goals, or a to-do list.

- The code is the truth; for `orientation-build.md`, the active build files are the code. When a file disagrees with the code, the file is wrong. Fix the file, not the code, unless I say otherwise.
- Never treat a described behavior as a requirement to preserve, or a listed limitation as work to do.
- Describe only the current state: no history of how it got that way, no change logs, troubleshooting narratives, conversation, or plans.
- After a change alters what a file describes, update that file in place to match the code.
- Size limits: `orientation-player.md` ≤ 15,000 characters; `orientation-code.md` ≤ 12,000 characters; `orientation-build.md` ≤ 15,000 characters. Trim when over the limit.
- The fixed header at the top of each file is not content: do not edit it, and it does not count toward the size limit.

# Markdown

- Do not hard-wrap prose in Markdown files: write each paragraph or list item as one line and let the editor wrap it. Tables and code blocks are unaffected.

# Release documentation and build files

- The mod is released for Minecraft 1.20.1, 1.21.1, 1.21.4, 1.21.8, 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2, and 26.3, each from the branch named for its Minecraft version. Other version checkouts are unreleased waypoints.
- `README.md` is identical in every checkout and covers every release.
- `docs/curseforge-main.md` and the guides in `docs/` (`player-guide.md`, `automation.md`, `server-admin.md`, `pack-authors.md`) describe only this checkout's Minecraft version.
- Each checkout carries one changelog, `CHANGELOG.md`, describing this checkout's release.
- `.gitignore`, `.gitattributes`, `gradlews.ps1`, the wrapper, and the shared agent instructions (`CLAUDE.md`, `AGENTS.md`, `code-review.md`, `code-conventions.md`, `code-review-decisions.md`) are identical in every release checkout. Platform notes and orientation bodies describe their own checkout. Gradle files differ where the Minecraft version, loader, or optional-mod availability requires it; every version number lives in `gradle.properties`, and values derivable from it are derived in the build scripts. Synchronizing instructions does not authorize changing working target versions or integration settings.
- `build-env/` is the snapshot described in `orientation-build.md`; update it whenever a file it covers changes.

# Development and Verification

- Manual play-testing uses MultiMC, not the development runServer environment. GameTests may delete their disposable development world.
- Keep build environments as similar across versions as reasonable; inactive shared machinery is acceptable.
- Recoverable optional-integration failures are isolated to the affected integration so the rest of the mod keeps working.

- Do not compile, build, or run the project (no `gradlew`, no `runGameTestServer`, etc.) unless I explicitly ask. Your job is to read and write code. Verify your work by reading it and reasoning about it. Do not touch the Loom caches, kill processes, or otherwise rewire the dev environment.
- I run the builds and tests. If you believe a build or test run is warranted, say so and let me decide.
- Do not decompile anything (Minecraft or the loaders) without permission.

# Finishing a change

These are part of writing the change, not a second audit.

- After a rename, move, removal, or signature change, search production code, tests, loader implementations, comments, docs, and data for affected references and descriptions. Include string-based Mixin declarations and Architectury implementation names.
- On a multi-loader change, follow the path on each loader from the trigger to the shared code. Do not assume the second loader matches the first.
- Search for the vanilla, loader, and mod calls your new code makes. If other code makes the same calls for the same purpose, the change is not finished: move the decision to one place and have both call it.
- A brief for an agent that will write code names the owning classes, from the "Code map" table in `orientation-code.md`, for whatever it will touch.
- When several agents work in one checkout at once, each owns a separate set of files. Before editing a file another agent may have changed, read its current text; never write a file back from an earlier copy.
- In your report, name any recalled API fact the change depends on and anything only an in-game check can confirm. Distinguish source inspection, successful compilation, passing automated tests, and manual verification. Report owner-supplied results as such; do not infer checks that were not reported.

# Version Control and Commit Messages

- You are not to modify git or interact with github unless explicitly asked.
- When asked to write a commit message, substantive changes, write a commit message with an imperative subject naming the main behavioral outcome, followed by a short body explaining: when the new behavior occurs, the central implementation mechanism, which major variants or platforms it affects, and any important state or compatibility invariant. Keep the body to one compact paragraph of roughly two to four wrapped lines. Do not enumerate files, narrate the work process, or include minor implementation details.

Commit messages are not part of the current conversation; do not talk to the user, report what was done. They should be interpretable without knowledge of the conversation.

# Code Comments

- Code comments are for existing code, not for recording what changed from some historical version, and not for recording future hypotheticals. Just as there is no legacy support in code, there should be no legacy commentary or reference to what was done in the past.
- Migration code may describe the released format it reads; that format is a current input, not history.
- Code comments are not part of the current conversation. They should be interpretable without knowledge of it.
- A comment must be true of the code beside it. Do not state counts of things elsewhere or promise that one piece of code mirrors another; share the code instead.
