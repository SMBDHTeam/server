#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${E2E_BASE_URL:-http://13.209.73.113}"
ALLOW_WRITE="${E2E_ALLOW_WRITE:-false}"
RESULT_ROOT="${E2E_RESULT_DIR:-build/e2e-results}"
TIMEOUT_PERSIST_WAIT_SECONDS="${E2E_TIMEOUT_PERSIST_WAIT_SECONDS:-60}"

if [[ ! "$TIMEOUT_PERSIST_WAIT_SECONDS" =~ ^[0-9]+$ ]]; then
  echo "E2E_TIMEOUT_PERSIST_WAIT_SECONDS must be a non-negative integer." >&2
  exit 2
fi

active_share_schedule_id=""
active_share_id=""
share_response_raw=""

cleanup_active_share() {
  local exit_status=$?
  local cleanup_share_id="$active_share_id"
  if [[ -z "$cleanup_share_id" && -n "$share_response_raw" && -f "$share_response_raw" ]]; then
    cleanup_share_id="$(jq -r '.id // empty' "$share_response_raw" 2>/dev/null || true)"
  fi
  if [[ -n "$active_share_schedule_id" && -n "$cleanup_share_id" ]]; then
    curl -sS --max-time 20 \
      -X DELETE \
      "$BASE_URL/api/v1/schedules/$active_share_schedule_id/shares/$cleanup_share_id" \
      >/dev/null 2>&1 || true
  fi
  if [[ -n "$share_response_raw" ]]; then
    rm -f "$share_response_raw"
  fi
  return "$exit_status"
}

trap cleanup_active_share EXIT

if [[ "$ALLOW_WRITE" != "true" ]]; then
  echo "Set E2E_ALLOW_WRITE=true to create schedules in the target server." >&2
  exit 2
fi

for command_name in curl jq; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "$command_name is required." >&2
    exit 2
  fi
done

add_days() {
  local base_date="$1"
  local days="$2"
  if date -j -v+"${days}"d -f "%Y-%m-%d" "$base_date" "+%Y-%m-%d" 2>/dev/null; then
    return
  fi
  date -d "$base_date +${days} days" "+%Y-%m-%d"
}

request() {
  local method="$1"
  local path="$2"
  local output_file="$3"
  local payload_file="${4:-}"
  local status
  local curl_args=(-sS --max-time 180 -X "$method" -o "$output_file" -w "%{http_code}")
  if [[ -n "$payload_file" ]]; then
    curl_args+=(-H "Content-Type: application/json" --data-binary "@$payload_file")
  fi
  status="$(curl "${curl_args[@]}" "$BASE_URL$path")"
  printf '%s' "$status"
}

assert_status() {
  local actual="$1"
  local expected="$2"
  local response_file="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo "Expected HTTP $expected but received $actual: $(jq -c . "$response_file" 2>/dev/null || head -c 500 "$response_file")" >&2
    exit 1
  fi
}

timestamp="$(date '+%Y%m%d-%H%M%S')"
result_dir="$RESULT_ROOT/$timestamp"
mkdir -p "$result_dir"

start_date="${E2E_START_DATE:-$(add_days "$(date '+%Y-%m-%d')" 14)}"
second_date="$(add_days "$start_date" 1)"
third_date="$(add_days "$start_date" 2)"

places_file="$result_dir/places.json"
status="$(request GET '/api/v1/places?longitude=129.0403&latitude=35.1151&radius=5000' "$places_file")"
assert_status "$status" 200 "$places_file"
jq -e '.items | length >= 5' "$places_file" >/dev/null

place_1="$(jq -r '.items[0].id' "$places_file")"
place_2="$(jq -r '.items[1].id' "$places_file")"
place_3="$(jq -r '.items[2].id' "$places_file")"

common_answers='[
  {"questionId":"COMPANION","answerId":"COMPANION_PARENTS"},
  {"questionId":"THEME","answerId":"THEME_LOCAL"},
  {"questionId":"PACE","answerId":"PACE_BALANCED"},
  {"questionId":"MOBILITY","answerId":"MOBILITY_LOW_WALK"},
  {"questionId":"TRANSIT","answerId":"TRANSIT_SIMPLE"}
]'

one_day_request="$result_dir/one-day-request.json"
jq -n \
  --arg startDate "$start_date" \
  --argjson placeId "$place_1" \
  --argjson answers "$common_answers" \
  '{
    startDate:$startDate,
    endDate:$startDate,
    dailyStartTime:"09:00",
    dailyEndTime:"19:00",
    startLocation:{name:"부산역",longitude:129.0403,latitude:35.1151},
    endLocation:{name:"부산역",longitude:129.0403,latitude:35.1151},
    selectedAnswers:$answers,
    mustVisitPlaceIds:[$placeId]
  }' > "$one_day_request"

one_day_response="$result_dir/one-day-response.json"
status="$(request POST '/api/v1/schedules' "$one_day_response" "$one_day_request")"
assert_status "$status" 201 "$one_day_response"
jq -e \
  --argjson requiredPlaceId "$place_1" \
  '.evaluation.hardGate.passed == true
    and (.evaluation.operations.externalHttpCallCount > 0)
    and (.evaluation.operations.providers | index("ODSAY") != null)
    and ([.days[].stops[].place.id] | index($requiredPlaceId) != null)
    and ([.days[].stops[].inboundTransit.provider, .days[].finalTransit.provider]
      | all(.[]; . != "FAKE"))' \
  "$one_day_response" >/dev/null

one_day_schedule_id="$(jq -r '.id' "$one_day_response")"

schedules_file="$result_dir/schedules.json"
status="$(request GET '/api/v1/schedules' "$schedules_file")"
assert_status "$status" 200 "$schedules_file"
jq -e --arg scheduleId "$one_day_schedule_id" \
  'any(.items[]; .id == $scheduleId)' "$schedules_file" >/dev/null

map_file="$result_dir/map.json"
status="$(request GET "/api/v1/schedules/$one_day_schedule_id/map?dayNo=1" "$map_file")"
assert_status "$status" 200 "$map_file"
jq -e '(.markers | length) > 0 and (.routeLines | type == "array")' "$map_file" >/dev/null

update_request="$result_dir/update-request.json"
jq '{
  stops:[
    .days[0].stops | to_entries[] |
    {
      stopId:.value.id,
      dayNo:1,
      order:(.key + 1),
      stayMinutes:(.value.stayMinutes + (if .key == 0 then 10 else 0 end))
    }
  ]
}' "$one_day_response" > "$update_request"

update_response="$result_dir/update-response.json"
status="$(request PATCH "/api/v1/schedules/$one_day_schedule_id" "$update_response" "$update_request")"
assert_status "$status" 200 "$update_response"
jq -e \
  '(.evaluation == null)
    and (.days[0].finalTransit != null)
    and ([.days[].stops[].inboundTransit.provider, .days[].finalTransit.provider]
      | all(.[]; . != "FAKE"))' \
  "$update_response" >/dev/null

share_request="$result_dir/share-request.json"
printf '%s\n' '{"expiresInDays":1}' > "$share_request"
share_response="$result_dir/share-response.json"
share_response_raw="$(mktemp "${TMPDIR:-/tmp}/tour-share-response.XXXXXX")"
chmod 600 "$share_response_raw"
active_share_schedule_id="$one_day_schedule_id"
status="$(request POST "/api/v1/schedules/$one_day_schedule_id/shares" "$share_response_raw" "$share_request")"
assert_status "$status" 201 "$share_response_raw"
share_id="$(jq -r '.id' "$share_response_raw")"
active_share_id="$share_id"
share_token="$(jq -r '.token' "$share_response_raw")"
jq '.token = "[REDACTED]" | .url = "[REDACTED]"' "$share_response_raw" > "$share_response"
rm -f "$share_response_raw"
share_response_raw=""

shared_file="$result_dir/shared-schedule.json"
status="$(request GET "/api/v1/shared-schedules/$share_token" "$shared_file")"
assert_status "$status" 200 "$shared_file"
jq -e --arg scheduleId "$one_day_schedule_id" \
  '.id == $scheduleId and .readOnly == true' "$shared_file" >/dev/null

shared_map_file="$result_dir/shared-map.json"
status="$(request GET "/api/v1/shared-schedules/$share_token/map?dayNo=1" "$shared_map_file")"
assert_status "$status" 200 "$shared_map_file"
jq -e '(.markers | length) > 0 and (.routeLines | type == "array")' "$shared_map_file" >/dev/null

revoke_file="$result_dir/revoke-response.txt"
status="$(request DELETE "/api/v1/schedules/$one_day_schedule_id/shares/$share_id" "$revoke_file")"
assert_status "$status" 204 "$revoke_file"
active_share_schedule_id=""
active_share_id=""
status="$(request GET "/api/v1/shared-schedules/$share_token" "$result_dir/revoked-share-response.json")"
assert_status "$status" 404 "$result_dir/revoked-share-response.json"
jq -e '.code == "SHARE_LINK_NOT_FOUND"' "$result_dir/revoked-share-response.json" >/dev/null
share_token=""

multi_day_request="$result_dir/multi-day-request.json"
jq -n \
  --arg firstDate "$start_date" \
  --arg secondDate "$second_date" \
  --arg thirdDate "$third_date" \
  --argjson firstPlaceId "$place_1" \
  --argjson secondPlaceId "$place_2" \
  --argjson thirdPlaceId "$place_3" \
  --argjson answers "$common_answers" \
  '{
    startDate:$firstDate,
    endDate:$thirdDate,
    dailyStartTime:"09:00",
    dailyEndTime:"19:00",
    startLocation:{name:"부산역",longitude:129.0403,latitude:35.1151},
    endLocation:{name:"김해국제공항",longitude:128.9485,latitude:35.1732},
    selectedAnswers:$answers,
    mustVisitPlaceIds:[$firstPlaceId,$secondPlaceId,$thirdPlaceId],
    days:[
      {
        dayNo:1,startTime:"09:00",endTime:"19:00",
        startLocation:{name:"부산역",longitude:129.0403,latitude:35.1151},
        endLocation:{name:"광안리 숙소",longitude:129.1186,latitude:35.1532}
      },
      {
        dayNo:2,startTime:"09:00",endTime:"19:00",
        startLocation:{name:"광안리 숙소",longitude:129.1186,latitude:35.1532},
        endLocation:{name:"남포동 숙소",longitude:129.0320,latitude:35.1000}
      },
      {
        dayNo:3,startTime:"09:00",endTime:"17:00",
        startLocation:{name:"남포동 숙소",longitude:129.0320,latitude:35.1000},
        endLocation:{name:"김해국제공항",longitude:128.9485,latitude:35.1732}
      }
    ]
  }' > "$multi_day_request"

multi_day_response="$result_dir/multi-day-response.json"
status="$(request POST '/api/v1/schedules' "$multi_day_response" "$multi_day_request")"
multi_day_outcome="PASS"
overall_status="PASS"
multi_day_evaluation="null"

if [[ "$status" == "201" ]]; then
  multi_day_validation_file="$multi_day_response"
  multi_day_schedule_id="$(jq -r '.id' "$multi_day_response")"
  multi_day_evaluation="$(jq '.evaluation' "$multi_day_response")"
  jq -e \
    '.evaluation.hardGate.passed == true
      and (.evaluation.operations.externalHttpCallCount > 0)
      and (.evaluation.operations.providers | index("ODSAY") != null)' \
    "$multi_day_response" >/dev/null
elif [[ "$status" == "504" ]]; then
  schedules_after_timeout="$result_dir/schedules-after-timeout.json"
  multi_day_schedule_id=""
  persist_deadline=$((SECONDS + TIMEOUT_PERSIST_WAIT_SECONDS))
  while (( SECONDS <= persist_deadline )); do
    timeout_list_status="$(request GET '/api/v1/schedules' "$schedules_after_timeout")"
    assert_status "$timeout_list_status" 200 "$schedules_after_timeout"
    multi_day_schedule_id="$(jq -r \
      --slurpfile before "$schedules_file" \
      --arg startDate "$start_date" \
      --arg endDate "$third_date" \
      --argjson firstPlaceId "$place_1" \
      --argjson secondPlaceId "$place_2" \
      --argjson thirdPlaceId "$place_3" \
      '[.items[] as $item
        | select($item.startDate == $startDate and $item.endDate == $endDate)
        | select(($before[0].items | map(.id) | index($item.id)) == null)
        | select(($item.days | length) == 3)
        | select($item.days[0].startLocation.name == "부산역")
        | select($item.days[0].endLocation.name == "광안리 숙소")
        | select($item.days[1].startLocation.name == "광안리 숙소")
        | select($item.days[1].endLocation.name == "남포동 숙소")
        | select($item.days[2].startLocation.name == "남포동 숙소")
        | select($item.days[2].endLocation.name == "김해국제공항")
        | select([$item.days[].stops[].place.id] as $ids
            | ($ids | index($firstPlaceId) != null)
            and ($ids | index($secondPlaceId) != null)
            and ($ids | index($thirdPlaceId) != null))
        | $item.id][0] // empty' \
      "$schedules_after_timeout")"
    if [[ -n "$multi_day_schedule_id" ]]; then
      break
    fi
    sleep 2
  done
  if [[ -z "$multi_day_schedule_id" ]]; then
    echo "The 3-day request timed out and no matching persisted schedule was found within ${TIMEOUT_PERSIST_WAIT_SECONDS}s." >&2
    exit 1
  fi
  multi_day_validation_file="$result_dir/multi-day-persisted.json"
  jq --arg scheduleId "$multi_day_schedule_id" \
    '.items[] | select(.id == $scheduleId)' \
    "$schedules_after_timeout" > "$multi_day_validation_file"
  multi_day_outcome="TIMEOUT_PERSISTED"
  overall_status="FAIL"
else
  assert_status "$status" 201 "$multi_day_response"
fi

jq -e \
  --argjson firstPlaceId "$place_1" \
  --argjson secondPlaceId "$place_2" \
  --argjson thirdPlaceId "$place_3" \
  '(.days | length == 3)
    and (.days[0].startLocation.name == "부산역")
    and (.days[0].endLocation.name == "광안리 숙소")
    and (.days[1].startLocation.name == "광안리 숙소")
    and (.days[1].endLocation.name == "남포동 숙소")
    and (.days[2].startLocation.name == "남포동 숙소")
    and (.days[2].endLocation.name == "김해국제공항")
    and ([.days[].stops[].place.id] as $ids
      | ($ids | index($firstPlaceId) != null)
      and ($ids | index($secondPlaceId) != null)
      and ($ids | index($thirdPlaceId) != null))
    and ([.days[].stops[].inboundTransit.provider, .days[].finalTransit.provider]
      | all(.[]; . != "FAKE"))' \
  "$multi_day_validation_file" >/dev/null

for day_no in 1 2 3; do
  multi_day_map="$result_dir/multi-day-map-$day_no.json"
  map_status="$(request GET "/api/v1/schedules/$multi_day_schedule_id/map?dayNo=$day_no" "$multi_day_map")"
  assert_status "$map_status" 200 "$multi_day_map"
  jq -e '(.markers | length) > 0 and (.routeLines | length) > 0' "$multi_day_map" >/dev/null
done

invalid_request="$result_dir/invalid-place-request.json"
jq '.mustVisitPlaceIds = [9223372036854775807]' "$one_day_request" > "$invalid_request"
invalid_response="$result_dir/invalid-place-response.json"
status="$(request POST '/api/v1/schedules' "$invalid_response" "$invalid_request")"
assert_status "$status" 404 "$invalid_response"
jq -e '.code == "PLACE_NOT_FOUND"' "$invalid_response" >/dev/null

jq -n \
  --arg baseUrl "$BASE_URL" \
  --arg executedAt "$timestamp" \
  --arg oneDayScheduleId "$one_day_schedule_id" \
  --arg multiDayScheduleId "$multi_day_schedule_id" \
  --arg overallStatus "$overall_status" \
  --arg multiDayOutcome "$multi_day_outcome" \
  --argjson oneDayEvaluation "$(jq '.evaluation' "$one_day_response")" \
  --argjson multiDayEvaluation "$multi_day_evaluation" \
  '{
    baseUrl:$baseUrl,
    executedAt:$executedAt,
    status:$overallStatus,
    oneDay:{scheduleId:$oneDayScheduleId,evaluation:$oneDayEvaluation},
    multiDay:{scheduleId:$multiDayScheduleId,outcome:$multiDayOutcome,evaluation:$multiDayEvaluation},
    shareLifecycle:"PASS",
    invalidPlace:"PASS",
    cleanupRequired:[$oneDayScheduleId,$multiDayScheduleId]
  }' > "$result_dir/summary.json"

echo "Result: $result_dir/summary.json"
echo "Schedules requiring cleanup: $one_day_schedule_id $multi_day_schedule_id"
if [[ "$overall_status" != "PASS" ]]; then
  echo "Dev schedule E2E failed: $multi_day_outcome" >&2
  exit 1
fi
echo "Dev schedule E2E passed."
