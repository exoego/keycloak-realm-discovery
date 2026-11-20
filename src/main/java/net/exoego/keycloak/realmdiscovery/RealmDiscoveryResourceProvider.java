package net.exoego.keycloak.realmdiscovery;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.managers.AppAuthManager;
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
        // Keycloak calls this before the body is read, so a caller that is not allowed learns nothing from a bad body.
        RealmModel realm = session.getContext().getRealm();
        if (!settings.answersIn(realm.getName())) {
            throw new NotFoundException();
        }
        requireCaller(realm);
        return new RealmDiscoveryResource(session, settings);
    }

    private void requireCaller(RealmModel realm) {
        String bearer = AppAuthManager.extractAuthorizationHeaderToken(session.getContext().getHttpRequest().getHttpHeaders());
        var authenticated = new AppAuthManager.BearerTokenAuthenticator(session)
                .setRealm(realm)
                .setTokenString(bearer)
                .authenticate();
        if (authenticated == null) {
            throw new NotAuthorizedException("Bearer");
        }
        // The "sub" claim is not always the user id, so the user comes from the authenticator.
        // Keycloak 26.5 deprecated getUser() for user(), which Keycloak 24 and 25 do not have.
        @SuppressWarnings("removal")
        UserModel user = authenticated.getUser();
        if (user.getServiceAccountClientLink() == null) {
            throw new ForbiddenException();
        }
        RoleModel role = realm.getRole(settings.requiredRole());
        if (role == null || !user.hasRole(role)) {
            throw new ForbiddenException();
        }
    }

    @Override
    public void close() {
    }
}
