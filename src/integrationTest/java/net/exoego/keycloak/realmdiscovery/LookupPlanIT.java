package net.exoego.keycloak.realmdiscovery;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static net.exoego.keycloak.realmdiscovery.KeycloakEnvironment.HOST_REALM;
import static net.exoego.keycloak.realmdiscovery.KeycloakEnvironment.KEYCLOAK;
import static net.exoego.keycloak.realmdiscovery.TestRealms.CALLER;
import static net.exoego.keycloak.realmdiscovery.TestRealms.SECRET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that the lookup uses the indexes of Keycloak when there are many realms.
 */
class LookupPlanIT {

    private static final int REALMS = 300;
    // With fewer users the database reads the whole table, which is then the faster plan.
    private static final int USERS = 1000;

    // The statements as Hibernate sends them, without the display name. Its place depends on the Keycloak version.
    // scripts/bench/run.sh prints them from the log of the database.
    private static final String EXPLAIN_BY_EMAIL = """
            explain select re1_0.ID,re1_0.NAME from REALM re1_0,USER_ENTITY ue1_0
            where ue1_0.REALM_ID=re1_0.ID and ue1_0.EMAIL=? and ue1_0.EMAIL_VERIFIED=true
            and re1_0.LOGIN_WITH_EMAIL_ALLOWED=true and ue1_0.ENABLED=true and re1_0.ENABLED=true
            and ue1_0.SERVICE_ACCOUNT_CLIENT_LINK is null""";
    private static final String EXPLAIN_BY_USERNAME = """
            explain select re1_0.ID,re1_0.NAME from REALM re1_0,USER_ENTITY ue1_0
            where ue1_0.REALM_ID=re1_0.ID and ue1_0.USERNAME=?
            and (ue1_0.EMAIL is null or ue1_0.EMAIL<>ue1_0.USERNAME or ue1_0.EMAIL_VERIFIED=true)
            and ue1_0.ENABLED=true and re1_0.ENABLED=true and ue1_0.SERVICE_ACCOUNT_CLIENT_LINK is null""";

    @BeforeAll
    static void fillTheDatabase() throws Exception {
        TestRealms.ready();
        String seed = Files.readString(Path.of(System.getProperty("bench.seed")))
                .replace(":'host'", "'" + HOST_REALM + "'")
                .replace(":realms", String.valueOf(REALMS))
                .replace(":users", String.valueOf(USERS));
        try (Connection connection = KeycloakEnvironment.connection();
                Statement statement = connection.createStatement()) {
            statement.execute(seed);
        }
    }

    // Keycloak cannot serve the realms of the seed, so no other test may see them.
    @AfterAll
    static void emptyTheDatabase() throws Exception {
        try (Connection connection = KeycloakEnvironment.connection();
                Statement statement = connection.createStatement()) {
            statement.execute("delete from user_entity where id like 'bench-%'");
            statement.execute("delete from realm where id like 'bench-realm-%'");
        }
    }

    @Test
    void emailLookupUsesTheEmailIndex() throws Exception {
        String plan = plan(EXPLAIN_BY_EMAIL, "user-150-500@bench.example");

        assertTrue(plan.contains("idx_user_email"), plan);
        assertFalse(plan.contains("Seq Scan on user_entity"), plan);
    }

    @Test
    void usernameLookupUsesTheUsernameIndex() throws Exception {
        String plan = plan(EXPLAIN_BY_USERNAME, "user-150-500");

        assertTrue(plan.contains("uk_ru8tt6t700s9v50bu18ws5ha6"), plan);
        assertFalse(plan.contains("Seq Scan on user_entity"), plan);
    }

    @Test
    void lookupReturnsEveryRealmOfTheSeed() throws Exception {
        Discovery discovery = new Discovery(KEYCLOAK.getAuthServerUrl());
        String token = discovery.serviceAccountToken(HOST_REALM, CALLER, SECRET);

        for (String identifier : new String[] {"everywhere", "everywhere@bench.example"}) {
            HttpResponse<String> response = discovery.post(HOST_REALM, token,
                    "{\"identifier\":\"" + identifier + "\"}");

            assertEquals(200, response.statusCode(), response.body());
            assertEquals(REALMS, Discovery.JSON.readTree(response.body()).get("realms").size());
        }
    }

    private static String plan(String explain, String value) throws Exception {
        StringBuilder plan = new StringBuilder();
        try (Connection connection = KeycloakEnvironment.connection();
                PreparedStatement statement = connection.prepareStatement(explain)) {
            statement.setString(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    plan.append(rows.getString(1)).append('\n');
                }
            }
        }
        return plan.toString();
    }
}
