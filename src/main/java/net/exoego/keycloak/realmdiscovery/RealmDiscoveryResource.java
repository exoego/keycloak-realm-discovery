package net.exoego.keycloak.realmdiscovery;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;

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
     * Returns the names and the display names of the realms that have an enabled user with the username or the email address.
     *
     * @param body a JSON object with the text field {@code identifier}
     * @return the response with the realms
     */
    @POST
    @Path("")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response find(JsonNode body) {
        JsonNode field = body == null ? null : body.get("identifier");
        String identifier = field != null && field.isTextual() ? field.asText().trim() : "";
        if (identifier.isEmpty()) {
            throw new BadRequestException("identifier is required");
        }
        if (identifier.codePointCount(0, identifier.length()) > MAX_LENGTH) {
            throw new BadRequestException("identifier is too long");
        }
        // PostgreSQL fails a statement that has a NUL character.
        // Its driver sends half of a surrogate pair as "?", which can match another user.
        if (identifier.codePoints().anyMatch(c -> c == 0 || Character.getType(c) == Character.SURROGATE)) {
            throw new BadRequestException("identifier has an invalid character");
        }
        var entityManager = session.getProvider(JpaConnectionProvider.class).getEntityManager();
        var realms = new RealmFinder(entityManager, settings).find(identifier);
        return Response.ok(Map.of("realms", realms)).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }
}
