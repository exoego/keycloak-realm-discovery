-- Fills Keycloak with many realms and users, for the measurement of the lookup.
-- It writes to tables of Keycloak itself. The realms are rows for the lookup only, and Keycloak cannot serve them.
-- psql sets :host to the name of an existing realm, :realms to the number of realms, and :users to the
-- number of users in each realm.

-- Each realm is a copy of the row of the host realm, so the statement fits every Keycloak version.
INSERT INTO realm
SELECT (jsonb_populate_record(NULL::realm,
        to_jsonb(r) || jsonb_build_object('id', 'bench-realm-' || n, 'name', 'bench-realm-' || n))).*
FROM realm r, generate_series(1, :realms) n
WHERE r.name = :'host';

INSERT INTO user_entity (id, email, email_constraint, email_verified, enabled, realm_id, username, created_timestamp, not_before)
SELECT 'bench-' || n || '-' || i, 'user-' || n || '-' || i || '@bench.example', 'user-' || n || '-' || i || '@bench.example',
       true, true, 'bench-realm-' || n, 'user-' || n || '-' || i, 0, 0
FROM generate_series(1, :realms) n, generate_series(1, :users) i;

-- One person with an account in every realm. This is the largest answer.
INSERT INTO user_entity (id, email, email_constraint, email_verified, enabled, realm_id, username, created_timestamp, not_before)
SELECT 'bench-' || n || '-everywhere', 'everywhere@bench.example', 'everywhere@bench.example',
       true, true, 'bench-realm-' || n, 'everywhere', 0, 0
FROM generate_series(1, :realms) n;

ANALYZE;
