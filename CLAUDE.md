###### v9

YOU ARE NOT TO DECOMPILE OR UNARCHIVE ANYTHING, EVER. NO EXCEPTIONS. YOU ARE NOT TO TREAT EVERY TASK AS OF WORLD-ENDING IMPORTANCE TO GET RIGHT. YOU ARE NOT TO SEARCH THE ENTIRE INTERNET IN AN ATTEMPT TO MAKE SURE YOU HAVE AN IRREFUTABLE PROOF THAT EVERY WORD OF YOUR AS YET UNWRITTEN ANSWER IS PERFECTLY CORRECT.

# Language
Use American English, not British English. Be concise.

# Scope of analysis and work
Your domain of interest is the project at hand. Only if you cannot answer the question by looking here are you to look outside.

Do not decompile Forge or Minecraft, and do not read, extract, or search any decompiled/remapped source for them either — this includes ForgeGradle/Loom-cached "sources" jars, *_mapped_* jars, javap/bytecode inspection, or anything under .gradle/caches/forge_gradle or similar. If you need to know a Forge/Fabric/Minecraft API signature, ask me or find it in that library's own published API docs/source (e.g. Fabric API's own sources are fine — Minecraft's and Forge's are not).

You are a programmer; you write code. Do not build unless asked. Everything not explicitly a command to build is a discussion.

# Project Principles

- This is an unreleased Minecraft 1.20.1/Forge mod, not a mission-critical enterprise suite. There is to be no legacy support; no concern for backward compatibility. If we change data formats, we update all the stored data at the time of the change. Unless the data needs an AI to work out the new format, use deterministic tools (CLI utilities, python, etc).
- We should do things "the Forge and Minecraft way". If Forge/Minecraft has a facility for accomplishing a goal, we should use it. An expert Minecraft modder should look at our code and say "Yes, this is the way it should, and is expected to be done.".
- When assessing code for safety issues, do not worry that Minecraft or Forge itself may misbehave; it is not our job to protect against every conceivable error. Only known unreliable interfaces need to be protected against.
- The mod should be server friendly; we need to pay attention to how much work we require the server to do (and the client, of course, but that's a much smaller problem). The configuration should provide the server admin with the abilities an admin would find useful.
- Conceptual efficiency is more important than lines-of-code efficiency: I prefer deeper classes of coherent content to an atomized single-function-per-class architecture. Some duplication is better than yet one more small class.

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
- Code comments are not part of the current conversation. They should be interpretable without knowledge of it.

