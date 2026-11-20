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
        assertEquals(List.of(ALPHA), realms("alice@lookup.example"));
        assertEquals(List.of(BETA), realms("bob@lookup.example"));
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
    void returnsARealmOnceForTwoUsersWithTheSameEmail() throws Exception {
        assertEquals(List.of(TWINS), realms("twin@lookup.example"));
    }

    @Test
    void mergesAUsernameMatchAndAnEmailMatch() throws Exception {
        assertEquals(List.of(ALPHA, BETA), realms("name-or-mail@lookup.example"));
    }

    @Test
    void ignoresCaseAndOuterSpaces() throws Exception {
        assertEquals(List.of(ALPHA), realms("  Alice@Lookup.Example "));
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
    void checksTheCallerBeforeTheBody() throws Exception {
        assertEquals(401, discovery.post(HOST_REALM, null, "{}").statusCode());
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

    private static void create(RealmRepresentation realm, UserRepresentation... users) {
        realm.setUsers(List.of(users));
        TestRealms.create(realm);
    }
}
