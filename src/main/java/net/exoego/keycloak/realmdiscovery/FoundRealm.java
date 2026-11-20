package net.exoego.keycloak.realmdiscovery;

/**
 * A realm in the answer.
 *
 * @param name the name of the realm in its URLs
 * @param displayName the name of the realm for people, or null. Keycloak leaves a null out of the JSON.
 */
record FoundRealm(String name, String displayName) {
}
