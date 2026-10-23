
We have done a number of general code review sessions in previous conversations. Now I want to focus on particular topics.

Spawn subagents, one for each item in the list. Give each subagent the prompt below, filling in <topic title> and <topic description> from the list. Spawn at most 4 agents at once.

Topics:
1. Dead-code: Do we have dead code or duplicated code?
2. Organization: Do we have scattered functionality that could be consolidated? Code is cheap; conceptual complexity is not. We should prefer fewer thick classes, rather than atomized functionality.
3. Protection: Do we honor protection and claims?
4. Magic-numbers: Do we have magic numbers scattered about? Magic strings? Things that belong in en_us.json? Do we reference complete class names instead of using imports?
5. Reinvention: Do we duplicate Minecraft/Forge/NeoForge/Fabric functionality? Do we trigger the expected events for the things we do?
6. Guarding: Do we guard against impossible errors? Remember that we trust Minecraft and the loaders.
7. Javadocs: Do we have a full set of javadocs? Do they say what javadocs should say, or are they describing history, algorithm internals, hypotheticals, etc.? Do they match the code? Do the gametests have javadocs that describe the test in a way that a person can duplicate the test in-game?
8. Legacy: There is to be no legacy to support; there are no older versions to be backward compatible with (player-world migration from the immediately preceding release is the one exception). Do we have legacy support, maintain backwards compatibility, or layer functions on functions (i.e. myFunc1 just calls myFunc2)?
9. Networking: Is our network secure, both from malicious clients and malicious servers? Is it efficient? Do we spam the network?
10. Loader-divergence: If the mod supports multiple loaders, is there only necessary divergence between the code bases? Some things are hard in one loader and not in another, so it makes sense to implement them differently. Is there residue of a previous state where the code was mono-loader?
11. Multiloader-organization: Are the right things in common, forge, etc.? Herculean efforts to move functionality to common just to avoid a little code duplication are bad.
12. Expected-way: Forge/Fabric/NeoForge/Minecraft each have standard ways of doing things; each loader provides facilities. Do we use them?
13. Efficiency: Do we sort every tick? Do we search giant AABB boxes when we could keep a list? Are we efficient in time and space in general?
14. Build-environment: Have we accumulated cruft? Are we doing things the expected way? Are per-loader differences reasonable?
15. Test-completeness: Tests are cheap. We don't need to target only vital questions. Nor, though, do we need to test everything imaginable or trivial variations. Is the test system complete? Is it over-complete?
16. Logging: It should be possible to tell if the mod is working from its log messages. But we do not want to log everything. Do we log the various major events of mod startup? Is there any residual debug logging?


Prompt:

You are an experienced Minecraft modder doing a targeted code review of this mod. Your topic is <topic title>: <topic description>

The mod has grown via evolution, and the review exists to find what that evolution left behind.

## Before you start

1. Read CLAUDE.md and follow it. In particular: do not decompile, unarchive, or read Minecraft/Forge source in any form, and do not build or run anything.
2. Read orientation-code.md (and orientation-player.md if your topic touches player-visible behavior). They are orientation, not specification; the code is the truth.
3. Then read the code relevant to your topic.

## Rules

- Assess the project as it is. Do not inspect git history.
- Do not make any changes to the code. Your only output is the report file.
- Do not read other review-*.md files. Other agents are working in the codebase at the same time.
- Do not go off and research. Use what you know and the code you are given. If you are unsure whether something is the expected approach, say so in the finding's confidence rather than investigating further.

## Boring code is the goal

We want boring, expected code. An expert Minecraft modder reading any file should find nothing surprising: vanilla mechanisms used where vanilla has them, loader facilities (Forge, NeoForge, Fabric) used where the loader provides them, and conventional structure and naming throughout. This project tends to drift into clever or home-grown solutions even though it's been told not to. Within your topic, treat every place where the code does something its own way instead of the established way as a finding, even if the home-grown version works.

## What counts as a finding

A finding is a specific, verified problem in the code that should be acted on. Every finding must have a location, a consequence, and an action. Leave out:
- musings, general observations, and "it might be worth considering" remarks
- praise and descriptions of code that is fine
- style nits that no one would act on
- hypothetical problems you have not confirmed by reading the code

If you have a real concern but cannot pin it down to an action, put it under Open questions, stated as a question the owner can answer.

## Report

Write your report to review-<topic title>.md in the project root. Do not hard-wrap prose. Use exactly this structure:

```
# Review: <topic title>

## Summary
Two to four sentences in plain English: overall state of the code for this topic and the most important thing to do.

## Findings

### 1. <short title in plain English>
- **Severity:** High | Medium | Low
- **Confidence:** Certain | Likely | Unsure
- **Where:** path/to/File.java:123 (list every location)
- **In plain English:** One to three sentences a non-programmer who plays Minecraft could follow. No jargon; if you must name a technical thing, say what it does.
- **Consequence:** What actually goes wrong or costs us, concretely: who notices (player, server admin, maintainer), and when. "Harder to maintain" is not enough; say what becomes harder and why.
- **Action:** What to do, specific enough to implement without further investigation. Name the vanilla or loader facility to use if there is one.
- **Technical detail:** The precise explanation for the programmer: classes, methods, call paths, why the current code is wrong. Short code excerpts are fine.

(repeat for each finding, ordered most severe first)

## Drift from the expected way
One short paragraph: within this topic, where does the code depart from vanilla or loader conventions, and how widespread is it? Refer to finding numbers. If there is no drift, say so in one sentence.

## Open questions
Numbered questions for the owner, each one sentence. Omit the section if there are none.
```

Severity means:
- High: players or server admins can see it (wrong behavior, lost data, a protection bypass, noticeable lag), or it is a security hole.
- Medium: no visible symptom today, but it makes a likely future bug or wasted effort; for example duplicated logic that will drift apart, or a home-grown version of a vanilla or loader facility.
- Low: a cleanup that makes the code more boring and expected, with little risk either way.

Aim for the findings that matter. Twenty real findings are better than sixty padded ones; if you list only some instances of a repeated pattern, make it one finding that says so and lists every location.
