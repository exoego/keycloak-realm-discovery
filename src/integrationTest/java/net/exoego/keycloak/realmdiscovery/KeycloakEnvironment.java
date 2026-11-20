package net.exoego.keycloak.realmdiscovery;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One Keycloak with the provider jar and one PostgreSQL, shared by the integration tests of a run.
 * Testcontainers removes the containers when the JVM exits.
 */
final class KeycloakEnvironment {

    static final String HOST_REALM = "host";
    static final String REQUIRED_ROLE = "realm-finder";
    static final String EXCLUDED_REALM = "excluded";

    private static final String VERSION = System.getProperty("keycloak.version");
    private static final int MAJOR = Integer.parseInt(VERSION.split("\\.", -1)[0]);
    private static final int MINOR = Integer.parseInt(VERSION.split("\\.", -1)[1]);

    private static final Network NETWORK = Network.newNetwork();

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18")
            .withNetwork(NETWORK)
            .withNetworkAliases("postgres")
            .withDatabaseName("keycloak")
            .withUsername("keycloak")
            .withPassword("keycloak");

    static final KeycloakContainer KEYCLOAK = withProvider()
            .withNetwork(NETWORK)
            .withEnv("KC_DB", "postgres")
            .withEnv("KC_DB_URL", "jdbc:postgresql://postgres:5432/keycloak")
            .withEnv("KC_DB_USERNAME", "keycloak")
            .withEnv("KC_DB_PASSWORD", "keycloak")
            .withEnv(option("HOST_REALM"), HOST_REALM)
            .withEnv(option("REQUIRED_ROLE"), REQUIRED_ROLE)
            .withEnv(option("EXCLUDED_REALMS"), EXCLUDED_REALM + ", no-such-realm");

    static {
        POSTGRES.start();
        KEYCLOAK.start();
        // Testcontainers kills the container after the JVM exits, so the data is read before that.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> Coverage.save(KEYCLOAK)));
    }

    private KeycloakEnvironment() {
    }

    /** Creates a Keycloak container with the provider jar and without its options. */
    static KeycloakContainer withProvider() {
        KeycloakContainer keycloak = Coverage.keycloak("quay.io/keycloak/keycloak:" + VERSION)
                .withProviderLibsFrom(List.of(new File(System.getProperty("provider.jar"))));
        // Testcontainers sets only the bootstrap admin variables of Keycloak 26.
        if (MAJOR < 26) {
            keycloak.withEnv("KEYCLOAK_ADMIN", keycloak.getAdminUsername())
                    .withEnv("KEYCLOAK_ADMIN_PASSWORD", keycloak.getAdminPassword());
        }
        // Keycloak 25 moved the health endpoints to the management port 9000, where Testcontainers waits.
        if (MAJOR < 25) {
            keycloak.waitingFor(Wait.forHttp("/health/ready").forPort(8080));
        }
        return keycloak;
    }

    // Keycloak 26.3 added the "__" separator. Older versions read only the single "_" form.
    static String option(String name) {
        boolean doubleSeparator = MAJOR > 26 || (MAJOR == 26 && MINOR >= 3);
        return (doubleSeparator
                ? "KC_SPI_REALM_RESTAPI_EXTENSION__REALM_DISCOVERY__"
                : "KC_SPI_REALM_RESTAPI_EXTENSION_REALM_DISCOVERY_") + name;
    }

    /** Opens a connection to the database of Keycloak. */
    static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
