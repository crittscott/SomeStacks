package com.github.crittscott.somestacks.client;

/**
 * A complete, ready-to-render presentation for one item: the output of resolving the
 * override layers, or of measuring the item's model when no layer configures it.
 */
public record RenderProfile(RenderMode mode, float scale, float[] offset) {}
