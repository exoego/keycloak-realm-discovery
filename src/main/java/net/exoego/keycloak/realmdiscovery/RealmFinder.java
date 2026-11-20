package net.exoego.keycloak.realmdiscovery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jakarta.persistence.EntityManager;

import org.keycloak.models.jpa.entities.RealmEntity;
import org.keycloak.models.utils.KeycloakModelUtils;

/**
 * Finds the realms that have an enabled user with a username or an email address.
 */
final class RealmFinder {

    // Newer Keycloak versions keep the display name in a column of the realm, and older ones in a realm attribute.
    // A map and not a condition, because one Keycloak runs only one of the two and the build needs every branch tested.
    private static final String DISPLAY_NAME = Map.of(
            true, "r.displayName",
            false, "(select a.value from RealmAttributeEntity a where a.realm = r and a.name = 'displayName')")
            .get(Arrays.stream(RealmEntity.class.getDeclaredFields())
                    .anyMatch(field -> field.getName().equals("displayName")));

    // Anyone who can create a user can store the email address of another person, so only a verified one counts.
    private static final String BY_EMAIL = "u.email = :identifier and u.emailVerified = true";
    // A username that is the email address of the user is such an address too.
    private static final String BY_USERNAME = "u.username = :identifier"
            + " and (u.email is null or u.email <> u.username or u.emailVerified = true)";

    // Two statements, because each one can use its own index. USER_ENTITY has an index on EMAIL,
    // and a unique constraint on (REALM_ID, USERNAME) that the join probes once for each realm.
    private static final List<String> WHERE_THE_IDENTIFIER_LOGS_IN =
            List.of(query(BY_EMAIL + " and r.loginWithEmailAllowed = true"), query(BY_USERNAME));
    private static final List<String> WHERE_THE_USER_EXISTS = List.of(query(BY_EMAIL), query(BY_USERNAME));

    private final EntityManager entityManager;
    private final Settings settings;

    RealmFinder(EntityManager entityManager, Settings settings) {
        this.entityManager = entityManager;
        this.settings = settings;
    }

    /**
     * Returns one map for each realm, with the key {@code name}, and {@code displayName} when the realm has one.
     * The realms are in the order of {@link String#compareTo(String)} of their names.
     */
    List<Map<String, String>> find(String identifier) {
        // Keycloak stores usernames and email addresses in lower case.
        // Its own method is used, because the result depends on the default locale of the JVM.
        String value = KeycloakModelUtils.toLowerCaseSafe(identifier);
        Map<String, Map<String, String>> realms = new TreeMap<>();
        for (String query : settings.includeRealmsWithoutEmailLogin()
                ? WHERE_THE_USER_EXISTS : WHERE_THE_IDENTIFIER_LOGS_IN) {
            for (Object[] realm : entityManager.createQuery(query, Object[].class)
                    .setParameter("identifier", value)
                    .getResultList()) {
                if (!settings.excludedRealmIds().contains((String) realm[0])) {
                    Map<String, String> found = new LinkedHashMap<>();
                    found.put("name", (String) realm[1]);
                    // Keycloak leaves a null out of the JSON.
                    found.put("displayName", (String) realm[2]);
                    realms.put((String) realm[1], found);
                }
            }
        }
        return new ArrayList<>(realms.values());
    }

    private static String query(String match) {
        // No "distinct", because some databases cannot compare the large text of an attribute.
        return "select r.id, r.name, " + DISPLAY_NAME + " from RealmEntity r, UserEntity u"
                + " where u.realmId = r.id and " + match
                + " and u.enabled = true and r.enabled = true and u.serviceAccountClientLink is null";
    }
}
