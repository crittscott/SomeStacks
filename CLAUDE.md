###### v14

YOU ARE NOT TO DECOMPILE OR UNARCHIVE ANYTHING, EVER. NO EXCEPTIONS. YOU ARE NOT TO TREAT EVERY TASK AS OF WORLD-ENDING IMPORTANCE TO GET RIGHT. YOU ARE NOT TO SEARCH THE ENTIRE INTERNET IN AN ATTEMPT TO MAKE SURE YOU HAVE AN IRREFUTABLE PROOF THAT EVERY WORD OF YOUR AS YET UNWRITTEN ANSWER IS PERFECTLY CORRECT.

# Language
Use American English, not British English. Be concise.

# Scope of analysis and work
Your domain of interest is the project at hand. Only if you cannot answer the question by looking here are you to look outside.

Do not decompile Forge or Minecraft, and do not read, extract, or search any decompiled/remapped source for them either — this includes ForgeGradle/Loom-cached "sources" jars, *_mapped_* jars, javap/bytecode inspection, or anything under .gradle/caches/forge_gradle or similar. If you need to know a Forge/Fabric/Minecraft API signature, ask me or find it in that library's own published API docs/source (e.g. Fabric API's own sources are fine — Minecraft's and Forge's are not).

You are a programmer; you write code. Do not build unless asked. Everything not explicitly a command to build is a discussion.

# Project Principles

- This is a Minecraft mod, not a mission-critical enterprise suite. Only one version of the mod ever runs at a time, so it never interoperates with earlier versions of itself. When code or behavior changes, remove the old path entirely: no shims, fallbacks, deprecated aliases, or readers for formats that never shipped.
- Repository data (resources, data packs, test fixtures) changes format in place. When a format changes, convert all of it at that time, using deterministic tools (CLI utilities, python, etc.) unless the conversion needs an AI.
- Player worlds are the exception, and they are handled as migration, not compatibility. A world saved by the previous released version must load. Convert its saved data to the current form as soon as the mod first reads it, and write only the current form afterward. Keep this conversion in a dedicated migration layer so the rest of the code sees only current state. Support only the immediately preceding released version. Never silently discard saved player data that can't be recognized; set it aside and log it.
- Boring beats clever. Do things the vanilla, Forge, NeoForge, and Fabric way: if Minecraft or the loader has a facility for a goal, use it, even when a home-grown version would be shorter or already works. An expert Minecraft modder should look at our code and find nothing surprising.
- The mod acts as the player. Anything the mod does to the world on a player's behalf passes the same permission checks and fires the same events vanilla would if the player did it by hand, so protection, claims, and other mods' hooks apply without special handling.
- Each concept has one home. A behavior lives in exactly one place; when code is replaced, the old version is deleted, not left beside it.
- Share what is the same; diverge only where the loader forces it. Common code holds what is truly common, and each loader-specific difference has a loader-specific reason. Do not contort code to share a few lines.
- Work happens when something changes, not on a schedule. Prefer events and maintained lists over per-tick polling, broad area scans, and repeated sorting. Send network traffic only when state changes.
- Nothing crosses a trust boundary unchecked. The server validates everything a client sends; the client validates everything a server sends. Trusting Minecraft and the loader does not extend to the other end of the connection.
- Everything visible is deliberate. Player-facing text lives in the lang file; tunable numbers are named constants or config options; logs record milestones, not traffic, and debug logging does not stay in the code; every player-visible behavior has a gametest whose javadoc tells a person how to reproduce it in-game.
- When assessing code for safety issues, do not worry that Minecraft or Forge itself may misbehave; it is not our job to protect against every conceivable error. Only known unreliable interfaces need to be protected against.
- The mod should be server friendly; we need to pay attention to how much work we require the server to do (and the client, of course, but that's a much smaller problem). The configuration should provide the server admin with the abilities an admin would find useful.
- Conceptual efficiency is more important than lines-of-code efficiency: I prefer deeper classes of coherent content to an atomized single-function-per-class architecture. Some duplication is better than yet one more small class.

# Orientation files

`orientation-player.md` and `orientation-code.md` are snapshots of the mod as it currently is, written so an agent can orient itself without reading the whole codebase. They are not specifications, requirements, goals, or a to-do list.

- The code is the truth. When a file disagrees with the code, the file is wrong. Fix the file, not the code, unless I say otherwise.
- Never treat a described behavior as a requirement to preserve, or a listed limitation as work to do.
- After a change alters what a file describes, update that file in place to match the code.
- Size limits: `orientation-player.md` ≤ 15,000 characters; `orientation-code.md` ≤ 12,000 characters. Trim when over the limit.
- The fixed header at the top of each file is not content: do not edit it, and it does not count toward the size limit.

# Markdown

- Do not hard-wrap prose in Markdown files: write each paragraph or list item as one line and let the editor wrap it. Tables and code blocks are unaffected.

# Development and Verification

- Do not compile, build, or run the project (no `gradlew`, no `runGameTestServer`, etc.) unless I explicitly ask. Your job is to read and write code. Verify your work by reading it and reasoning about it. Do not touch the ForgeGradle caches, kill processes, or otherwise rewire the dev environment.
- I run the builds and tests. If you believe a build or test run is warranted, say so and let me decide.
- Do not decompile anything (Minecraft, Forge) without permission.

# Version Control and Commit Messages

- You are not to modify git or interact with github unless explicitly asked.
- When asked to write a commit message, substantive changes, write a commit message with an imperative subject naming the main behavioral outcome, followed by a short body explaining: when the new behavior occurs, the central implementation mechanism, which major variants or platforms it affects, and any important state or compatibility invariant. Keep the body to one compact paragraph of roughly two to four wrapped lines. Do not enumerate files, narrate the work process, or include minor implementation details.

Commit messages are not part of the current conversation; do not talk to the user, report what was done. They should be interpretable without knowledge of the conversation.

# Code Comments

- Code comments are for existing code, not for recording what changed from some historical version, and not for recording future hypotheticals. Just as there is no legacy support in code, there should be no legacy commentary or reference to what was done in the past.
- Migration code may describe the released format it reads; that format is a current input, not history.
- Code comments are not part of the current conversation. They should be interpretable without knowledge of it.

# Minecraft and loader architecture details

- There is no Forge FakePlayer after 1.20.1.