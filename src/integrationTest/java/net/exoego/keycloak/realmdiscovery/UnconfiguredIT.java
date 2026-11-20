package net.exoego.keycloak.realmdiscovery;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnconfiguredIT {

    @Test
    void everyRequestIsRejectedWithoutTheOptions() throws Exception {
        try (KeycloakContainer keycloak = KeycloakEnvironment.withProvider()) {
            keycloak.start();
            try {
                Discovery discovery = new Discovery(keycloak.getAuthServerUrl());
                String admin = keycloak.getKeycloakAdminClient().tokenManager().getAccessTokenString();

                assertEquals(404, discovery.post("master", admin, "{\"identifier\":\"admin\"}").statusCode());
                assertEquals(404, discovery.post("master", null, "{\"identifier\":\"admin\"}").statusCode());
                assertTrue(keycloak.getLogs().contains("realm-discovery rejects every request"));
            } finally {
                Coverage.save(keycloak);
            }
        }
    }
}
