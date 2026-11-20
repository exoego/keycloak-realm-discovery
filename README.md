# keycloak-realm-discovery
[![Maven Central](https://img.shields.io/maven-central/v/net.exoego/keycloak-realm-discovery)](https://central.sonatype.com/artifact/net.exoego/keycloak-realm-discovery)
[![codecov](https://codecov.io/gh/exoego/keycloak-realm-discovery/graph/badge.svg?token=nfS9Kalc4l)](https://codecov.io/gh/exoego/keycloak-realm-discovery)

A Keycloak extension that finds the realms of a user from a username or an email address.

Keycloak searches users in one realm at a time. A login page that serves many realms must know the realm
before it can send the user to Keycloak. This extension adds one REST endpoint that takes an identifier
and returns the realms that have an enabled user with that username or verified email address.
It returns the name and the display name of each realm, and no user data.

## Compatibility

| keycloak-realm-discovery | Keycloak |
|---|---|
| 0.2.x | 24.0.5, 25.0.6, 26.8 |

The lookup reads the tables of Keycloak with JPA, so it needs no certain database. The tests use PostgreSQL.

The extension uses internal classes of Keycloak, which can change in any release.
The integration tests run the jar in a real Keycloak of each version above to catch such changes.

## Install

Download the jar from [Maven Central](https://repo1.maven.org/maven2/net/exoego/keycloak-realm-discovery/),
put it in `/opt/keycloak/providers/` and run `kc.sh build`.
The jar has no dependencies of its own. For example, in a container image, with the version in `VERSION`:

```dockerfile
FROM quay.io/keycloak/keycloak:26.8 AS builder
ARG VERSION
ADD --chown=keycloak:keycloak https://repo1.maven.org/maven2/net/exoego/keycloak-realm-discovery/${VERSION}/keycloak-realm-discovery-${VERSION}.jar /opt/keycloak/providers/
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.8
COPY --from=builder /opt/keycloak/ /opt/keycloak/
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
```

## Configure

Set the options as environment variables, on the command line, or in `keycloak.conf`.

| Option | Meaning |
|---|---|
| `host-realm` | The only realm where the endpoint answers. Required. |
| `required-role` | A realm role of the host realm. The caller must have it. Required. |
| `excluded-realm-ids` | Ids of realms that are never returned, separated by commas. |
| `include-realms-without-email-login` | `true` lets an email address also find a realm where "Login with email" is off. The default is `false`. |

```sh
KC_SPI_REALM_RESTAPI_EXTENSION__REALM_DISCOVERY__HOST_REALM=login
KC_SPI_REALM_RESTAPI_EXTENSION__REALM_DISCOVERY__REQUIRED_ROLE=realm-finder
KC_SPI_REALM_RESTAPI_EXTENSION__REALM_DISCOVERY__EXCLUDED_REALM_IDS=5f0c3a52-8b1e-4d2a-9c47-2e6d1b0a7f31,9a1d7c3e-4b62-4f0a-8e15-6c2b9d0f3a74
```

On the command line the options are `--spi-realm-restapi-extension--realm-discovery--host-realm` and so on.
Keycloak 26.2 and earlier do not know the double separator. Use a single one there,
such as `KC_SPI_REALM_RESTAPI_EXTENSION_REALM_DISCOVERY_HOST_REALM`.

A realm is excluded by its id, because the id stays when the realm is renamed.
`GET /admin/realms/{name}` returns it in the field `id`.

Without `host-realm` and `required-role` the endpoint rejects every request, and Keycloak logs a warning when it starts.

Then set up the caller in the host realm.

1. Create the realm role that `required-role` names.
2. Create a confidential client with service accounts enabled.
3. Give the role to the service account of the client.

## API

```
POST /realms/{host-realm}/realm-discovery
Authorization: Bearer <access token of the service account>
Content-Type: application/json

{"identifier": "alice@example.com"}
```

```json
{"realms": [{"name": "realm-a", "displayName": "Realm A"}, {"name": "realm-b"}]}
```

- The identifier is a username or an email address. It must match in full. Case and outer spaces do not matter.
- The database decides which texts are equal. One that ignores accents, such as MySQL for a username,
  also finds the realm of the user `andré` for `andre`.
- Keycloak stores at most 255 characters for a username or an email address, so a longer identifier is rejected.
- An identifier with a NUL character inside, or with half of a surrogate pair, is rejected.
  PostgreSQL cannot store such a text.
- A realm is returned when it is enabled and has an enabled user with that username or email address.
- An email address counts only when it is verified. This also holds for a username that is the email address
  of the same user. A username that is not the email address of the user needs no verified email address.
- An email address finds only a realm where "Login with email" is on. A realm that allows duplicate email
  addresses has it off. Set `include-realms-without-email-login` to find such realms too.
- The realms are sorted by the character codes of their names, each one once. So `Zeta` comes before `alpha`.
  No match gives an empty list.
- `name` is the name in the URLs of the realm. `displayName` is the text for people, as set in the realm settings.
  A realm without a display name has no `displayName` field.
- The identifier is in the body, so it does not show up in access logs.

| Status | Reason |
|---|---|
| `200` | The search ran. |
| `400` | The body has no text field `identifier`, or it is blank, or it is longer than 255 characters, or it has a NUL character or half of a surrogate pair. |
| `401` | No valid bearer token of the host realm. |
| `403` | The token is not of a service account, or the service account does not have the role. |
| `404` | The realm is not the host realm, or the options are not set. |

## Performance

Both the email address and the username are found through indexes that Keycloak creates itself.
With 500 realms and 500,000 users a call takes less than 10 ms. See [docs/performance.md](docs/performance.md).

## Security

- The answer tells whether an identifier exists. Give the role only to backends that you trust,
  and do not pass the answer on to an anonymous user without a rate limit.
- The path of the endpoint is public, like every realm resource of Keycloak. The token and the role protect it.
  You can also block the path at your proxy.
- A user that exists only in a user federation, such as LDAP, and was never imported into Keycloak is not found.
- An imported user is found by its copy in the database of Keycloak. The lookup does not ask the user federation
  or an organization. So it can return a realm where Keycloak refuses the login, such as after the user was
  removed from LDAP, or for a managed member of a disabled organization.
- A user can choose a username that is the email address of another person, where the realm allows it.
  The lookup then returns that realm for the address. So an answer does not prove that the person has an account there.
- Service accounts are never returned.

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
