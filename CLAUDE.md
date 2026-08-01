###### v5

# Language
Use American English, not British English.

# Scope of analysis and work
Your domain of interest is the project at hand. Only if you cannot answer the question by looking here are you to look outside.

Do not decompile Forge or Minecraft. Do not decompile Gradle or adjust the development environment. If you believe that the source of an issue lies deep within some external asset, stop and declare that, do not go on extended hunts to prove it.

You are a programmer; you write code. Do not build unless asked. Everything not explicitly a command to build is a discussion.

# Project Principles

- This is an unreleased Minecraft 1.20.1/Forge mod, not a mission-critical enterprise suite. There is to be no legacy support; no concern for backward compatibility. If we change data formats, we update all the stored data at the time of the change. Unless the data needs an AI to work out the new format, use deterministic tools (CLI utilities, python, etc).
- We should do things "the Forge and Minecraft way". If Forge/Minecraft has a facility for accomplishing a goal, we should use it. An expert Minecraft modder should look at our code and say "Yes, this is the way it should, and is expected to be done.".
- When assessing code for safety issues, do not worry that Minecraft or Forge itself may misbehave; it is not our job to protect against every conceivable error. Only known unreliable interfaces need to be protected against.
- The mod should be server friendly; we need to pay attention to how much work we require the server to do (and the client, of course, but that's a much smaller problem). The configuration should provide the server admin with the abilities an admin would find useful.

# Development and Verification

- Do not compile, build, or run the project (no `gradlew`, no `runGameTestServer`, etc.) unless I explicitly ask. Your job is to read and write code. Verify your work by reading it and reasoning about it. Do not touch the ForgeGradle caches, kill processes, or otherwise rewire the dev environment.
- I run the builds and tests. If you believe a build or test run is warranted, say so and let me decide.
- Do not decompile anything (Minecraft, Forge) without permission.

# Version Control and Commit Messages

- You are not to modify git or interact with github unless explicitly asked.
- When asked to write a commit message, make it succinct. Commit messages are not part of the current conversation. They should be interpretable without knowledge of it.

# Code Comments

- Code comments are for existing code, not for recording what changed from some historical version, and not for recording future hypotheticals. Just as there is no legacy support in code, there should be no legacy commentary or reference to what was done in the past.
- Code comments are not part of the current conversation. They should be interpretable without knowledge of it.
