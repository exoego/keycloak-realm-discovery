package net.exoego.keycloak.realmdiscovery;

import java.net.http.HttpResponse;
import java.util.List;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;

import static net.exoego.keycloak.realmdiscovery.KeycloakEnvironment.option;
import static net.exoego.keycloak.realmdiscovery.TestRealms.user;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs a second Keycloak, for the cases that need other options than the shared one has.
 */
class OtherOptionsIT {

    private static final String HOST = "master";
    private static final String ROLE = "realm-finder";
    private static final String CLIENT = "caller";
    private static final String SECRET = "test-secret";
    private static final String BODY = "{\"identifier\":\"twin@option.example\"}";

    private static KeycloakContainer keycloak;
    private static Discovery discovery;

    @BeforeAll
    static void start() {
        keycloak = KeycloakEnvironment.withProvider()
                .withEnv(option("HOST_REALM"), HOST)
                .withEnv(option("REQUIRED_ROLE"), ROLE)
                .withEnv(option("INCLUDE_REALMS_WITHOUT_EMAIL_LOGIN"), "true");
        keycloak.start();
        discovery = new Discovery(keycloak.getAuthServerUrl());

        RealmResource host = keycloak.getKeycloakAdminClient().realm(HOST);
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(CLIENT);
        client.setPublicClient(false);
        client.setSecret(SECRET);
        client.setServiceAccountsEnabled(true);
        host.clients().create(client).close();
        createRoleOfTheCaller(host);
    }

    @AfterAll
    static void stop() {
        try {
            Coverage.save(keycloak);
        } finally {
            keycloak.close();
        }
    }

    @Test
    void anEmailFindsARealmWithoutEmailLogin() throws Exception {
        RealmRepresentation twins = TestRealms.realm("twins");
        // Keycloak turns the login with an email address off for such a realm.
        twins.setDuplicateEmailsAllowed(true);
        twins.setUsers(List.of(
                user("twin-one", "twin@option.example"),
                user("twin-two", "twin@option.example")));
        keycloak.getKeycloakAdminClient().realms().create(twins);

        HttpResponse<String> response = discovery.post(HOST, token(), BODY);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals("twins", Discovery.JSON.readTree(response.body()).get("realms").get(0).get("name").asText());
    }

    @Test
    void aRequiredRoleThatDoesNotExistRejectsTheCaller() throws Exception {
        RealmResource host = keycloak.getKeycloakAdminClient().realm(HOST);
        host.roles().deleteRole(ROLE);
        try {
            assertEquals(403, discovery.post(HOST, token(), BODY).statusCode());
        } finally {
            createRoleOfTheCaller(host);
        }
        assertEquals(200, discovery.post(HOST, token(), BODY).statusCode());
    }

    private static String token() throws Exception {
        return discovery.serviceAccountToken(HOST, CLIENT, SECRET);
    }

    private static void createRoleOfTheCaller(RealmResource host) {
        host.roles().create(new RoleRepresentation(ROLE, null, false));
        String id = host.clients().findByClientId(CLIENT).get(0).getId();
        String account = host.clients().get(id).getServiceAccountUser().getId();
        host.users().get(account).roles().realmLevel().add(List.of(host.roles().get(ROLE).toRepresentation()));
    }
}
