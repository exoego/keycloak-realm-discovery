package net.exoego.keycloak.realmdiscovery;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static net.exoego.keycloak.realmdiscovery.KeycloakEnvironment.HOST_REALM;
import static net.exoego.keycloak.realmdiscovery.KeycloakEnvironment.KEYCLOAK;
import static net.exoego.keycloak.realmdiscovery.TestRealms.CALLER;
import static net.exoego.keycloak.realmdiscovery.TestRealms.HUMAN;
import static net.exoego.keycloak.realmdiscovery.TestRealms.NO_ROLE;
import static net.exoego.keycloak.realmdiscovery.TestRealms.PASSWORD;
import static net.exoego.keycloak.realmdiscovery.TestRealms.SECRET;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthorizationIT {

    private static final String BODY = "{\"identifier\":\"nobody\"}";

    private static Discovery discovery;

    @BeforeAll
    static void createRealms() {
        TestRealms.ready();
        discovery = new Discovery(KEYCLOAK.getAuthServerUrl());
    }

    @Test
    void serviceAccountWithTheRoleIsAccepted() throws Exception {
        String token = discovery.serviceAccountToken(HOST_REALM, CALLER, SECRET);

        HttpResponse<String> response = discovery.post(HOST_REALM, token, BODY);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
    }

    @Test
    void requestWithoutTokenIsRejected() throws Exception {
        assertEquals(401, discovery.post(HOST_REALM, null, BODY).statusCode());
    }

    @Test
    void requestWithBrokenTokenIsRejected() throws Exception {
        String token = discovery.serviceAccountToken(HOST_REALM, CALLER, SECRET);

        assertEquals(401, discovery.post(HOST_REALM, token + "x", BODY).statusCode());
        assertEquals(401, discovery.post(HOST_REALM, "not-a-token", BODY).statusCode());
    }

    @Test
    void serviceAccountWithoutTheRoleIsRejected() throws Exception {
        String token = discovery.serviceAccountToken(HOST_REALM, NO_ROLE, SECRET);

        assertEquals(403, discovery.post(HOST_REALM, token, BODY).statusCode());
    }

    @Test
    void userWithTheRoleIsRejected() throws Exception {
        String token = discovery.passwordToken(HOST_REALM, CALLER, SECRET, HUMAN, PASSWORD);

        assertEquals(403, discovery.post(HOST_REALM, token, BODY).statusCode());
    }

    @Test
    void tokenOfAnotherRealmIsRejected() throws Exception {
        String token = KEYCLOAK.getKeycloakAdminClient().tokenManager().getAccessTokenString();

        assertEquals(401, discovery.post(HOST_REALM, token, BODY).statusCode());
    }

    @Test
    void otherRealmsDoNotAnswer() throws Exception {
        String admin = KEYCLOAK.getKeycloakAdminClient().tokenManager().getAccessTokenString();

        assertEquals(404, discovery.post("master", admin, BODY).statusCode());
        assertEquals(404, discovery.post("master", null, BODY).statusCode());
    }
}
