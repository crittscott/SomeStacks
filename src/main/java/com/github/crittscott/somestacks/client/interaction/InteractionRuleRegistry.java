package com.github.crittscott.somestacks.client.interaction;

import java.util.List;

/**
 * The ordered rule lists behind each right-click event, one list per kind of click.
 *
 * <p>Order is the precedence rule: the first match wins and the walk stops. The block list runs
 * from the most specific gesture to the least, so the torch gestures precede deposit, deposit into
 * the clicked stack precedes deposit into its neighbour, generic placement catches the modified
 * clicks none of those claimed, and unmodified extraction sits last.
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
            new DepositIntoClickedStackRule(),
            new DepositIntoAdjacentStackRule(),
            new PlaceAdjacentGenericRule(),
            new ExtractionRule()
    );

    public static void processEmptyHandRules(InteractionContext ctx) {
        processFirstMatch(EMPTY_HAND_RULES, ctx);
    }

    public static void processItemRules(InteractionContext ctx) {
        processFirstMatch(ITEM_RULES, ctx);
    }

    public static void processBlockRules(InteractionContext ctx) {
        processFirstMatch(BLOCK_RULES, ctx);
    }

    private static void processFirstMatch(List<InteractionRule> rules, InteractionContext ctx) {
        for (InteractionRule rule : rules) {
            if (rule.matches(ctx)) {
                rule.execute(ctx);
                break;
            }
        }
    }
}
