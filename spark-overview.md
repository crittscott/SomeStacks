# spark: overview for Some Stacks performance work

Orientation notes on the spark profiler mod and how it fits the mod's efficiency work. Nothing in the project references spark; this describes an external tool, not an existing integration.

## What spark is

spark is lucko's profiler and diagnostics mod — the same author as LuckPerms. It is the de facto standard tool for Minecraft performance work, available for Forge 1.20.1 on both Modrinth and CurseForge. It runs on the client, the dedicated server, or both, and requires no code changes to the mod being profiled.

Three things live under one mod:

1. A sampling CPU profiler — the part that matters here.
2. Memory inspection — heap summaries, full heap dumps, GC monitoring.
3. Health monitoring — TPS, MSPT, tick durations, ping, CPU and memory gauges.

## What sampling does and does not tell you

spark is a statistical sampling profiler, not an instrumenting one. It captures stack traces at intervals (default around 4ms) and aggregates them into a call tree weighted by how often each frame appeared. That yields where time goes, at roughly 1% overhead, without distorting the thing being measured.

It does not yield invocation counts. A method called five hundred times that returns in 200ns barely registers in a sample-weighted tree even when the call count is the defect — which is the shape of the capability-walk problem recorded in the living specification, where the same run resolution was repeated hundreds of times a tick. spark therefore complements an internal call tracker rather than replacing it: the tracker answers *how many times*, spark answers *how long*.

## Getting it running

Drop the Forge jar into `run/mods/` for a development run, or declare it as a `runtimeOnly fg.deobf(...)` dependency if it should be managed by the build.

Two command roots:

- `/spark` — server side, operator gated.
- `/sparkc` — client side, works in singleplayer and against servers that do not have it.

## Commands and flags that matter

```
/spark profiler start --thread "Server thread" --timeout 300
/spark profiler stop
```

The capture uploads to `spark.lucko.me/<id>` and the command prints the link.

| Flag | Effect |
| --- | --- |
| `--only-ticks-over <ms>` | Record only ticks exceeding a threshold. The best flag for hunting spikes: everything else is filtered out, so an occasional expensive settle pass appears alone. |
| `--thread *` | Profile all threads rather than the main one. Use for chunk and IO work; leave off for tick work. |
| `--interval <ms>` | Sampling interval. Drop to 1–2ms for short, detailed captures. |
| `--alloc` | Allocation profiling: the tree is weighted by bytes allocated rather than time. Good for finding per-tick garbage in hot paths such as a capability walk. |
| `--timeout <s>` | Auto-stop and upload. |
| `--combine-all` / `--separate-parent-calls` | Collapse or split identical methods reached by different call paths. |

Other useful subcommands:

- `/spark profiler open` — spark runs a low-rate background profiler continuously by default, so a profile can be opened after the fact for lag that has already happened.
- `/spark tps`, `/spark health` — quick vitals, before deciding a profiling run is warranted.
- `/spark heapsummary` — class histogram without a full heap dump.
- `/spark activity` — log of past spark runs and their links.

## The viewer

The web viewer is the substance of the tool: a flame graph plus a collapsible call tree. Frames are attributed to the mod that owns them, read from mod metadata, so the tree can be filtered to `somestacks` alone and read apart from Forge and vanilla cost. spark downloads and applies mappings for the Minecraft version, so 1.20.1 frames read as `getBlockState` rather than `m_1234_`.

Client-side captures profile the render thread, which covers the block entity renderers — `CubeRenderHelper` and the per-cell draw paths appear there under the `ENTITYBLOCK_ANIMATED` render pass.

## A working procedure

1. Reproduce the load deliberately: build a test wall with `ss test list`, or attach a storage network's external storage to a run.
2. `/spark profiler start --thread "Server thread" --interval 2 --timeout 120`.
3. Filter the viewer to `somestacks` and read the tree top down.
4. When a cheap method shows a suspiciously large aggregate, switch to the internal call tracker to get the count.
5. Re-run with `--alloc` to check whether the same path is also churning garbage.

## Caveat

On Windows, spark falls back to the Java thread-dump sampler rather than async-profiler, which is Linux only. The result is still accurate, slightly coarser, and adequate for tick-level work.
