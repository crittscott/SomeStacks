package com.github.crittscott.somestacks.client.interaction;

public interface InteractionRule {
    boolean matches(InteractionContext ctx);
    void execute(InteractionContext ctx);

    default String getName() {
        return getClass().getSimpleName();
    }
}
