package com.github.crittscott.somestacks.client.interaction;

import java.util.List;

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
