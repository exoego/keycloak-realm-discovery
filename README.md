# keycloak-realm-discovery

A Keycloak extension that finds the realms of a user from a username or an email address.

Keycloak searches users in one realm at a time. A login page that serves many realms must know the realm
before it can send the user to Keycloak. This extension adds one REST endpoint that takes an identifier
and returns the names of the realms that have an enabled user with that username or email address.
It returns realm names only, and no user data.

## Status

Under development. There is no release yet.

## Build

```sh
./gradlew build
```

The build needs JDK 21.

## Test

```sh
./gradlew check
```

The integration tests start Keycloak and PostgreSQL in containers, so they need Docker.
To test another Keycloak version, pass `-PtestKeycloakVersion=25.0.6`.

## Release

Publish a GitHub release with a tag like `v1.0.0`.
The `Publish to Maven Central` workflow takes the version from the tag, runs the tests, and publishes the jar.

The workflow needs a `release` environment in the repository settings with these secrets.

- `SONATYPE_USERNAME` and `SONATYPE_PASSWORD`
- `GPG_PRIVATE_KEY` and `GPG_PASSPHRASE`

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[Apache License 2.0](LICENSE)
