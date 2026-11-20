# Performance

The lookup runs two statements over all realms, one for the email address and one for the username.
Both use indexes that Keycloak creates itself, so the extension adds nothing to the database.

| Statement | Index of `USER_ENTITY` | How it is used |
|---|---|---|
| Email | `IDX_USER_EMAIL (EMAIL)` | One search. |
| Username | `UK_RU8TT6T700S9V50BU18WS5HA6 (REALM_ID, USERNAME)` | One search for each realm. |

The username has no index of its own. The statement joins `REALM`, so the database can search the
unique index once for each realm. PostgreSQL 18 does the same with a skip scan of the index.

## Measurement

`scripts/bench/run.sh` starts Keycloak and PostgreSQL in containers, fills the tables with realms and users,
and calls the endpoint. It needs Docker, curl, and jq.

```sh
REALMS=500 USERS=1000 scripts/bench/run.sh
POSTGRES_VERSION=15 scripts/bench/run.sh
```

A run on a laptop with 500 realms and 1000 users in each realm, 500,000 users in total, on Keycloak 26.8.2.
The times are for the whole HTTP call, with the check of the token.

| Lookup | Realms in the answer | PostgreSQL 18, median in ms | PostgreSQL 15, median in ms |
|---|---|---|---|
| Email in one realm | 1 | 6.7 | 6.9 |
| Username in one realm | 1 | 7.0 | 6.7 |
| Email in every realm | 500 | 9.2 | 8.6 |
| Username in every realm | 500 | 8.1 | 7.2 |
| Unknown email | 0 | 6.3 | 5.9 |
| Unknown username | 0 | 6.3 | 6.2 |

In the database, the username statement took about 2 ms and the email statement less than 0.1 ms.
The rest of the time is the HTTP call and the check of the token.

The username statement grows with the number of realms, at about 3 microseconds for each realm in this run.

## Check in the tests

`LookupPlanIT` fills the database of the integration tests with 300 realms and reads the plans of both statements.
It fails when a statement reads the whole `USER_ENTITY` table.

With few users the database reads the whole table on purpose, because that is then the faster plan.
