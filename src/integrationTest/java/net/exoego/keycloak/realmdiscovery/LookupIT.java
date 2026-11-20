package net.exoego.keycloak.realmdiscovery;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import static net.exoego.keycloak.realmdiscovery.KeycloakEnvironment.EXCLUDED_REALM;
import static net.exoego.keycloak.realmdiscovery.KeycloakEnvironment.HOST_REALM;
import static net.exoego.keycloak.realmdiscovery.KeycloakEnvironment.KEYCLOAK;
import static net.exoego.keycloak.realmdiscovery.TestRealms.CALLER;
import static net.exoego.keycloak.realmdiscovery.TestRealms.HUMAN;
import static net.exoego.keycloak.realmdiscovery.TestRealms.SECRET;
import static net.exoego.keycloak.realmdiscovery.TestRealms.user;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LookupIT {

    private static final String ALPHA = "lookup-alpha";
    private static final String BETA = "lookup-beta";
    private static final String TWINS = "lookup-twins";
    private static final String SLEEPING = "lookup-sleeping";
    private static final String NAMES_ONLY = "lookup-names-only";

    private static Discovery discovery;
    private static String token;

    @BeforeAll
    static void createRealms() throws Exception {
        TestRealms.ready();

        UserRepresentation dana = user("dana", "dana@lookup.example");
        dana.setEnabled(false);
        create(TestRealms.realm(ALPHA),
                user("alice", "alice@lookup.example"),
                user("carol", "carol@alpha.example"),
                user("shared-in-alpha", "shared@lookup.example"),
                user("erin", "name-or-mail@lookup.example"),
                unverified("grace", "grace@lookup.example"),
                unverified("heidi@lookup.example", "heidi@lookup.example"),
                unverified("ivan@lookup.example", "other@lookup.example"),
                dana);
        create(TestRealms.realm(BETA),
                user("bob", "bob@lookup.example"),
                user("carol", "carol@beta.example"),
                user("shared-in-beta", "shared@lookup.example"),
                user("name-or-mail@lookup.example", "frank@lookup.example"));

        RealmRepresentation twins = TestRealms.realm(TWINS);
        twins.setDuplicateEmailsAllowed(true);
        create(twins,
                user("twin-one", "twin@lookup.example"),
                user("twin-two", "twin@lookup.example"));

        RealmRepresentation namesOnly = TestRealms.realm(NAMES_ONLY);
        namesOnly.setLoginWithEmailAllowed(false);
        create(namesOnly,
                user("judy", "judy@lookup.example"),
                user("alice@lookup.example", "kim@lookup.example"));

        RealmRepresentation sleeping = TestRealms.realm(SLEEPING);
        sleeping.setEnabled(false);
        create(sleeping, user("carol", "shared@lookup.example"));

        create(TestRealms.realm(EXCLUDED_REALM), user("carol", "shared@lookup.example"));

        discovery = new Discovery(KEYCLOAK.getAuthServerUrl());
        token = discovery.serviceAccountToken(HOST_REALM, CALLER, SECRET);
    }

    @Test
    void findsTheRealmOfAUsername() throws Exception {
        assertEquals(List.of(ALPHA), realms("alice"));
        assertEquals(List.of(BETA), realms("bob"));
    }

    @Test
    void findsTheRealmOfAnEmail() throws Exception {
        assertEquals(List.of(ALPHA, NAMES_ONLY), realms("alice@lookup.example"));
        assertEquals(List.of(BETA), realms("bob@lookup.example"));
    }

    @Test
    void skipsAnEmailThatIsNotVerified() throws Exception {
        assertEquals(List.of(), realms("grace@lookup.example"));
        assertEquals(List.of(ALPHA), realms("grace"));
        assertEquals(List.of(), realms("other@lookup.example"));
    }

    @Test
    void skipsAUsernameThatIsTheUnverifiedEmailOfTheUser() throws Exception {
        assertEquals(List.of(), realms("heidi@lookup.example"));
        assertEquals(List.of(ALPHA), realms("ivan@lookup.example"));
    }

    @Test
    void skipsAnEmailInARealmWithoutEmailLogin() throws Exception {
        assertEquals(List.of(), realms("judy@lookup.example"));
        assertEquals(List.of(NAMES_ONLY), realms("judy"));
        assertEquals(List.of(), realms("twin@lookup.example"));
        assertEquals(List.of(TWINS), realms("twin-one"));
    }

    @Test
    void returnsNoRealmForAnUnknownIdentifier() throws Exception {
        assertEquals(List.of(), realms("nobody"));
        assertEquals(List.of(), realms("nobody@lookup.example"));
    }

    @Test
    void matchesTheWholeIdentifierOnly() throws Exception {
        assertEquals(List.of(), realms("alic"));
        assertEquals(List.of(), realms("alice@lookup"));
        assertEquals(List.of(), realms("%"));
        assertEquals(List.of(), realms("alice%"));
    }

    @Test
    void returnsEveryRealmOfASharedEmail() throws Exception {
        assertEquals(List.of(ALPHA, BETA), realms("shared@lookup.example"));
    }

    @Test
    void returnsEveryRealmOfASharedUsername() throws Exception {
        assertEquals(List.of(ALPHA, BETA), realms("carol"));
    }

    @Test
    void mergesAUsernameMatchAndAnEmailMatch() throws Exception {
        assertEquals(List.of(ALPHA, BETA), realms("name-or-mail@lookup.example"));
    }

    @Test
    void ignoresCaseAndOuterSpaces() throws Exception {
        assertEquals(List.of(ALPHA, NAMES_ONLY), realms("  Alice@Lookup.Example "));
        assertEquals(List.of(ALPHA), realms("ALICE"));
    }

    @Test
    void skipsADisabledUser() throws Exception {
        assertEquals(List.of(), realms("dana"));
        assertEquals(List.of(), realms("dana@lookup.example"));
    }

    @Test
    void skipsAServiceAccount() throws Exception {
        assertEquals(List.of(), realms("service-account-" + CALLER));
    }

    @Test
    void findsAUserOfTheHostRealm() throws Exception {
        assertEquals(List.of(HOST_REALM), realms(HUMAN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"identifier\":\"\"}", "{\"identifier\":\"  \"}", "{\"identifier\":5}",
            "{\"identifier\":null}", "{\"identifier\":[\"alice\"]}", "[]", "\"alice\"", "null", "{", ""})
    void rejectsABodyWithoutAnIdentifier(String body) throws Exception {
        HttpResponse<String> response = discovery.post(HOST_REALM, token, body);

        assertEquals(400, response.statusCode(), response.body());
    }

    @Test
    void rejectsAnIdentifierLongerThanTheColumns() throws Exception {
        assertEquals(List.of(), realms("a".repeat(255)));
        assertEquals(List.of(), realms("  " + "a".repeat(255) + "  "));
        // One character outside the basic plane is two chars in Java and one character in the database.
        assertEquals(List.of(), realms("\uD83D\uDE00".repeat(255)));

        for (String identifier : List.of("a".repeat(256), "\uD83D\uDE00".repeat(256))) {
            String body = Discovery.JSON.writeValueAsString(java.util.Map.of("identifier", identifier));
            assertEquals(400, discovery.post(HOST_REALM, token, body).statusCode());
        }
    }

    // The bodies are written by hand. A Java string with half of a pair would leave the HTTP client as "?".
    @ParameterizedTest
    @ValueSource(strings = {"a\\u0000b", "who\\ud83d@lookup.example", "who\\ude00@lookup.example",
            "who\\ude00\\ud83d@lookup.example"})
    void rejectsANulCharacterAndHalfOfASurrogatePair(String escaped) throws Exception {
        HttpResponse<String> response = discovery.post(HOST_REALM, token, "{\"identifier\":\"" + escaped + "\"}");

        assertEquals(400, response.statusCode(), response.body());
    }

    @Test
    void checksTheCallerBeforeTheBody() throws Exception {
        assertEquals(401, discovery.post(HOST_REALM, null, "{}").statusCode());
        assertEquals(401, discovery.post(HOST_REALM, null, "{").statusCode());
        assertEquals(404, discovery.post("master", null, "{").statusCode());
    }

    private static List<String> realms(String identifier) throws Exception {
        String body = Discovery.JSON.writeValueAsString(java.util.Map.of("identifier", identifier));
        HttpResponse<String> response = discovery.post(HOST_REALM, token, body);
        assertEquals(200, response.statusCode(), response.body());
        JsonNode json = Discovery.JSON.readTree(response.body());
        assertEquals(1, json.size(), response.body());
        List<String> realms = new ArrayList<>();
        json.get("realms").forEach(name -> realms.add(name.asText()));
        return realms;
    }

    private static UserRepresentation unverified(String username, String email) {
        UserRepresentation user = user(username, email);
        user.setEmailVerified(false);
        return user;
    }

    private static void create(RealmRepresentation realm, UserRepresentation... users) {
        realm.setUsers(List.of(users));
        TestRealms.create(realm);
    }
}
