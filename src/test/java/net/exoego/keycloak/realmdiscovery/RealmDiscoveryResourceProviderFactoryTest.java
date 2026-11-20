package net.exoego.keycloak.realmdiscovery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RealmDiscoveryResourceProviderFactoryTest {

    // Keycloak closes the factory when it stops, after the integration tests have read the coverage.
    @Test
    void closesWithoutState() {
        var factory = new RealmDiscoveryResourceProviderFactory();

        factory.close();

        assertEquals("realm-discovery", factory.getId());
    }
}
