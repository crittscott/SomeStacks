package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

import com.github.crittscott.somestacks.client.ItemRenderOverrides;
import com.github.crittscott.somestacks.client.RenderProfile;
import com.github.crittscott.somestacks.renderconfig.ItemRenderConfig;
import com.github.crittscott.somestacks.renderconfig.RenderMode;
import com.github.crittscott.somestacks.renderconfig.RenderOffset;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Development-client checks through the real profile resolution and mutation entry points. */
public final class RenderProfileChecks extends ItemRenderOverrides {
    private RenderProfileChecks() {}

    /**
     * To reproduce in-game: give Stone distinct user and resource-pack profiles, edit the user
     * profile, reset it, and reload a changed resource pack. Each change immediately updates the
     * displayed mode, scale, and offset. A user entry containing only a mode uses scale one and
     * zero offset, rather than inheriting the pack's fields. Run /ssclienttest to check these
     * transitions, which write no files and restore all in-memory overrides afterward. It then
     * runs {@link #userFileIsNeverOverwrittenWhenUnreadableAndKeepsMalformedEntries()}, which
     * writes the user file and restores it.
     */
    public static void run() {
        ItemStack stack = new ItemStack(Items.STONE);
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(Items.STONE);
        // Resolving loads the existing user layer before its selected entry is saved for
        // restoration.
        ItemRenderOverrides.resolve(stack);
        ItemRenderConfig originalUser = userEntry(id);
        Map<ResourceLocation, ItemRenderConfig> originalResources = new HashMap<>(CONFIG_MAP);
        RenderProfileChecks listener = new RenderProfileChecks();
        ItemRenderConfig resource =
                new ItemRenderConfig(RenderMode.GUI, 0.8f, new RenderOffset(0.1f, 0.2f, 0.3f));
        ItemRenderConfig user =
                new ItemRenderConfig(RenderMode.TWO_D, 0.6f, new RenderOffset(-0.1f, -0.2f, -0.3f));
        try {
            Map<ResourceLocation, ItemRenderConfig> resources = new HashMap<>(originalResources);
            resources.put(id, resource);
            listener.applyResources(resources);
            putUser(id, user);
            assertProfile(stack, user, "User precedence");

            ItemRenderConfig changed =
                    new ItemRenderConfig(
                            RenderMode.THREE_D, 0.9f, new RenderOffset(0.2f, 0.0f, -0.2f));
            putUser(id, changed);
            assertProfile(stack, changed, "User edit invalidation");

            putUser(id, new ItemRenderConfig(RenderMode.BLOCK, null, null));
            assertProfile(
                    stack,
                    new ItemRenderConfig(RenderMode.BLOCK, 1.0f, RenderOffset.ZERO),
                    "Partial user entry owns the complete profile");

            removeUser(id);
            assertProfile(stack, resource, "User removal invalidation");

            ItemRenderConfig replacement =
                    new ItemRenderConfig(
                            RenderMode.THREE_D, 0.4f, new RenderOffset(0.0f, 0.1f, 0.0f));
            resources.put(id, replacement);
            listener.applyResources(resources);
            assertProfile(stack, replacement, "Resource replacement invalidation");
        } finally {
            listener.applyResources(originalResources);
            if (originalUser == null) removeUser(id);
            else putUser(id, originalUser);
        }
        userFileIsNeverOverwrittenWhenUnreadableAndKeepsMalformedEntries();
    }

    /**
     * To reproduce in-game: quit, put a syntax error in {@code
     * config/somestacks/item_overrides.json} (delete its last closing brace), start the game, and
     * run {@code /ss item minecraft:apple gui} and then {@code /ss write changed}. The command
     * fails, saying the file could not be read, and the file is unchanged. Restore the brace, add
     * {@code "minecraft:dirt": {"mode": "flat"}}, and run {@code /ss write changed} again: it
     * succeeds, the file gains the apple entry, and it keeps the dirt entry exactly as written
     * along with every other entry. Then run {@code /ss item minecraft:dirt reset} and {@code /ss
     * write changed}: the dirt entry is gone.
     *
     * <p>The real file is backed up and restored, and the one-time read is reset by reflection
     * before the scenario, standing in for a restart. Afterward the user layer is read again from
     * the restored file, so unsaved {@code /ss item} changes of the session running the check are
     * dropped.
     */
    private static void userFileIsNeverOverwrittenWhenUnreadableAndKeepsMalformedEntries() {
        ResourceLocation apple = BuiltInRegistries.ITEM.getKey(Items.APPLE);
        ResourceLocation dirt = BuiltInRegistries.ITEM.getKey(Items.DIRT);
        ResourceLocation sand = BuiltInRegistries.ITEM.getKey(Items.SAND);
        ItemRenderConfig gui = new ItemRenderConfig(RenderMode.GUI, 1.0f, RenderOffset.ZERO);
        try (UserFileScope scope = new UserFileScope()) {
            String broken = "{\"minecraft:stone\": {\"mode\": \"gui\"},";
            Files.writeString(scope.file, broken);
            scope.resetUserLayer();
            putUser(apple, gui);
            check(!writeUserFile().written(), "Unreadable user file was written");
            checkEquals(broken, Files.readString(scope.file), "Unreadable user file changed");

            JsonObject fixed =
                    JsonParser.parseString(
                                    """
                                    {
                                      "minecraft:stone": {"mode": "gui"},
                                      "minecraft:dirt": {"mode": "flat"},
                                      "minecraft:sand": {"mode": "3d", "scale": "big"},
                                      "Not An Item!": {"mode": "gui"},
                                      "minecraft:gravel": 5
                                    }
                                    """)
                            .getAsJsonObject();
            Files.writeString(scope.file, fixed.toString());
            check(writeUserFile().written(), "Fixed user file was not read again and written");
            JsonObject written = readFile(scope.file);
            checkEquals(
                    fixed.get("minecraft:stone"), written.get("minecraft:stone"), "Valid entry");
            check(written.has(apple.toString()), "Session entry was not written");
            for (String key :
                    List.of(
                            "minecraft:dirt", "minecraft:sand", "Not An Item!", "minecraft:gravel")) {
                checkEquals(fixed.get(key), written.get(key), "Malformed entry " + key);
            }

            removeUser(dirt);
            putUser(sand, gui);
            check(writeUserFile().written(), "Second write failed");
            written = readFile(scope.file);
            check(!written.has(dirt.toString()), "Reset kept the malformed dirt entry");
            checkEquals(
                    "gui",
                    written.getAsJsonObject(sand.toString()).get("mode").getAsString(),
                    "Session edit did not replace the malformed sand entry");
            checkEquals(
                    fixed.get("minecraft:gravel"),
                    written.get("minecraft:gravel"),
                    "Untouched malformed entry");
        } catch (IOException | ReflectiveOperationException exception) {
            throw new IllegalStateException("User override file scenario failed", exception);
        }
    }

    private static JsonObject readFile(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    /**
     * Backs up the user file and restores it on close, leaving the user layer unread and empty so
     * the next resolution reads the restored file.
     */
    private static final class UserFileScope implements AutoCloseable {
        private final Path file;
        private final byte[] backup;

        private UserFileScope() throws IOException, ReflectiveOperationException {
            file = (Path) field("USER_FILE").get(null);
            backup = Files.exists(file) ? Files.readAllBytes(file) : null;
            Files.createDirectories(file.getParent());
        }

        private static Field field(String name) throws NoSuchFieldException {
            Field field = ItemRenderOverrides.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void resetUserLayer() throws ReflectiveOperationException {
            ((Map<?, ?>) field("USER_OVERRIDES").get(null)).clear();
            ((Map<?, ?>) field("USER_SET_ASIDE").get(null)).clear();
            ((Set<?>) field("SESSION_EDITS").get(null)).clear();
            Field state = field("userFileState");
            state.set(null, Enum.valueOf((Class) state.getType(), "UNREAD"));
            Method clear = ItemRenderOverrides.class.getDeclaredMethod("clearResolvedProfiles");
            clear.setAccessible(true);
            clear.invoke(null);
        }

        @Override
        public void close() throws IOException, ReflectiveOperationException {
            try {
                if (backup == null) {
                    Files.deleteIfExists(file);
                } else {
                    Files.write(file, backup);
                }
            } finally {
                resetUserLayer();
            }
        }
    }

    private void applyResources(Map<ResourceLocation, ItemRenderConfig> resources) {
        apply(resources, Minecraft.getInstance().getResourceManager(), InactiveProfiler.INSTANCE);
    }

    private static void assertProfile(ItemStack stack, ItemRenderConfig expected, String message) {
        RenderProfile actual = resolve(stack);
        checkEquals(
                new RenderProfile(expected.mode(), expected.scale(), expected.offset()),
                actual,
                message);
    }

    /**
     * Reads only the restoration value; production mutation still goes through putUser/removeUser.
     */
    @SuppressWarnings("unchecked")
    private static ItemRenderConfig userEntry(ResourceLocation id) {
        try {
            Field field = ItemRenderOverrides.class.getDeclaredField("USER_OVERRIDES");
            field.setAccessible(true);
            return ((Map<ResourceLocation, ItemRenderConfig>) field.get(null)).get(id);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not preserve the user render override", failure);
        }
    }
}
