package net.exoego.keycloak.realmdiscovery;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Calls the endpoint of the extension and the token endpoint of one Keycloak.
 */
final class Discovery {

    static final ObjectMapper JSON = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final String serverUrl;

    Discovery(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    HttpResponse<String> post(String realm, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(serverUrl + "/realms/" + realm + "/realm-discovery"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    String serviceAccountToken(String realm, String clientId, String secret) throws Exception {
        return token(realm, Map.of(
                "grant_type", "client_credentials",
                "client_id", clientId,
                "client_secret", secret));
    }

    String passwordToken(String realm, String clientId, String secret, String username, String password)
            throws Exception {
        return token(realm, Map.of(
                "grant_type", "password",
                "client_id", clientId,
                "client_secret", secret,
                "username", username,
                "password", password));
    }

    private String token(String realm, Map<String, String> form) throws Exception {
        String body = form.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(serverUrl + "/realms/" + realm + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return JSON.readTree(response.body()).get("access_token").asText();
    }
}
