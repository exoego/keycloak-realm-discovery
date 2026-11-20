package net.exoego.keycloak.realmdiscovery;

import java.net.http.HttpResponse;
import java.util.List;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;

import static net.exoego.keycloak.realmdiscovery.KeycloakEnvironment.option;
import static net.exoego.keycloak.realmdiscovery.TestRealms.user;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailLoginOptionIT {

    private static final String ROLE = "realm-finder";
    private static final String CLIENT = "caller";
    private static final String SECRET = "test-secret";

    @Test
    void anEmailFindsARealmWithoutEmailLoginWhenTheOptionIsSet() throws Exception {
        try (KeycloakContainer keycloak = KeycloakEnvironment.withProvider()
                .withEnv(option("HOST_REALM"), "master")
                .withEnv(option("REQUIRED_ROLE"), ROLE)
                .withEnv(option("INCLUDE_REALMS_WITHOUT_EMAIL_LOGIN"), "true")) {
            keycloak.start();
            try {
                createCaller(keycloak.getKeycloakAdminClient().realm("master"));
                RealmRepresentation twins = TestRealms.realm("twins");
                // Keycloak turns the login with an email address off for such a realm.
                twins.setDuplicateEmailsAllowed(true);
                twins.setUsers(List.of(
                        user("twin-one", "twin@option.example"),
                        user("twin-two", "twin@option.example")));
                keycloak.getKeycloakAdminClient().realms().create(twins);

                Discovery discovery = new Discovery(keycloak.getAuthServerUrl());
                String token = discovery.serviceAccountToken("master", CLIENT, SECRET);
                HttpResponse<String> response = discovery.post("master", token,
                        "{\"identifier\":\"twin@option.example\"}");

                assertEquals(200, response.statusCode(), response.body());
                assertEquals("{\"realms\":[\"twins\"]}", response.body());
            } finally {
                Coverage.save(keycloak);
            }
        }
    }

    private static void createCaller(RealmResource master) {
        master.roles().create(new RoleRepresentation(ROLE, null, false));
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(CLIENT);
        client.setPublicClient(false);
        client.setSecret(SECRET);
        client.setServiceAccountsEnabled(true);
        master.clients().create(client).close();
        String id = master.clients().findByClientId(CLIENT).get(0).getId();
        String account = master.clients().get(id).getServiceAccountUser().getId();
        master.users().get(account).roles().realmLevel().add(List.of(master.roles().get(ROLE).toRepresentation()));
    }
}
