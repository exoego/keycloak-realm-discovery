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
        Settings settings = Settings.of(" host ", " finder ", " a, b ,,a ", false);

        assertEquals("host", settings.hostRealm());
        assertEquals("finder", settings.requiredRole());
        assertEquals(Set.of("a", "b"), settings.excludedRealmIds());
    }

    @Test
    void excludesNoRealmByDefault() {
        assertEquals(Set.of(), Settings.of("host", "finder", null, false).excludedRealmIds());
        assertEquals(Set.of(), Settings.of("host", "finder", " ", false).excludedRealmIds());
    }

    @Test
    void keepsTheEmailLoginOption() {
        assertFalse(Settings.of("host", "finder", null, false).includeRealmsWithoutEmailLogin());
        assertTrue(Settings.of("host", "finder", null, true).includeRealmsWithoutEmailLogin());
    }

    @Test
    void answersOnlyInTheHostRealm() {
        Settings settings = Settings.of("host", "finder", null, false);

        assertTrue(settings.answersIn("host"));
        assertFalse(settings.answersIn("other"));
    }

    @Test
    void answersNowhereWithoutBothOptions() {
        for (Settings settings : new Settings[] {
                Settings.of(null, "finder", null, false),
                Settings.of(" ", "finder", null, false),
                Settings.of("host", null, null, false),
                Settings.of("host", "", null, false),
                Settings.of(null, null, null, false)}) {
            assertFalse(settings.complete());
            assertFalse(settings.answersIn("host"));
            assertFalse(settings.answersIn(null));
        }
        assertNull(Settings.of(" ", null, null, false).hostRealm());
    }
}
