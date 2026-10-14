package com.github.crittscott.somestacks.block;

/**
 * Holds off an automation edit that arrives while another one is still running.
 *
 * <p>A run's item handler is not only a bag: an insertion can place a block on top of the run and an
 * extraction can take blocks off it. Those world edits notify neighbors, and a neighbor woken in
 * the middle of the call can reach straight back into the same run's handler — while a batch is
 * open, while the run's block list is being appended to, and while a block whose capability the
 * outer caller is holding is being invalidated. No ordinary inventory does this, so no caller is
 * written to survive it.
 *
 * <p>The inner call is refused instead: an insertion keeps its stack and an extraction yields
 * nothing, both standard failure results for automation. The run remains in the state the outer
 * call is building. Forge's explicit simulations remain available because they do not touch the
 * world; Fabric offers are refused because their transaction does not reveal whether it will commit.
 *
 * <p>One flag serves the whole server: every path through it is a world mutation and therefore runs
 * on the server thread.
 */
public final class RunEdit {
    private static boolean inProgress;

    private RunEdit() {
    }

    /**
     * Claims the server-wide automation mutation guard on the server thread.
     *
     * @return false if another edit owns the guard; on true, the caller must call {@link #end()}
     * in a finally block after its edit
     */
    public static boolean begin() {
        if (inProgress) {
            return false;
        }
        inProgress = true;
        return true;
    }

    /**
     * Reports whether an automation mutation owns the global guard. Use only on the server thread.
     */
    public static boolean isInProgress() {
        return inProgress;
    }

    /**
     * Releases the guard after this caller's successful {@link #begin()}. Call on the server thread
     * in a finally block; a caller whose begin failed must not release another caller's guard.
     */
    public static void end() {
        inProgress = false;
    }
}
