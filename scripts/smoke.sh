#!/usr/bin/env bash
# Smoke test for the REST API. Needs a running app; safe to rerun (uses fresh usernames each time).
#   Usage: scripts/smoke.sh [base-url]        (default http://localhost:8080)
# Prints one line per check and exits non-zero if any status code (or expected body/content type) mismatches.
set -uo pipefail

BASE="${1:-http://localhost:8080}"
RUN="$(date +%s%N | tail -c 7)"
ALICE="alice$RUN" BOB="bob$RUN" CAROL="carol$RUN"
BODY="$(mktemp)"
trap 'rm -f "$BODY"' EXIT
PASSED=0 FAILED=0

# check METHOD PATH EXPECTED_STATUS [JSON_BODY] [REGEX_THE_RESPONSE_BODY_MUST_MATCH]
# Error responses (>= 400) must also be application/problem+json.
check() {
  local method="$1" path="$2" expected="$3" body="${4:-}" pattern="${5:-}"
  local args=(-s -o "$BODY" -w '%{http_code} %{content_type}' -X "$method")
  [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' --data-raw "$body")
  local out status ctype problem=""
  out="$(curl "${args[@]}" "$BASE$path")" || out="000 -"
  status="${out%% *}" ctype="${out#* }"

  if [[ "$status" != "$expected" ]]; then
    problem="expected $expected"
  elif (( status >= 400 )) && [[ "$ctype" != application/problem+json* ]]; then
    problem="error body is '$ctype', not problem+json"
  elif [[ -n "$pattern" ]] && ! grep -Eq "$pattern" "$BODY"; then
    problem="body doesn't match /$pattern/"
  fi

  if [[ -z "$problem" ]]; then
    PASSED=$((PASSED + 1)); printf 'PASS  %s  %-6s %s\n' "$status" "$method" "$path"
  else
    FAILED=$((FAILED + 1)); printf 'FAIL  %s  %-6s %s  (%s)\n      body: %s\n' "$status" "$method" "$path" "$problem" "$(head -c 300 "$BODY")"
  fi
}

# Pulls the first "key":number out of the last response body
json_number() { grep -oE "\"$1\":[0-9]+" "$BODY" | head -1 | grep -oE '[0-9]+$'; }

echo "Smoke testing $BASE as $ALICE / $BOB / $CAROL"

echo "--- rooms"
check GET    /api/rooms 200 "" '"name":"general"'
ROOM="$(grep -oE '"id":[0-9]+,"name":"general"' "$BODY" | grep -oE '[0-9]+' | head -1)"
[[ -z "$ROOM" ]] && { echo "Could not find room 'general'; is the app running at $BASE?"; exit 1; }
check PUT    "/api/rooms/$ROOM/members/$ALICE" 204
check PUT    "/api/rooms/$ROOM/members/$ALICE" 204          # idempotent
check PUT    "/api/rooms/$ROOM/members/$BOB"   204
check DELETE "/api/rooms/$ROOM/members/$BOB"   204
check DELETE "/api/rooms/$ROOM/members/$BOB"   204          # idempotent
check PUT    "/api/rooms/$ROOM/members/$CAROL" 204
check GET    /api/rooms 200 "" "\"members\":\[[^]]*\"$ALICE\""
check PUT    "/api/rooms/999999/members/$ALICE" 404
check PUT    "/api/rooms/abc/members/$ALICE"    400
check PUT    "/api/rooms/$ROOM/members/bad%20name" 400

echo "--- direct messages"
check POST /api/dms 201 "{\"userA\":\"$ALICE\",\"userB\":\"$BOB\"}" '"created":true'
DM="$(json_number id)"
check POST /api/dms 200 "{\"userA\":\"$BOB\",\"userB\":\"$ALICE\"}" "\"id\":$DM,"   # same DM, either order
check POST /api/dms 400 "{\"userA\":\"$ALICE\",\"userB\":\"$ALICE\"}"
check POST /api/dms 400 '{not json'

echo "--- messages"
MSGS="/api/conversations/$DM/messages"
check POST "$MSGS" 201 "{\"sender\":\"$ALICE\",\"content\":\"hello\",\"clientMessageId\":\"m-1\"}" '"seq":1,'
check POST "$MSGS" 200 "{\"sender\":\"$ALICE\",\"content\":\"hello\",\"clientMessageId\":\"m-1\"}" '"seq":1,'   # retry
check POST "$MSGS" 409 "{\"sender\":\"$ALICE\",\"content\":\"changed\",\"clientMessageId\":\"m-1\"}"
check POST "$MSGS" 201 "{\"sender\":\"$BOB\",\"content\":\"hi back\"}" '"seq":2,'
check POST "$MSGS" 400 "{\"sender\":\"$ALICE\",\"content\":\"   \"}"
check POST "$MSGS" 403 "{\"sender\":\"$CAROL\",\"content\":\"let me in\"}"
check POST /api/conversations/999999/messages 404 "{\"sender\":\"$ALICE\",\"content\":\"hi\"}"

echo "--- history"
check GET "$MSGS?user=$BOB&afterSeq=0&limit=1" 200 "" '"seq":1,.*"hasMore":true'
check GET "$MSGS?user=$BOB&afterSeq=1&limit=1" 200 "" '"seq":2,.*"hasMore":false'
check GET "$MSGS?user=$CAROL"                  403
check GET "$MSGS?user=$BOB&limit=0"            400
check GET "$MSGS"                              400

echo "--- unread"
# Bob sent seq 2, which marks everything up to it read for him; Alice hasn't read Bob's reply
check GET "/api/users/$BOB/conversations"   200 "" "\"id\":$DM,[^}]*\"unreadCount\":0"
check GET "/api/users/$ALICE/conversations" 200 "" "\"id\":$DM,[^}]*\"unreadCount\":1"
check PUT "/api/conversations/$DM/read"     204 "{\"user\":\"$ALICE\",\"seq\":2}"
check PUT "/api/conversations/$DM/read"     204 "{\"user\":\"$ALICE\",\"seq\":1}"   # never moves back
check GET "/api/users/$ALICE/conversations" 200 "" "\"id\":$DM,[^}]*\"unreadCount\":0"
check PUT "/api/conversations/$DM/read"     403 "{\"user\":\"$CAROL\",\"seq\":1}"

echo "--- misc"
check GET    /api/nope  404
check DELETE /api/rooms 405
check GET    /          200 "" '<title>Chat</title>'

echo
echo "$PASSED passed, $FAILED failed"
(( FAILED == 0 ))
