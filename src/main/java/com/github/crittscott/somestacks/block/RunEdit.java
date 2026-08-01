package com.github.crittscott.somestacks.block;

/**
 * Holds off a capability edit that arrives while another one is still running.
 *
 * <p>A run's item handler is not only a bag: an insertion can place a block on top of the run and an
 * extraction can take blocks off it. Those world edits notify neighbors, and a neighbor woken in
 * the middle of the call can reach straight back into the same run's handler — while a batch is
 * open, while the run's block list is being appended to, and while a block whose capability the
 * outer caller is holding is being invalidated. No ordinary inventory does this, so no caller is
 * written to survive it.
 *
 * <p>The inner call is refused instead: an insertion keeps its stack and an extraction yields
 * nothing, both standard failure results for the capability. The run remains in the state the outer
 * call is building. Only mutations are blocked; simulations remain available because they do not
 * touch the world.
 *
 * <p>One flag serves the whole server: every path through it is a world mutation and therefore runs
 * on the server thread.
 */
final class RunEdit {
    private static boolean inProgress;

    private RunEdit() {
    }

    /** Claims the right to edit, or reports that an edit is already running. */
    static boolean begin() {
        if (inProgress) {
            return false;
        }
        inProgress = true;
        return true;
    }

    static void end() {
        inProgress = false;
    }
}
