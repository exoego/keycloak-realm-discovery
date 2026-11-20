package net.exoego.keycloak.realmdiscovery;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import jakarta.persistence.EntityManager;

/**
 * Finds the realms that have an enabled user with a username or an email address.
 */
final class RealmFinder {

    // Two statements, because each one can use its own index. USER_ENTITY has an index on EMAIL,
    // and a unique constraint on (REALM_ID, USERNAME) that the join probes once for each realm.
    private static final String BY_EMAIL = query("email");
    private static final String BY_USERNAME = query("username");

    private final EntityManager entityManager;
    private final Set<String> excludedRealms;

    RealmFinder(EntityManager entityManager, Set<String> excludedRealms) {
        this.entityManager = entityManager;
        this.excludedRealms = excludedRealms;
    }

    /** Returns the realm names in alphabetical order. */
    List<String> find(String identifier) {
        // Keycloak stores usernames and email addresses in lower case.
        String value = identifier.trim().toLowerCase(Locale.ROOT);
        Set<String> realms = new TreeSet<>();
        for (String query : List.of(BY_EMAIL, BY_USERNAME)) {
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
