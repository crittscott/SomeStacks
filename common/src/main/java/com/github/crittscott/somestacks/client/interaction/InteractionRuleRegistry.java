package com.github.crittscott.somestacks.client.interaction;

import java.util.List;
import javax.annotation.Nullable;

/**
 * The ordered rule lists behind each loader's right-click callbacks, one list per kind of click.
 *
 * <p>The permanence and torch gestures precede the generic modified deposit/placement gesture.
 * Unmodified extraction sits last. Target selection belongs entirely to the server.
 */
public final class InteractionRuleRegistry {
    private InteractionRuleRegistry() {}

    private static final List<InteractionRule> EMPTY_HAND_RULES = List.of(
            new ModeCycleRule()
    );

    private static final List<InteractionRule> ITEM_RULES = List.of(
            new ModeCycleRule()
    );

    private static final List<InteractionRule> BLOCK_RULES = List.of(
            new TogglePermanentRule(),
            new RotateBlockWithRedstoneTorchRule(),
            new RotateItemWithSoulTorchRule(),
            new PlaceAdjacentGenericRule(),
            new ExtractionRule()
    );

    /** Runs the first matching main-hand air rule; returns that rule or null when none claims it. */
    @Nullable
    public static InteractionRule processEmptyHandRules(InteractionContext ctx) {
        return processFirstMatch(EMPTY_HAND_RULES, ctx);
    }

    /** Runs the first matching main-hand held-item rule; returns the claimed rule or null. */
    @Nullable
    public static InteractionRule processItemRules(InteractionContext ctx) {
        return processFirstMatch(ITEM_RULES, ctx);
    }

    /** Runs the first matching main-hand block rule; returns the claimed rule or null. */
    @Nullable
    public static InteractionRule processBlockRules(InteractionContext ctx) {
        return processFirstMatch(BLOCK_RULES, ctx);
    }

    @Nullable
    private static InteractionRule processFirstMatch(List<InteractionRule> rules, InteractionContext ctx) {
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
