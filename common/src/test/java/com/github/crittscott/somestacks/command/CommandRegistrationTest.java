package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.client.ClientRenderCommands;
import com.github.crittscott.somestacks.renderconfig.OverrideJsonCodec;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommandRegistrationTest {
    @Test
    void registersTheServerCommandSurface() {
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();
        CommandNode<CommandSourceStack> ss = child(dispatcher.getRoot(), "ss");

        assertEquals(Set.of("gallery", "ingotgallery", "reload", "gen", "deny", "help"),
                ss.getChildren().stream()
                        .map(CommandNode::getName)
                        .collect(Collectors.toUnmodifiableSet()));
    }

    @Test
    void registersTheClientCommandSurface() {
        CommandNode<Object> ss = child(clientDispatcher().getRoot(), "ss");

        assertEquals(Set.of("item", "write"),
                ss.getChildren().stream()
                        .map(CommandNode::getName)
                        .collect(Collectors.toUnmodifiableSet()));
        CommandNode<Object> write = child(ss, "write");
        for (String form : new String[] {"changed", "all", "list", "modid"}) {
            assertNotNull(child(write, form).getCommand(), form + " endpoint");
        }
    }

    @Test
    void itemOverrideTreeHasEveryDocumentedEndpoint() {
        CommandNode<Object> item = path(clientDispatcher().getRoot(), "ss", "item", "item");
        CommandNode<Object> mode = child(item, "mode");
        CommandNode<Object> scale = child(mode, "scale");
        CommandNode<Object> x = child(scale, "x");
        CommandNode<Object> y = child(x, "y");
        CommandNode<Object> z = child(y, "z");

        assertNotNull(child(item, "reset").getCommand(), "reset endpoint");
        assertNotNull(mode.getCommand(), "mode endpoint");
        assertNotNull(scale.getCommand(), "scale endpoint");
        assertNotNull(y.getCommand(), "x/y endpoint");
        assertNotNull(z.getCommand(), "x/y/z endpoint");
    }

    @Test
    void commandNumericBoundsMatchTheOverrideCodec() {
        CommandNode<Object> scale = path(
                clientDispatcher().getRoot(), "ss", "item", "item", "mode", "scale");
        CommandNode<Object> x = child(scale, "x");
        CommandNode<Object> y = child(x, "y");
        CommandNode<Object> z = child(y, "z");

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

    private static CommandDispatcher<Object> clientDispatcher() {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        ClientRenderCommands.register(dispatcher, new ClientRenderCommands.Feedback<>() {
            @Override
            public void success(Object source, Component message) {}

            @Override
            public void failure(Object source, Component message) {}
        });
        return dispatcher;
    }

    private static <S> CommandNode<S> path(CommandNode<S> start, String... names) {
        CommandNode<S> node = start;
        for (String name : names) {
            node = child(node, name);
        }
        return node;
    }

    private static <S> CommandNode<S> child(CommandNode<S> parent, String name) {
        CommandNode<S> child = parent.getChild(name);
        assertNotNull(child, () -> "Missing command node " + name + " under " + parent.getName());
        return child;
    }

    private static void assertBounds(CommandNode<?> node, float minimum, float maximum) {
        ArgumentCommandNode<?, ?> argument = assertInstanceOf(
                ArgumentCommandNode.class, node, node.getName());
        FloatArgumentType type = assertInstanceOf(
                FloatArgumentType.class, argument.getType(), node.getName());
        assertEquals(minimum, type.getMinimum());
        assertEquals(maximum, type.getMaximum());
    }
}
