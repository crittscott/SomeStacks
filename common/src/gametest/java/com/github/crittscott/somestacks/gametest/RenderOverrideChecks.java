package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

import com.github.crittscott.somestacks.client.ItemRenderOverrides;
import com.github.crittscott.somestacks.client.renderconfig.ItemRenderConfig;
import com.github.crittscott.somestacks.client.renderconfig.OverrideJsonCodec;
import com.github.crittscott.somestacks.client.renderconfig.RenderMode;
import com.github.crittscott.somestacks.client.renderconfig.RenderOffset;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * The client's own render-override file, {@code config/somestacks/item_overrides.json}, driven
 * through the handlers {@code /ss item} and {@code /ss write changed} call. They need no client
 * singleton, so they run headlessly against the real file, which each check backs up and restores.
 */
public final class RenderOverrideChecks {
    private static final ResourceLocation APPLE = new ResourceLocation("minecraft", "apple");
    private static final ResourceLocation DIRT = new ResourceLocation("minecraft", "dirt");
    private static final ResourceLocation SAND = new ResourceLocation("minecraft", "sand");

    private RenderOverrideChecks() {}

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
     * <p>The handlers' one-time read is reset by reflection before each scenario, standing in for a
     * restart.
     */
    public static void userFileIsNeverOverwrittenWhenUnreadableAndKeepsMalformedEntries(
            GameTestHelper helper) {
        try (UserFileScope scope = new UserFileScope()) {
            String broken = "{\"minecraft:stone\": {\"mode\": \"gui\"},";
            Files.writeString(scope.file, broken);
            scope.resetUserLayer();
            ItemRenderOverrides.putUser(APPLE, gui());
            ItemRenderOverrides.WriteResult refused = ItemRenderOverrides.writeUserFile();
            check(!refused.written(), "Unreadable user file was written");
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
            check(
                    ItemRenderOverrides.writeUserFile().written(),
                    "Fixed user file was not read again and written");
            JsonObject written = readFile(scope.file);
            checkEquals(
                    fixed.get("minecraft:stone"),
                    written.get("minecraft:stone"),
                    "Valid file entry");
            check(written.has(APPLE.toString()), "Session entry was not written");
            for (String key :
                    new String[] {
                        "minecraft:dirt", "minecraft:sand", "Not An Item!", "minecraft:gravel"
                    }) {
                checkEquals(fixed.get(key), written.get(key), "Malformed entry " + key);
            }

            ItemRenderOverrides.removeUser(DIRT);
            ItemRenderOverrides.putUser(SAND, gui());
            check(ItemRenderOverrides.writeUserFile().written(), "Second write failed");
            written = readFile(scope.file);
            check(!written.has(DIRT.toString()), "Reset kept the malformed dirt entry");
            checkEquals(
                    "gui",
                    written.getAsJsonObject(SAND.toString()).get("mode").getAsString(),
                    "Session edit did not replace the malformed sand entry");
            checkEquals(
                    fixed.get("minecraft:gravel"),
                    written.get("minecraft:gravel"),
                    "Untouched malformed entry");
        } catch (IOException | ReflectiveOperationException exception) {
            throw new AssertionError("User override file scenario failed", exception);
        }
        helper.succeed();
    }

    /**
     * To reproduce in-game: in a resource pack's {@code
     * assets/minecraft/item_render_overrides/minecraft.json}, give {@code minecraft:apple} {@code
     * {"mode": "gui", "scale": "big"}} and {@code minecraft:stick} {@code {"mode": "nope", "scale":
     * 2}}, then reload resources. The log warns about each bad field, the apple renders in gui mode
     * at normal size, and the stick renders at double size with its measured mode.
     */
    public static void malformedFieldsKeepTheEntrysValidFields(GameTestHelper helper) {
        JsonObject root =
                JsonParser.parseString(
                                """
                                {
                                  "minecraft:apple": {"mode": "gui", "scale": "big"},
                                  "minecraft:stick": {"mode": "nope", "scale": 2},
                                  "minecraft:stone": {"offset": [0.5, true, 0], "scale": 0.5},
                                  "minecraft:dirt": {"mode": "3d", "offset": [0.25, 0, -0.25]}
                                }
                                """)
                        .getAsJsonObject();
        OverrideJsonCodec.Document document = OverrideJsonCodec.parse(root, "fixture");
        checkEquals(
                new ItemRenderConfig(RenderMode.GUI, null, null),
                document.entries().get(APPLE),
                "Apple keeps its valid mode");
        checkEquals(
                new ItemRenderConfig(null, 2.0f, null),
                document.entries().get(new ResourceLocation("minecraft", "stick")),
                "Stick keeps its valid scale");
        checkEquals(
                new ItemRenderConfig(null, 0.5f, null),
                document.entries().get(new ResourceLocation("minecraft", "stone")),
                "Stone keeps its valid scale");
        checkEquals(
                new ItemRenderConfig(RenderMode.THREE_D, null, new RenderOffset(0.25f, 0, -0.25f)),
                document.entries().get(DIRT),
                "Dirt entry");
        checkEquals(
                Set.of("minecraft:apple", "minecraft:stick", "minecraft:stone"),
                document.malformed().keySet(),
                "Entries reported malformed");
        helper.succeed();
    }

    private static ItemRenderConfig gui() {
        return new ItemRenderConfig(RenderMode.GUI, 1.0f, RenderOffset.ZERO);
    }

    private static JsonObject readFile(Path file) throws IOException {
        JsonElement root = JsonParser.parseString(Files.readString(file));
        return root.getAsJsonObject();
    }

    /**
     * Backs up the user file and restores it on close, leaving the handlers' user layer unread and
     * empty so a later read sees the restored file.
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
            Field state = field("userFileState");
            state.set(null, Enum.valueOf((Class) state.getType(), "UNREAD"));
            ItemRenderOverrides.clearResolvedProfiles();
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
}
