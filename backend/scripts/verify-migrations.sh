#!/bin/bash
# Verifies the Flyway migrations of every service against SCRATCH MySQL databases (tvl_verify_*), never
# the real ones: for each service it (1) migrates an empty database (V1+V2) and (2) loads the pre-Flyway
# schema (V1 content) with sample rows and lets Flyway baseline + upgrade it, then starts the real service
# jar so Hibernate `validate` must accept the result. Build first: mvn -DskipTests package.
# Usage: VERIFY_DB_USERNAME=... VERIFY_DB_PASSWORD=... scripts/verify-migrations.sh [workdir]
set -u
B=$(cd "$(dirname "$0")/.." && pwd)
J=${JAVA:-java}
S=${1:-$(mktemp -d)}
DB_USER=${VERIFY_DB_USERNAME:?set VERIFY_DB_USERNAME}
DB_PASS=${VERIFY_DB_PASSWORD:?set VERIFY_DB_PASSWORD}
mkdir -p $S/storage
MY="mysql -u$DB_USER -p$DB_PASS -N"
run_service() { # svc jar db label
  local svc=$1 jar=$2 db=$3 label=$4
  local log=$S/$svc-$label.log
  env -i PATH=/usr/bin:/bin HOME=$HOME \
    TRAVELEASE_JWT_SECRET=verify-jwt-secret-0123456789-abcdefghijklmn \
    TRAVELEASE_INTERNAL_SECRET=verify-internal-secret-0123456789-abcdefgh \
    TRAVELEASE_DB_USERNAME=$DB_USER TRAVELEASE_DB_PASSWORD=$DB_PASS TRAVELEASE_STORAGE_DIR=$S/storage \
    SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/$db?createDatabaseIfNotExist=false&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
    SERVER_PORT=0 TRAVELEASE_OUTBOX_DISPATCH_ENABLED=false \
    $J -jar $B/$svc-service/target/$jar > $log 2>&1 &
  local pid=$!
  for i in $(seq 1 90); do
    if grep -q 'Started .*Application' $log; then kill $pid; wait $pid 2>/dev/null; echo "OK"; return; fi
    if ! kill -0 $pid 2>/dev/null; then echo "FAILED"; return; fi
    sleep 1
  done
  kill $pid; echo "TIMEOUT"
}
for spec in admin:travelease_admin travel-request:travelease_travelrequest expense:travelease_expence audit:travelease_audit notification:travelease_notification; do
  svc=${spec%%:*}; jar=$svc-service-1.0.0-SNAPSHOT.jar
  key=$(echo $svc | tr - _)
  fresh=tvl_verify_${key}_fresh; upg=tvl_verify_${key}_upgrade
  $MY -e "DROP DATABASE IF EXISTS $fresh; DROP DATABASE IF EXISTS $upg; CREATE DATABASE $fresh; CREATE DATABASE $upg;" 2>/dev/null
  # upgrade path: today's ddl-auto schema (V1 content) without Flyway history, plus a sample row
  $MY $upg < $B/$svc-service/src/main/resources/db/migration/V1__baseline_schema.sql 2>&1 | grep -v 'Using a password'
  case $svc in
    audit) $MY $upg -e "INSERT INTO audit_record (action,actor_id,after_status,before_status,entity_id,entity_type,occurred_at) VALUES ('APPROVED',2,'APPROVED','PENDING',7,'TRAVEL_REQUEST',NOW(6));" 2>/dev/null;;
    travel-request) $MY $upg -e "INSERT INTO travel_request (created_at,created_by,deleted,updated_at,updated_by,version,approver_id,destination,employee_id,end_date,justification,start_date,status) VALUES (NOW(6),'v',0,NOW(6),'v',0,2,'Pune',1,'2027-01-12','x','2027-01-10','PENDING');" 2>/dev/null;;
  esac
  echo "$svc fresh:   $(run_service $svc $jar $fresh fresh)  history=$($MY -e "SELECT GROUP_CONCAT(version ORDER BY installed_rank) FROM $fresh.flyway_schema_history" 2>/dev/null)"
  echo "$svc upgrade: $(run_service $svc $jar $upg upgrade)  history=$($MY -e "SELECT GROUP_CONCAT(CONCAT(version,':',type) ORDER BY installed_rank) FROM $upg.flyway_schema_history" 2>/dev/null)"
done
# clean up the scratch databases (only tvl_verify_* are ever created or dropped)
for d in $($MY -e "SHOW DATABASES LIKE 'tvl\_verify\_%'" 2>/dev/null); do $MY -e "DROP DATABASE \`$d\`" 2>/dev/null; done
