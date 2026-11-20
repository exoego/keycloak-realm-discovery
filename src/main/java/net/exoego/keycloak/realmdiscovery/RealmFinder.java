package net.exoego.keycloak.realmdiscovery;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import jakarta.persistence.EntityManager;

import org.keycloak.models.utils.KeycloakModelUtils;

/**
 * Finds the realms that have an enabled user with a username or an email address.
 */
final class RealmFinder {

    // Two statements, because each one can use its own index. USER_ENTITY has an index on EMAIL,
    // and a unique constraint on (REALM_ID, USERNAME) that the join probes once for each realm.
    private static final List<String> QUERIES = List.of(query("email"), query("username"));

    private final EntityManager entityManager;
    private final Set<String> excludedRealms;

    RealmFinder(EntityManager entityManager, Set<String> excludedRealms) {
        this.entityManager = entityManager;
        this.excludedRealms = excludedRealms;
    }

    /** Returns the realm names in the order of {@link String#compareTo(String)}. */
    List<String> find(String identifier) {
        // Keycloak stores usernames and email addresses in lower case.
        // Its own method is used, because the result depends on the default locale of the JVM.
        String value = KeycloakModelUtils.toLowerCaseSafe(identifier);
        Set<String> realms = new TreeSet<>();
        for (String query : QUERIES) {
            realms.addAll(entityManager.createQuery(query, String.class)
                    .setParameter("identifier", value)
                    .getResultList());
        }
        realms.removeAll(excludedRealms);
        return List.copyOf(realms);
    }

    private static String query(String attribute) {
        return "select distinct r.name from RealmEntity r, UserEntity u"
                + " where u.realmId = r.id and u." + attribute + " = :identifier"
                + " and u.enabled = true and r.enabled = true and u.serviceAccountClientLink is null";
    }
}
