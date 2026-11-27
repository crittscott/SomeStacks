package com.github.crittscott.somestacks.client.interaction;

import java.util.List;

import javax.annotation.Nullable;

/**
 * The ordered rule lists behind each loader's right-click callbacks: one for clicks on a block, one
 * for clicks that reach no block (an empty-hand click at air, or a held-item use).
 *
 * <p>Order is the precedence rule: the first match wins and the walk stops. The block list starts
 * with the empty-handed permanence toggle, then the sneaking torch gestures, then the modified-item
 * click that the server resolves into a deposit or placement, and ends with unmodified extraction.
 */
public final class InteractionRuleRegistry {
    private InteractionRuleRegistry() {}

    private static final List<InteractionRule> AIR_RULES = List.of(new ModeCycleRule());

    private static final List<InteractionRule> BLOCK_RULES =
            List.of(
                    new TogglePermanentRule(),
                    new RotateBlockWithRedstoneTorchRule(),
                    new RotateItemWithSoulTorchRule(),
                    new ModifiedItemClickRule(),
                    new ExtractionRule());

    /**
     * Runs the first matching main-hand air or held-item rule; returns that rule or null when none
     * claims it.
     */
    @Nullable
    public static InteractionRule processAirRules(InteractionContext ctx) {
        return processFirstMatch(AIR_RULES, ctx);
    }

    /** Runs the first matching main-hand block rule; returns the claimed rule or null. */
    @Nullable
    public static InteractionRule processBlockRules(InteractionContext ctx) {
        return processFirstMatch(BLOCK_RULES, ctx);
    }

    @Nullable
    private static InteractionRule processFirstMatch(
            List<InteractionRule> rules, InteractionContext ctx) {
        if (!ctx.isMainHand()) return null;
        for (InteractionRule rule : rules) {
            if (rule.matches(ctx)) {
                rule.execute(ctx);
                return rule;
            }
        }
        return null;
    }
}
