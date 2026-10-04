#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
REQUESTS="${REQUESTS:-100}"
SEAT="${SEAT:-A12}"
USERNAME="${BURST_USERNAME:-burst_user_$(date +%s)}"
PASSWORD="${BURST_PASSWORD:-password123}"

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

log() { echo "$@" >&2; }

json_field() {
  local json="$1"
  local field="$2"
  python3 -c "import json,sys; print(json.load(sys.stdin)['$field'])" <<<"$json"
}

register_and_login() {
  curl -s -X POST "$BASE_URL/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" >/dev/null || true
  local login
  login="$(curl -s -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")"
  json_field "$login" "access_token"
}

admin_create_show() {
  local admin_login
  admin_login="$(curl -s -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"adminrole"}')"
  local admin_token
  admin_token="$(json_field "$admin_login" "access_token")"
  local create
  create="$(curl -s -X POST "$BASE_URL/shows" \
    -H "Authorization: Bearer $admin_token" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"Burst Show\",\"seats\":[\"$SEAT\"],\"price_paise\":10000,\"per_user_limit\":4}")"
  json_field "$create" "id"
}

USER_TOKEN="$(register_and_login)"
SHOW_ID="$(admin_create_show)"

log "Running $REQUESTS concurrent reservations for seat $SEAT on show $SHOW_ID"

for i in $(seq 1 "$REQUESTS"); do
  (
    code="$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/shows/$SHOW_ID/reserve" \
      -H "Authorization: Bearer $USER_TOKEN" \
      -H "Content-Type: application/json" \
      -H "Idempotency-Key: burst-$i" \
      -d "{\"seats\":[\"$SEAT\"]}")"
    echo "$code" >>"$tmpdir/codes"
  ) &
done
wait

CREATED=0
REPLAY=0
CONFLICT=0
SERVER_ERRORS=0
while read -r code; do
  case "$code" in
    201) CREATED=$((CREATED + 1)) ;;
    200) REPLAY=$((REPLAY + 1)) ;;
    409) CONFLICT=$((CONFLICT + 1)) ;;
    5*) SERVER_ERRORS=$((SERVER_ERRORS + 1)) ;;
  esac
done <"$tmpdir/codes"

SHOW_JSON="$(curl -s "$BASE_URL/shows/$SHOW_ID")"
FINAL_STATUS="$(python3 -c "import json,sys; data=json.load(sys.stdin); print(next(s['status'] for s in data['seats'] if s['seat_number']=='$SEAT'))" <<<"$SHOW_JSON")"

echo "========================================"
echo " Seat Reservation Concurrency Test"
echo "========================================"
echo
echo "Requests:       $REQUESTS"
echo "Seat:           $SEAT"
echo
echo "201 Created:    $CREATED"
echo "200 Replay:     $REPLAY"
echo "409 Conflict:   $CONFLICT"
echo "5xx Errors:     $SERVER_ERRORS"
OTHER=$((REQUESTS - CREATED - REPLAY - CONFLICT - SERVER_ERRORS))
echo "Other (401…):   $OTHER"
echo
echo "Final Seat State:"
echo "$SEAT = $FINAL_STATUS"
echo

PASS=true
if [[ "$CREATED" -ne 1 || "$SERVER_ERRORS" -ne 0 || "$OTHER" -ne 0 || "$FINAL_STATUS" != "CONFIRMED" ]]; then
  PASS=false
fi
if [[ "$((CREATED + REPLAY + CONFLICT))" -ne "$REQUESTS" ]]; then
  PASS=false
fi

if [[ "$PASS" == true ]]; then
  echo "Result:"
  echo "PASS"
  echo "========================================"
  exit 0
fi

echo "Result:"
echo "FAIL"
echo "========================================"
exit 1
