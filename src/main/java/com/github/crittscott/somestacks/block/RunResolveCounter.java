package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.SomeStacks;
import net.minecraftforge.event.TickEvent;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Measurement scaffolding: counts how often a vertical run is resolved from the world.
 *
 * <p>Every item-handler call resolves its pile or column afresh, walking the block entities above
 * and below and building a list. Whether that matters is a question about call volume, which is a
 * thing to count rather than to reason about, so this counts it: a run of digits per ten seconds,
 * logged only when something is actually resolving.
 *
 * <p>This exists to answer one question and should go once it has. Delete this class, the three
 * call sites in {@code at()}, and the tick listener in {@code SomeStacks}.
 */
public final class RunResolveCounter {
    private RunResolveCounter() {}

    private static final int REPORT_INTERVAL_TICKS = 200;

    private static final AtomicLong storage = new AtomicLong();
    private static final AtomicLong singles = new AtomicLong();
    private static final AtomicLong bar = new AtomicLong();

    private static int ticksSinceReport;

    static void countStorage() {
        storage.incrementAndGet();
    }

    static void countSingles() {
        singles.incrementAndGet();
    }

    static void countBar() {
        bar.incrementAndGet();
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++ticksSinceReport < REPORT_INTERVAL_TICKS) {
            return;
        }
        ticksSinceReport = 0;

        long storageCount = storage.getAndSet(0);
        long singlesCount = singles.getAndSet(0);
        long barCount = bar.getAndSet(0);
        long total = storageCount + singlesCount + barCount;
        if (total == 0) {
            return;
        }

        int seconds = REPORT_INTERVAL_TICKS / 20;
        SomeStacks.LOGGER.info("Run resolutions over {}s: {} Storage, {} Singles, {} Bar; {}/s overall",
                seconds, storageCount, singlesCount, barCount, total / seconds);
    }
}
