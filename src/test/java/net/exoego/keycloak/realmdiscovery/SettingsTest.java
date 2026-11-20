package net.exoego.keycloak.realmdiscovery;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {

    @Test
    void trimsTheOptions() {
        Settings settings = Settings.of(" host ", " finder ", " a, b ,,a ");

        assertEquals("host", settings.hostRealm());
        assertEquals("finder", settings.requiredRole());
        assertEquals(Set.of("a", "b"), settings.excludedRealms());
    }

    @Test
    void excludesNoRealmByDefault() {
        assertEquals(Set.of(), Settings.of("host", "finder", null).excludedRealms());
        assertEquals(Set.of(), Settings.of("host", "finder", " ").excludedRealms());
    }

    @Test
    void answersOnlyInTheHostRealm() {
        Settings settings = Settings.of("host", "finder", null);

        assertTrue(settings.answersIn("host"));
        assertFalse(settings.answersIn("other"));
    }

    @Test
    void answersNowhereWithoutBothOptions() {
        for (Settings settings : new Settings[] {
                Settings.of(null, "finder", null),
                Settings.of(" ", "finder", null),
                Settings.of("host", null, null),
                Settings.of("host", "", null),
                Settings.of(null, null, null)}) {
            assertFalse(settings.complete());
            assertFalse(settings.answersIn("host"));
            assertFalse(settings.answersIn(null));
        }
        assertNull(Settings.of(" ", null, null).hostRealm());
    }
}
