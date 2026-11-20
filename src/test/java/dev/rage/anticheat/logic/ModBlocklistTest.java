package dev.rage.anticheat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModBlocklistTest {

    private final ModBlocklist list = new ModBlocklist(List.of("Wurst", " baritone ", "freecam", ""));

    @Test
    void matchesWholeWordsIgnoringCase() {
        assertEquals("wurst", list.match("Wurst Client"));
        assertEquals("baritone", list.match("baritone:main"));
        assertNull(list.match("bratwurst"));
        assertNull(list.match("vanilla"));
        assertNull(list.match((String) null));
    }

    @Test
    void checksBrandThenChannels() {
        assertEquals("freecam", list.match("fabric", List.of("fabric:registry/sync", "freecam:state")));
        assertNull(list.match("fabric", List.of("fabric:registry/sync", "minecraft:brand")));
    }

    @Test
    void emptyListMatchesNothing() {
        assertNull(new ModBlocklist(List.of()).match("wurst"));
    }
}
