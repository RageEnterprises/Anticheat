package dev.rage.anticheat.moderation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VpnCheckTest {

    @Test
    void detectsProxy() throws Exception {
        String json = "{\"status\":\"ok\",\"1.2.3.4\":{\"proxy\":\"yes\",\"type\":\"VPN\"}}";
        assertTrue(VpnCheck.parse(json, "1.2.3.4"));
    }

    @Test
    void cleanAddress() throws Exception {
        String json = "{\"status\":\"ok\",\"1.2.3.4\":{\"proxy\":\"no\"}}";
        assertFalse(VpnCheck.parse(json, "1.2.3.4"));
    }

    @Test
    void warningStillHasAResult() throws Exception {
        String json = "{\"status\":\"warning\",\"message\":\"close to limit\",\"1.2.3.4\":{\"proxy\":\"yes\"}}";
        assertTrue(VpnCheck.parse(json, "1.2.3.4"));
    }

    @Test
    void deniedOrBrokenResponsesAreErrors() {
        assertThrows(VpnCheck.LookupException.class,
                () -> VpnCheck.parse("{\"status\":\"denied\",\"message\":\"quota\"}", "1.2.3.4"));
        assertThrows(VpnCheck.LookupException.class, () -> VpnCheck.parse("<html>", "1.2.3.4"));
    }
}
