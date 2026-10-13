package com.github.crittscott.somestacks;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServerConfigSettingsTest {
    @Test
    void settingsCopyListsAndReadersCannotEditThem() {
        List<String> mods = new ArrayList<>(List.of("example"));
        ServerConfig.Settings settings = new ServerConfig.Settings(8, true, true, true,
                mods, List.of(), List.of("forge:ingots*", "somestacks:ingots"),
                64, false, 3, List.of(), List.of());
        mods.clear();
        assertEquals(List.of("example"), settings.disableMods());
        assertThrows(UnsupportedOperationException.class, () -> settings.disableMods().add("other"));
        assertThrows(UnsupportedOperationException.class, () -> settings.ingotTags().clear());
    }
}
