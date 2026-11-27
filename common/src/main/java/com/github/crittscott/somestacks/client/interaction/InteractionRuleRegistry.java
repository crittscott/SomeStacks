package com.github.crittscott.somestacks.client.interaction;

import java.util.List;

/**
 * The ordered rule lists behind each loader's right-click callbacks: one for clicks on a block, one
 * for clicks that reach no block (an empty-hand click at air, or a held-item use).
 *
 * <p>Order is the precedence rule: the first match wins and the walk stops. The block list starts
 * with the empty-handed permanence toggle, then the sneaking torch gestures, then the modified-item
 * click that the server resolves into a deposit or placement, and ends with unmodified extraction.
 * The air list cycles the mode at open air before refusing any other modifier item use.
 */
public final class InteractionRuleRegistry {
    private InteractionRuleRegistry() {}

    private static final List<InteractionRule> AIR_RULES =
            List.of(new ModeCycleRule(), new ModifierItemUseRule());

    private static final List<InteractionRule> BLOCK_RULES =
            List.of(
                    new TogglePermanentRule(),
                    new RotateBlockWithRedstoneTorchRule(),
                    new RotateItemWithSoulTorchRule(),
                    new ModifiedItemClickRule(),
                    new ExtractionRule());

    /**
     * Executes the first air rule that matches and returns it, or null when none does; the caller
     * reads the context to learn whether to cancel the click.
     */
    public static InteractionRule processAirRules(InteractionContext ctx) {
        return processFirstMatch(AIR_RULES, ctx);
    }

    /** Like {@link #processAirRules}, over the block rules. */
    public static InteractionRule processBlockRules(InteractionContext ctx) {
        return processFirstMatch(BLOCK_RULES, ctx);
    }

    private static InteractionRule processFirstMatch(
            List<InteractionRule> rules, InteractionContext ctx) {
        for (InteractionRule rule : rules) {
            if (rule.matches(ctx)) {
                rule.execute(ctx);
                return rule;
            }
        }
        return null;
    }
}
