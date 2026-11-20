package net.exoego.keycloak.realmdiscovery;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The options of the provider.
 *
 * @param hostRealm the only realm where the endpoint answers, or null
 * @param requiredRole the realm role of the host realm that a caller must have, or null
 * @param excludedRealms the realms that are never returned
 * @param includeRealmsWithoutEmailLogin whether an email address also finds a realm where it cannot log in
 */
record Settings(String hostRealm, String requiredRole, Set<String> excludedRealms,
        boolean includeRealmsWithoutEmailLogin) {

    static Settings of(String hostRealm, String requiredRole, String excludedRealms,
            boolean includeRealmsWithoutEmailLogin) {
        Set<String> excluded = Arrays.stream((excludedRealms == null ? "" : excludedRealms).split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        return new Settings(blankToNull(hostRealm), blankToNull(requiredRole), excluded,
                includeRealmsWithoutEmailLogin);
    }

    // Without both options nobody may call the endpoint.
    boolean complete() {
        return hostRealm != null && requiredRole != null;
    }

    boolean answersIn(String realmName) {
        return complete() && hostRealm.equals(realmName);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
