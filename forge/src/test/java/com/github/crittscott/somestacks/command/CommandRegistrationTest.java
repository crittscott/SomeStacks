package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.util.OverrideJsonCodec;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommandRegistrationTest {
    @Test
    void registersThePublicCommandSurface() {
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();
        CommandNode<CommandSourceStack> ss = child(dispatcher.getRoot(), "ss");

        assertEquals(Set.of(
                        "item", "gallery", "ingotgallery", "write", "reload",
                        "gen", "deny", "ingot", "help"),
                ss.getChildren().stream()
                        .map(CommandNode::getName)
                        .collect(Collectors.toUnmodifiableSet()));
    }

    @Test
    void itemOverrideTreeHasEveryDocumentedEndpoint() {
        CommandNode<CommandSourceStack> item = path(
                dispatcher().getRoot(), "ss", "item", "item");
        CommandNode<CommandSourceStack> mode = child(item, "mode");
        CommandNode<CommandSourceStack> scale = child(mode, "scale");
        CommandNode<CommandSourceStack> x = child(scale, "x");
        CommandNode<CommandSourceStack> y = child(x, "y");
        CommandNode<CommandSourceStack> z = child(y, "z");

        assertNotNull(child(item, "reset").getCommand(), "reset endpoint");
        assertNotNull(mode.getCommand(), "mode endpoint");
        assertNotNull(scale.getCommand(), "scale endpoint");
        assertNotNull(y.getCommand(), "x/y endpoint");
        assertNotNull(z.getCommand(), "x/y/z endpoint");
    }

    @Test
    void commandNumericBoundsMatchTheOverrideCodec() {
        CommandNode<CommandSourceStack> scale = path(
                dispatcher().getRoot(), "ss", "item", "item", "mode", "scale");
        CommandNode<CommandSourceStack> x = child(scale, "x");
        CommandNode<CommandSourceStack> y = child(x, "y");
        CommandNode<CommandSourceStack> z = child(y, "z");

        assertBounds(scale, OverrideJsonCodec.MIN_SCALE, OverrideJsonCodec.MAX_SCALE);
        assertBounds(x, OverrideJsonCodec.MIN_OFFSET, OverrideJsonCodec.MAX_OFFSET);
        assertBounds(y, OverrideJsonCodec.MIN_OFFSET, OverrideJsonCodec.MAX_OFFSET);
        assertBounds(z, OverrideJsonCodec.MIN_OFFSET, OverrideJsonCodec.MAX_OFFSET);
    }

    @Test
    void renderGalleryKindsExposeStableTranslationKeys() {
        assertEquals("somestacks.command.gallery.storage_stacks",
                RenderGalleryGenerator.Kind.STORAGE.stackLabelKey());
        assertEquals("somestacks.command.gallery.items",
                RenderGalleryGenerator.Kind.STORAGE.itemLabelKey());
        assertEquals("somestacks.command.gallery.bar_stacks",
                RenderGalleryGenerator.Kind.BAR.stackLabelKey());
        assertEquals("somestacks.command.gallery.ingots",
                RenderGalleryGenerator.Kind.BAR.itemLabelKey());
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        SsCommand.register(dispatcher);
        return dispatcher;
    }

    private static CommandNode<CommandSourceStack> path(
            CommandNode<CommandSourceStack> start, String... names) {
        CommandNode<CommandSourceStack> node = start;
        for (String name : names) {
            node = child(node, name);
        }
        return node;
    }

    private static CommandNode<CommandSourceStack> child(
            CommandNode<CommandSourceStack> parent, String name) {
        CommandNode<CommandSourceStack> child = parent.getChild(name);
        assertNotNull(child, () -> "Missing command node " + name + " under " + parent.getName());
        return child;
    }

    private static void assertBounds(
            CommandNode<CommandSourceStack> node, float minimum, float maximum) {
        ArgumentCommandNode<?, ?> argument = assertInstanceOf(
                ArgumentCommandNode.class, node, node.getName());
        FloatArgumentType type = assertInstanceOf(
                FloatArgumentType.class, argument.getType(), node.getName());
        assertEquals(minimum, type.getMinimum());
        assertEquals(maximum, type.getMaximum());
    }
}
