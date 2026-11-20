package net.exoego.keycloak.realmdiscovery;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

import org.jboss.logging.Logger;

/**
 * Options:
 * <pre>
 * --spi-realm-restapi-extension--realm-discovery--host-realm=&lt;realm name&gt;
 * --spi-realm-restapi-extension--realm-discovery--required-role=&lt;realm role name&gt;
 * --spi-realm-restapi-extension--realm-discovery--excluded-realm-ids=&lt;realm id&gt;,&lt;realm id&gt;
 * --spi-realm-restapi-extension--realm-discovery--include-realms-without-email-login=&lt;true or false&gt;
 * </pre>
 */
public class RealmDiscoveryResourceProviderFactory implements RealmResourceProviderFactory {

    /**
     * The provider ID. It is the last part of the path of the endpoint, and the options use it in their names.
     */
    public static final String ID = "realm-discovery";

    private static final Logger logger = Logger.getLogger(RealmDiscoveryResourceProviderFactory.class);

    private Settings settings;

    /**
     * Keycloak creates the factory through {@link java.util.ServiceLoader}.
     */
    public RealmDiscoveryResourceProviderFactory() {
    }

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new RealmDiscoveryResourceProvider(session, settings);
    }

    @Override
    public void init(Config.Scope config) {
        settings = Settings.of(config.get("host-realm"), config.get("required-role"), config.get("excluded-realm-ids"),
                config.getBoolean("include-realms-without-email-login", false));
        if (settings.complete()) {
            logger.infof("%s enabled in realm %s", ID, settings.hostRealm());
        } else {
            logger.warnf("%s rejects every request: set the options host-realm and required-role", ID);
        }
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return ID;
    }
}
