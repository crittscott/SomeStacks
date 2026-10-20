package com.github.crittscott.somestacks.block;

/**
 * Holds off an automation edit that arrives while another one is still running.
 *
 * <p>One flag serves the whole server and is touched only on the server thread. Callers pair a
 * successful {@link #begin()} with {@link #end()} in a {@code finally}. An edit that arrives while
 * another is running is refused with the ordinary failure result for automation: an insertion
 * keeps its stack and an extraction yields nothing.
 */
public final class RunEdit {
    // A run's world edits notify neighbors, and a woken neighbor can reach back into the same run's
    // handler mid-call: while a batch is open, while the block list is being appended to, or while
    // a held automation view is being invalidated. The inner call is refused so the run stays in the
    // state the outer call is building.
    private static boolean inProgress;

    private RunEdit() {
    }

    /** Claims the right to edit, or reports that an edit is already running. */
    public static boolean begin() {
        if (inProgress) {
            return false;
        }
        inProgress = true;
        return true;
    }

    /** Whether an outer automation mutation currently owns the structural-edit guard. */
    public static boolean isInProgress() {
        return inProgress;
    }

    /** Releases the guard after, and only after, a successful {@link #begin()}. */
    public static void end() {
        inProgress = false;
    }
}
