package net.exoego.keycloak.realmdiscovery;

import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.core.Response;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.RolesRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import static net.exoego.keycloak.realmdiscovery.KeycloakEnvironment.HOST_REALM;
import static net.exoego.keycloak.realmdiscovery.KeycloakEnvironment.KEYCLOAK;
import static net.exoego.keycloak.realmdiscovery.KeycloakEnvironment.REQUIRED_ROLE;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The realms that the integration tests share.
 */
final class TestRealms {

    static final String SECRET = "test-secret";
    static final String PASSWORD = "test-password";
    /** A client of the host realm whose service account has the required role. */
    static final String CALLER = "caller";
    /** A client of the host realm whose service account has no role. */
    static final String NO_ROLE = "no-role";
    /** A user of the host realm with a password and the required role. */
    static final String HUMAN = "human";

    static {
        RealmRepresentation host = realm(HOST_REALM);
        RolesRepresentation roles = new RolesRepresentation();
        roles.setRealm(List.of(new RoleRepresentation(REQUIRED_ROLE, null, false)));
        host.setRoles(roles);
        host.setClients(List.of(client(CALLER), client(NO_ROLE)));

        UserRepresentation account = new UserRepresentation();
        account.setUsername("service-account-" + CALLER);
        account.setEnabled(true);
        account.setServiceAccountClientId(CALLER);
        account.setRealmRoles(List.of(REQUIRED_ROLE));
        UserRepresentation human = user(HUMAN, HUMAN + "@host.example");
        human.setRealmRoles(List.of(REQUIRED_ROLE));
        host.setUsers(new ArrayList<>(List.of(account, human)));
        create(host);
    }

    private TestRealms() {
    }

    /** Makes sure that the host realm exists. */
    static void ready() {
    }

    static RealmRepresentation realm(String name) {
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(name);
        realm.setEnabled(true);
        return realm;
    }

    static UserRepresentation user(String username, String email) {
        CredentialRepresentation password = new CredentialRepresentation();
        password.setType(CredentialRepresentation.PASSWORD);
        password.setValue(PASSWORD);

        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEnabled(true);
        user.setCredentials(List.of(password));
        return user;
    }

    static void create(RealmRepresentation realm) {
        KEYCLOAK.getKeycloakAdminClient().realms().create(realm);
    }

    static void createUser(String realm, UserRepresentation user) {
        try (Response response = KEYCLOAK.getKeycloakAdminClient().realm(realm).users().create(user)) {
            assertEquals(201, response.getStatus());
        }
    }

    private static ClientRepresentation client(String clientId) {
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(clientId);
        client.setPublicClient(false);
        client.setSecret(SECRET);
        client.setServiceAccountsEnabled(true);
        client.setDirectAccessGrantsEnabled(true);
        client.setStandardFlowEnabled(false);
        return client;
    }
}
