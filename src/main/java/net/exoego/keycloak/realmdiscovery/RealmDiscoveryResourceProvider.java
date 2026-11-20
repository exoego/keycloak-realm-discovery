package net.exoego.keycloak.realmdiscovery;

import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

final class RealmDiscoveryResourceProvider implements RealmResourceProvider {

    private final KeycloakSession session;
    private final Settings settings;

    RealmDiscoveryResourceProvider(KeycloakSession session, Settings settings) {
        this.session = session;
        this.settings = settings;
    }

    @Override
    public Object getResource() {
        return new RealmDiscoveryResource(session, settings);
    }

    @Override
    public void close() {
    }
}
