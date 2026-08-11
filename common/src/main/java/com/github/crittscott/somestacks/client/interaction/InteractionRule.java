package com.github.crittscott.somestacks.client.interaction;

/**
 * One loader-neutral player gesture. {@link InteractionRuleRegistry} walks an ordered list and runs
 * the first rule that matches, so a rule states only its own conditions and relies on its position
 * in that list to settle overlaps with broader rules behind it.
 *
 * <p>Rules run on the client, which owns gesture recognition. A rule that acts sends a packet and
 * leaves the world untouched; the server decides whether the gesture is allowed.
 */
public interface InteractionRule {
    /**
     * Whether this rule claims the gesture. Called on each rule in turn until one returns true, so
     * it reads state and changes none.
     */
    boolean matches(InteractionContext ctx);

    /**
     * Acts on the claimed gesture, normally by sending a packet and calling
     * {@link InteractionContext#cancelEvent()} to take the click away from the vanilla interaction.
     * A matched rule ends the walk whether or not it cancels.
     */
    void execute(InteractionContext ctx);
}
