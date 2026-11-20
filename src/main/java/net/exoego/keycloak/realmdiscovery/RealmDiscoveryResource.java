package net.exoego.keycloak.realmdiscovery;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.representations.AccessToken;
import org.keycloak.services.managers.AppAuthManager;

/**
 * The REST resource at {@code /realms/{realm}/realm-discovery}.
 */
public class RealmDiscoveryResource {

    // The username and email columns of Keycloak hold 255 characters, so a longer identifier matches no user.
    private static final int MAX_LENGTH = 255;

    private final KeycloakSession session;
    private final Settings settings;

    RealmDiscoveryResource(KeycloakSession session, Settings settings) {
        this.session = session;
        this.settings = settings;
    }

    /**
     * Returns the names of the realms that have an enabled user with the username or the email address.
     *
     * @param body a JSON object with the text field {@code identifier}
     * @return the response with the realm names
     */
    @POST
    @Path("")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response find(JsonNode body) {
        RealmModel realm = session.getContext().getRealm();
        if (!settings.answersIn(realm.getName())) {
            throw new NotFoundException();
        }
        requireCaller(realm);
        JsonNode field = body == null ? null : body.get("identifier");
        String identifier = field != null && field.isTextual() ? field.asText().trim() : "";
        if (identifier.isEmpty()) {
            throw new BadRequestException("identifier is required");
        }
        if (identifier.codePointCount(0, identifier.length()) > MAX_LENGTH) {
            throw new BadRequestException("identifier is too long");
        }
        var entityManager = session.getProvider(JpaConnectionProvider.class).getEntityManager();
        var realms = new RealmFinder(entityManager, settings.excludedRealms()).find(identifier);
        return Response.ok(Map.of("realms", realms)).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
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
        // The type of the result above changed in Keycloak 26.6, so the user is read from the token.
        // The token is valid here, because the authenticator accepted it.
        AccessToken token = session.tokens().decode(bearer, AccessToken.class);
        UserModel user = session.users().getUserById(realm, token.getSubject());
        RoleModel role = realm.getRole(settings.requiredRole());
        if (user.getServiceAccountClientLink() == null || role == null || !user.hasRole(role)) {
            throw new ForbiddenException();
        }
    }
}
