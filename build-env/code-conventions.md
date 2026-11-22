# Code conventions

Rules for writing code, organized by what you are doing. Apply them while writing; they are not a final audit. Facts about particular Minecraft versions and loaders are in `platform-notes.md`, and API names used here as examples may differ on your version.

Read code-review-decisions.md before applying a general rule to accepted behavior. Do not turn a deliberate exception into cleanup work.

## Adding a variant of something that exists

- Before writing a new method, search for one that already does the job. Search by what it would do, not by what you would name it: the vanilla and loader calls it would make, the types it takes, the config value or tag it reads. Copies have different names but the same calls inside. If a match exists, call it or extend it. The "Code map" table in `orientation-code.md` names the owners of the recurring ones.
- Before writing the new one, read its siblings. The usual way a mod rots is that each new stack type, loader adapter, or setting copies the last one. When changing a rule, find its existing owner and consumers, including predictions, previews, inverse operations, loader adapters, and diagnostics.
- Decisions go in one place; mechanics may repeat. A gate, a formula, a timing constant, or a choice of which case wins lives in one function that every copy calls. Repeated wiring that holds no decision may stay.
- Add the shared code to an existing class. If sharing needs a new small class to save a few lines, keep the duplication.
- When one change would need edits in several lists, make one declaration drive them all. A setting declares its key, default, range, and comment once, and each loader builds from that. A reset list, copy-out list, or schema written by hand beside the real one is a bug waiting for a missed line.
- A list that mirrors something else in the code, such as command names known to two trees, is built from the same constants.
- One concept has one authoritative predicate. If a tag defines what counts as shears, nothing else checks the class instead.
- A preview, hint, or client prediction calls the same verdict function as the action it predicts. Never restate the rules for display.
- An action and its inverse share a gate. If adding something requires a permission, removing it requires the same one.
- Ask whether a total can fail where each part passes. Validate the prospective whole before committing the part.

## Hooking the game

- Prefer, in order: a method on your own class, a loader event, a loader or vanilla API, a Mixin. Each Mixin's javadoc states why the earlier options do not work.
- Hook the result, not the request. "`heal` was called" does not mean health went up, and a result slot being touched does not mean a trade happened. Check the real change, or hook where vanilla records it.
- Before a Mixin stands in for a loader event, list what the event covers: subclass overrides that skip super, hand-built paths that never reach the method, and gates around the call. Cover every case, and hook the stage the event fires at.
- Rules live in shared services; loader adapters only translate an event into a call. A rule in one adapter is a rule the other loader lacks.
- When you cancel or preempt vanilla's flow, list what that flow would have done and do it yourself: permission checks, game rules, statistics, advancement criteria, durability and destroy events, the arm-swing result.
- Use paired `@Inject`s to bracket context only where the exit hook runs on every supported path, including cancellation. Limit the bracket to calls that need the context. Nested scopes inherit the outer information they need and restore the enclosing scope on exit. Use `@Redirect` or `@Overwrite` only to change the call, since they collide with other mods.
- Listeners that only observe run at the lowest priority, so they never react to something a later mod cancels.
- An optional integration never stops the game from starting. Keep its Mixins in a non-required config, and do not give it a version floor that blocks startup.
- Do not check the side where only one side can reach the code. Keep the check where both can.
- Use the objects a callback hands you. Do not look up what you were given and then null-check the lookup.
- Interaction results control what happens next. Return `PASS` when no mod action applies. Use `FAIL` for deliberate refusal and `SUCCESS`/`CONSUME` for a performed action or its matching client prediction. Loader adapters preserve the shared service's result rather than reconstructing it.

## Guards and failures

- Before writing a check, name the input that reaches it. If you cannot, do not write it.
- Check a fact once, at the entry point every path passes through. Do not stack the same check in layers, and do not leave one path without it.
- `@Nullable` means null really flows here. Fields a codec requires and values every caller has checked are not nullable.
- Do not throw for a case that would otherwise harmlessly do nothing, least of all on a render or send path.
- Do not wrap an API that reports failure by return value in try/catch.
- Catch the narrowest exceptions that can occur: `ReflectiveOperationException | LinkageError` around reflection, never `Throwable`. Do not use a caught exception as a type test.
- A per-item catch may isolate an expected filesystem or integration failure so other items can succeed. Log which item failed. Do not catch ordinary processing defects and report them as malformed input; use a codec's failure result for malformed data.
- Trust our code and platform and mod contracts unless the relevant behavior is known to be unreliable or unguaranteed. Guard actual boundaries, including filesystem and reflection failures; do not invent speculative failure paths. Network peers remain untrusted.
- Do not suppress deprecation or removal warnings. Move to the replacement.

## Removing what is not needed

- When the last reader of a parameter, field, or branch goes, it goes too.
- No function that only forwards to one other call. Exceptions: forced platform bridges, overloads that supply a default to several callers, and one-line event adapters.
- Each type has one decoding entry point, not a static factory beside a private constructor doing the same job.
- Do not write a branch the caller already excludes, and do not document behavior no production path can reach.

## Porting a change to another version

- Carry the intent, not the text. Re-derive the fix from the target's own code. "Does not apply" is a valid result when the target already gets it right by other means, such as data-driven content, render states, or a loader API that now exists; do not port code to fix a problem the target does not have.
- Take only the hunks that belong to the change. Copy a whole file only when its entire diff against the source is that change; otherwise you overwrite the target's own API forms, integrations, and pending work.
- Re-establish every fact the change rests on for the target version. Vanilla and loader APIs are added, removed, renamed, and retimed between versions (Forge has no `FakePlayer` after 1.20.1), so a name, signature, or event order remembered from another version is a hypothesis. Read the target's `platform-notes.md` first and record there what you establish.
- A workaround for one version's quirk goes away where the quirk is gone. Delete runtime hooks and abstractions the target cannot use. Shared cross-version build machinery is an exception; do not remove it solely to make the checkout look version-pure.
- Count the target's loaders. A fix written for Forge needs its NeoForge counterpart where NeoForge exists, traced from that loader's own event, which may not be the one Forge uses.
- Data files are code. Tags, advancement criteria, recipes, and enchantments change shape between versions, and one bad key can stop the whole registry from loading. Port data with the same care as Java.

## Values and text

- Name a value when it is tunable, shared, or not obvious from context. A fixed platform fact that reads naturally where it stands, such as 16 pixels per block, may stay literal.
- Declare a shared value once, and derive from vanilla's constant when one exists. Do not tie two values to one constant because they happen to be equal today.
- Name sentinel values. Use an enum, not integers, for a closed set.
- Use imports, not fully qualified names, except in reflective strings, Mixin targets, and javadoc links.
- Format numbers in player-facing or machine-readable text with `Locale.ROOT`, through one helper. Command arguments do not parse a comma decimal.
- Lang entries are whole sentences a translator can reorder, never fragments assembled in code.
- A message read by someone without the mod carries a fallback, because they have no lang file.
- Logs are readable English on a dedicated server, which may have no lang files loaded.
- Use the format's existing serializer for structured output rather than assembling JSON, TOML, or similar syntax by hand.

## Saved data and state

- Reading never creates or changes state. A getter that creates storage turns a look into a permanent save-file change.
- The empty state is the absent state. Removing the last of something removes its record, so the object matches and stacks with one that never had it.
- Store only known fields. Rebuild data taken from an item, a packet, or a block entity load instead of keeping the incoming compound.
- Never store a permanent decision based on data that is missing or not yet loaded.
- Validate a proposed replacement before disturbing current state. A refused operation preserves the previous selection, ownership, target state, and item costs. Keep rollback scoped to the unit the operation treats as atomic.
- When inserting, deleting, or reordering indexed data, update every dependent reference, including the active draft. Preserve identifiers and ordering that saved data uses unless the change includes the required migration.

## Runtime state and scope

- For each retained state, establish its owner, identity key, and lifetime. Handle the transitions that actually affect it: replacement, conversion, dimension change, unequip, entity removal, reload, disconnect, or server stop. Repeated join or load notifications must be idempotent.
- Choose identity or value equality according to what the cache stores. A cache of data belonging to one object instance must not return another instance's data merely because the objects compare equal. Clear model-dependent caches when their models are rebuilt.
- Keep received authoritative state separate from disposable render caches when it must survive cache eviction. Preserve deliberate differences between world and GUI rendering when consolidating code.
- Mutate game-owned state and publish its consequences on the owning game thread. Moving only the notification onto that thread leaves the mutation unsafe.

## Networking

- Validate structure, field membership, and intrinsic bounds in the decoder. Encoders do not validate. Authorization and checks requiring current server state remain in the execution path.
- Keep what changes rarely and what changes often in separate packets, so a small setting change does not resend a large definition set or reset client state.
- Do not send the default when the recipient already interprets absence that way. Send a reset when the recipient may retain a previous nondefault state.
- Encode a broadcast once and send the same packet to everyone.
- Use explicit, stable ids and compact forms: `VarInt` for entity ids, a small id over a resource name for a closed set.
- Size limits include the loader's own framing.
- Check the version in one place, and make a mismatch say so rather than look like a missing mod.
- For replicated behavior, identify the authoritative endpoint, recipients, and initial or mid-effect catch-up state. Include the acting player where appropriate. A cache shared within one client does not synchronize other clients.
- Send state only after the recipient has the entity or other object needed to apply it, or explicitly retain it until that prerequisite arrives. Establish the ordering on each loader rather than assuming similarly named tracking events fire at the same stage.
- A wire-format change updates the target checkout's protocol version and every reader, writer, caller, and serialization test. Preserve established explicit IDs; do not copy another checkout's protocol number.

## Commands

- Keep /ss as the only command root, with the client/server routing it needs. Favor a simpler player interface even when the code is more complicated.

- `requires` tests stable permission only. Check mutable state, such as game mode, at execution, with a real failure message.
- Report through `sendSuccess` and `sendFailure`. Return 0 on failure and a count on success, so `execute store` and command blocks read the truth.
- Operator actions that change the world broadcast to operators and the console: `sendSuccess(..., true)`.
- Permission rules live in one gate that every command tree uses.

## Logging

- A reader of the log can tell whether the mod is working. Log startup milestones at the point they actually completed, with the mod version, and list detected integrations once, from the entry point rather than a static initializer.
- Log effective settings at load and on change, including the ones that could make the mod appear broken.
- WARN means someone should act. Anything the message itself calls expected is INFO.
- Print the summary line every time, and word it so an empty result cannot read as a healthy one.
- A failure message says what stays in effect, and no success line follows a failure.
- Do not guess a cause in a log message. State what was found.
- Name the file or object at fault. A count alone sends the reader hunting.
- Code reachable per frame or per tick logs at most once.
- A shared message states only what is true of every caller. Do not invent an exception to fill a logging parameter.
- Derive diagnostics from the checks and outcomes the operation actually uses. Do not maintain a second integration-detection list. A decode failure, partial acceptance, and successful installation are different outcomes; the messages and summary counts must describe the chosen outcome consistently.

## Hot paths

- Render, tick, and per-item GUI paths start with the cheapest test that rejects the common case, before any allocation, map access, config read, or clock read.
- Do not decode on a draw path. Decode when the source changes and keep the result.
- If a change arrives by packet or load, react then. Do not poll for it.
- Do not recompute per frame what is consumed per tick, or what never changes. Build lookups once, after registries freeze, as immutable maps if two threads read them.
- Keep the cost in proportion. A loop over a few entries does not justify new invalidation state.

## Tests

- Drive the real hook, not the service behind it. A test that calls the service cannot catch a hook that never fires.
- Cover the trigger on each loader, including the cases a hook is likely to miss, such as sneaking players, adjacent blocks, and automation.
- Build the expected value independently. A test built on the same model as the code passes on the same mistake.
- Do not test a path production cannot reach.
- A permission or gate needs a test in both directions, with a second player where relevant. Assert unchanged state and costs on refusal. Include an allowed control so a disconnected hook or an always-refusing implementation cannot pass.
- Establish age, definitions, configuration, permissions, and other prerequisites before firing the trigger. Demonstrate that the trigger actually occurred: damage reduced health, a trade completed, or the intended command executed. Avoid uncontrolled spawn chances and unrelated ambient behavior.
- Keep timing tests on the clock production uses, with enough timeout for the scenario. Restore changed configuration, definitions, game rules, equipment, permissions, and temporary listeners after the last scheduled assertion, including failure paths.
- When replacing internal tests with real-trigger tests, retain their useful assertions and negative cases. State the limit of wrapper coverage: invoking a callback does not establish complete packet ordering, a full respawn transition, or visibility on another client.

## Comments and docs

- A javadoc states what is true now. "Same as the other loader" is a claim someone must verify, and it is usually how a loader difference hides.
- Say why a surprising choice was made, naming what the obvious alternative fails at, so the next reader does not undo it.
- Test reproduction instructions name the setup, action, and expected observation. If a test has no ordinary in-game equivalent, say "No in-game form" and explain what it guards instead of inventing player steps.

## Organization

- Package names must be true. Client-only code lives under `client`, loader code in a package named for its loader, and nothing in a `server` package runs on the client.
- Matching files on each loader follow one naming scheme, so one name predicts the other.
- Loader-only adapters belong in that loader. Optional integration support may stay in common when it shares the existing pipeline.
- Use the build layout the toolchain expects. A custom source root needs a reason a build would fail without it.

## Member ordering

Order members within a class/interface/record alphabetically, **to the extent the
Java language allows**. The exceptions below are the only places where another rule
overrides alphabetical — everything else sorts by name (case-insensitive).

We order for readability and let the few real constraints win where they apply.

### Member order within a type

```
1. static final constants          (alphabetical; dependency-order exception)
2. other static fields             (alphabetical; dependency-order exception)
3. instance fields                 (alphabetical; dependency-order exception)
4. static / instance init blocks   (placed by dependency, not alphabetical)
5. constructors                    (by arity, fewest parameters first)
6. nested enums                    (alphabetical)
7. nested records                  (alphabetical)
8. other nested classes/interfaces (alphabetical)
9. methods                         (alphabetical)
```

Within each group, sort alphabetically by name unless a rule below says otherwise.
Overloaded methods cluster naturally by shared name; order them by arity, then by
parameter type names.

### Rule: methods and nested types are always alphabetical

Java imposes no ordering on methods or nested type declarations, so these always sort
by name with no exceptions. This covers the bulk of any reordering.

### Exception: field initialization order

Fields (static **and** instance) initialize top-to-bottom. Alphabetical order breaks
when one field's initializer reads another field declared below it. The Java language
(JLS 8.3.3) splits this into two cases, and **constants are not blanket-exempt** from
either — the inlining of compile-time constants only matters for the second case.

- **Simple-name reference → compile error.** If a field's initializer reads a sibling
  field by its *simple name* (e.g. `... = create(MOD_ID)` referring to a `MOD_ID` field
  in the same class), that field must be declared *earlier*. A later declaration is a
  hard compile error (`illegal forward reference`) **even when the target is a `final`
  constant** — and even when the read is inside a lambda body (`() -> MOD_ID`). The one
  carve-out is a reference inside an *anonymous class* body, whose innermost enclosing
  class differs, so 8.3.3 does not apply there. Because the compiler rejects these, they
  can't ship silently; dependency order simply wins.
- **Qualified-name / cross-class reference → silent hazard.** A read through a qualified
  name (`ClassName.FIELD`) or from a different class is *not* restricted by 8.3.3, so it
  compiles. There, a compile-time constant is inlined and reads correctly regardless of
  order, but a non-constant object field silently yields `null`/`0` if it hasn't
  initialized yet.

When such a dependency exists, dependency order overrides alphabetical for *those fields
only*. Order the rest of the group alphabetically around them — in practice, a
"smallest name whose dependencies are already satisfied" (greedy topological) ordering.
Add a brief comment on any field that sits out of alphabetical order for this reason.

### Exception: enum constants and `ordinal()`

Within an enum, the constants are declared first (a language rule) and may be
alphabetized like anything else. But **do not serialize an enum by `ordinal()`** —
reordering the constants would silently change the format. Serialize by name or an
explicit, stable id instead, so the constants stay freely reorderable.

The same principle applies to any value whose meaning is derived from declaration order
(e.g. network packet discriminators): assign explicit, stable ids rather than deriving
them from order, so the source can be alphabetized without changing behavior.

### Exception: the identity class header

`SomeStacksCommon` starts with the mod's identity constants in a fixed,
**non-alphabetical** order:

```
MODID
LOGGER
```

This is the conventional Forge idiom — `MODID` first because `@Mod` and every registry
references it, then `LOGGER`. Any other constants in the class follow these and are
ordered normally (alphabetical, subject to the field-initialization exception).

### Exception: deliberately grouped field blocks

Alphabetical is the default for fields, but a field block that is a **deliberate aligned
table** — comma-multi-declarations (`float prevX, x;`), `prev`/current animation-channel
pairs, or a run of one-per-line fields under explanatory section comments — may keep its
existing order when alphabetizing would scatter tightly-related declarations and orphan
their comments. Constants still precede non-constant fields (the group ordering holds); only
the order *within* the block is preserved. When in doubt, alphabetize — reserve this for
genuine tables.

### Notes

- Registration/wiring **statements** inside a method (e.g. the `DeferredRegister.register`,
  event-bus, and config calls in the Forge `SomeStacks` constructor) are not members and aren't
  alphabetized. They read top-to-bottom and are grouped by concern. Their call order is also
  behaviorally free: each merely subscribes a deferred registry/handler to a bus, and Forge
  fires the actual registration later in its own order (by registry type, event, and listener
  priority) — not in call order.
- Constructors are not sorted among methods; the constructor's name is the class name,
  which would drop it at an arbitrary alphabetical position. They get their own slot
  (group 5), ordered by arity.
- Non-constant fields (mutable statics, instance fields) are not "constants"; they fall
  into groups 2 and 3, still subject to the field-initialization exception.
