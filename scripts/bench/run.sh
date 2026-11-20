#!/usr/bin/env bash
# Fills Keycloak with many realms and measures the lookup.
# It needs Docker, curl, and jq. Run it from the root of the project. It is not a check of CI.
set -euo pipefail

KEYCLOAK_VERSION="${KEYCLOAK_VERSION:-26.8.2}"
POSTGRES_VERSION="${POSTGRES_VERSION:-18}"
PORT="${PORT:-18090}"
BASE="http://127.0.0.1:${PORT}"
HOST=bench-host
ROLE=realm-finder
# The defaults are the size of the run in docs/performance.md.
REALMS="${REALMS:-500}"
USERS="${USERS:-1000}"

# A caller that built the jar already can name it in JAR, as a path from the root of the project.
if [ -z "${JAR:-}" ]; then
  ./gradlew jar -q
  JAR="$(ls build/libs/keycloak-realm-discovery-*-SNAPSHOT.jar | head -1)"
fi

docker rm -f realm-discovery-bench-kc realm-discovery-bench-pg >/dev/null 2>&1 || true
docker network create realm-discovery-bench >/dev/null 2>&1 || true
# The database writes the plan of every statement to its log.
docker run -d --name realm-discovery-bench-pg --network realm-discovery-bench \
  -e POSTGRES_DB=keycloak -e POSTGRES_USER=keycloak -e POSTGRES_PASSWORD=keycloak "postgres:${POSTGRES_VERSION}" \
  -c shared_preload_libraries=auto_explain -c auto_explain.log_min_duration=0 \
  -c auto_explain.log_analyze=on >/dev/null
docker run -d --name realm-discovery-bench-kc --network realm-discovery-bench -p "127.0.0.1:${PORT}:8080" \
  -v "${PWD}/${JAR}:/opt/keycloak/providers/realm-discovery.jar:ro" \
  -e KC_DB=postgres -e KC_DB_URL=jdbc:postgresql://realm-discovery-bench-pg:5432/keycloak \
  -e KC_DB_USERNAME=keycloak -e KC_DB_PASSWORD=keycloak \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
  "quay.io/keycloak/keycloak:${KEYCLOAK_VERSION}" start-dev \
  "--spi-realm-restapi-extension--realm-discovery--host-realm=${HOST}" \
  "--spi-realm-restapi-extension--realm-discovery--required-role=${ROLE}" >/dev/null
curl -s -o /dev/null --retry 120 --retry-delay 2 --retry-all-errors --max-time 5 "${BASE}/realms/master"

ADMIN="$(curl -s -d grant_type=password -d client_id=admin-cli -d username=admin -d password=admin \
  "${BASE}/realms/master/protocol/openid-connect/token" | jq -r .access_token)"
curl -s -o /dev/null -X POST -H "Authorization: Bearer ${ADMIN}" -H 'Content-Type: application/json' \
  "${BASE}/admin/realms" -d @- <<JSON
{
  "realm": "${HOST}",
  "enabled": true,
  "roles": {"realm": [{"name": "${ROLE}"}]},
  "clients": [{"clientId": "bench", "secret": "bench", "publicClient": false, "serviceAccountsEnabled": true}],
  "users": [{"username": "service-account-bench", "enabled": true, "serviceAccountClientId": "bench", "realmRoles": ["${ROLE}"]}]
}
JSON

docker exec -i realm-discovery-bench-pg psql -q -U keycloak -d keycloak -v ON_ERROR_STOP=1 \
  -v host="${HOST}" -v realms="${REALMS}" -v users="${USERS}" < scripts/bench/seed.sql

TOKEN="$(curl -s -d grant_type=client_credentials -d client_id=bench -d client_secret=bench \
  "${BASE}/realms/${HOST}/protocol/openid-connect/token" | jq -r .access_token)"

lookup() {
  curl -s -o /tmp/realm-discovery-bench-body -w '%{time_total} %{http_code}' -X POST \
    -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' \
    -d "{\"identifier\": \"$1\"}" "${BASE}/realms/${HOST}/realm-discovery"
}

# Prints the median of 21 times in milliseconds, the number of realms in the answer, and the status.
measure() {
  local name="$1" identifier="$2" line
  lookup "${identifier}" >/dev/null
  local times=()
  for _ in $(seq 21); do
    line="$(lookup "${identifier}")"
    times+=("$(awk '{printf "%.1f", $1 * 1000}' <<<"${line}")")
  done
  printf '| %s | `%s` | %s | %s | %s |\n' "${name}" "${identifier}" \
    "$(printf '%s\n' "${times[@]}" | sort -n | sed -n 11p)" \
    "$(jq '.realms | length' /tmp/realm-discovery-bench-body)" "$(awk '{print $2}' <<<"${line}")"
}

echo "${REALMS} realms with ${USERS} users each, Keycloak ${KEYCLOAK_VERSION}, PostgreSQL ${POSTGRES_VERSION}"
echo
echo '| Lookup | Identifier | Median of 21 in ms | Realms | Status |'
echo '|---|---|---|---|---|'
measure 'Email in one realm' "user-$((REALMS / 2))-$((USERS / 2))@bench.example"
measure 'Username in one realm' "user-$((REALMS / 2))-$((USERS / 2))"
measure 'Email in every realm' 'everywhere@bench.example'
measure 'Username in every realm' 'everywhere'
measure 'Unknown email' 'nobody@bench.example'
measure 'Unknown username' 'nobody'

echo
echo 'The plans of the two statements of the last lookup:'
docker logs realm-discovery-bench-pg 2>&1 | awk '/duration: .* plan:/ {block = ""; keep = 0}
  {block = block $0 "\n"} /user_entity/ {keep = 1}
  /^[0-9-]+ [0-9:.]+ UTC \[[0-9]+\] LOG:  duration/ {if (prev_keep) plans[++count] = prev; }
  {prev = block; prev_keep = keep}
  END {for (i = count - 1; i <= count; i++) if (i > 0) printf "%s\n", plans[i]}'
echo 'Remove the containers with: docker rm -f realm-discovery-bench-kc realm-discovery-bench-pg'
