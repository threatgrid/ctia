#!/bin/bash
#
# Migration script to backfill source_type and target_type fields on relationship documents.
# Uses Elasticsearch _update_by_query with Painless script.
#
# Usage:
#   ./migrate_relationship_types.sh <ES_HOST> <INDEX_NAME> [OPTIONS]
#
# Options:
#   --dry-run            Show count and sample doc without making changes
#   --throttle <N>       Limit to N documents per second (default: unlimited)
#   --async              Run in background, return task ID for monitoring
#
# Examples:
#   # Dry run (shows how many docs would be updated)
#   ./migrate_relationship_types.sh localhost:9200 ctia_relationship --dry-run
#
#   # Execute migration (full speed)
#   ./migrate_relationship_types.sh localhost:9200 ctia_relationship
#
#   # Throttled execution (1000 docs/sec) - safer for production
#   ./migrate_relationship_types.sh localhost:9200 ctia_relationship --throttle 1000
#
#   # Async execution (returns task ID to monitor)
#   ./migrate_relationship_types.sh localhost:9200 ctia_relationship --async
#
#   # Throttled + async (recommended for large production indices)
#   ./migrate_relationship_types.sh localhost:9200 ctia_relationship --throttle 500 --async
#
#   # With authentication
#   ES_USER=elastic ES_PASS=changeme ./migrate_relationship_types.sh localhost:9200 ctia_relationship
#
# Monitoring async tasks:
#   # Check task status
#   curl -X GET "http://localhost:9200/_tasks/<task_id>"
#
#   # List all update_by_query tasks
#   curl -X GET "http://localhost:9200/_tasks?actions=*update_by_query&detailed"
#
#   # Cancel a running task
#   curl -X POST "http://localhost:9200/_tasks/<task_id>/_cancel"
#

set -euo pipefail

# Parse arguments
ES_HOST="${1:-localhost:9200}"
INDEX_NAME="${2:-ctia_relationship}"
shift 2 || true

DRY_RUN=false
THROTTLE=""
ASYNC=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --throttle)
      THROTTLE="$2"
      shift 2
      ;;
    --async)
      ASYNC=true
      shift
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done

# Build auth header if credentials provided
AUTH_OPTS=""
if [[ -n "${ES_USER:-}" && -n "${ES_PASS:-}" ]]; then
  AUTH_OPTS="-u ${ES_USER}:${ES_PASS}"
fi

# Painless script to extract entity type from CTIM reference URLs
# Format: http://host/ctia/<entity_type>/<entity_type>-<uuid>
# Example: http://example.com/ctia/malware/malware-123 -> "malware"
PAINLESS_SCRIPT='
String extractType(String ref) {
  if (ref == null || ref.isEmpty()) {
    return null;
  }
  // Find /ctia/ in the path
  int ctiaIdx = ref.indexOf("/ctia/");
  if (ctiaIdx == -1) {
    return null;
  }
  // Extract the path after /ctia/
  String path = ref.substring(ctiaIdx + 6);
  // Get the entity type (first path segment)
  int slashIdx = path.indexOf("/");
  if (slashIdx == -1) {
    return null;
  }
  return path.substring(0, slashIdx);
}

// Extract types from refs
String sourceType = extractType(ctx._source.source_ref);
String targetType = extractType(ctx._source.target_ref);

// Only update if we successfully extracted types
if (sourceType != null) {
  ctx._source.source_type = sourceType;
}
if (targetType != null) {
  ctx._source.target_type = targetType;
}
'

# Query for documents missing source_type OR target_type
QUERY='{
  "query": {
    "bool": {
      "must": [
        { "term": { "type": "relationship" } }
      ],
      "should": [
        { "bool": { "must_not": { "exists": { "field": "source_type" } } } },
        { "bool": { "must_not": { "exists": { "field": "target_type" } } } }
      ],
      "minimum_should_match": 1
    }
  }
}'

echo "=== Relationship Type Migration ==="
echo "ES Host: ${ES_HOST}"
echo "Index: ${INDEX_NAME}"
echo "Throttle: ${THROTTLE:-unlimited}"
echo "Async: ${ASYNC}"
echo ""

# Count documents to be updated
echo "Counting documents to migrate..."
COUNT_RESPONSE=$(curl -s ${AUTH_OPTS} -X GET "http://${ES_HOST}/${INDEX_NAME}/_count" \
  -H 'Content-Type: application/json' \
  -d "${QUERY}")

DOC_COUNT=$(echo "${COUNT_RESPONSE}" | grep -o '"count":[0-9]*' | grep -o '[0-9]*' || echo "0")
echo "Documents to update: ${DOC_COUNT}"
echo ""

if [[ "${DOC_COUNT}" == "0" ]]; then
  echo "No documents need migration. Done."
  exit 0
fi

if [[ "${DRY_RUN}" == "true" ]]; then
  echo "[DRY RUN] Would update ${DOC_COUNT} documents."
  if [[ -n "${THROTTLE}" ]]; then
    ESTIMATED_TIME=$((DOC_COUNT / THROTTLE))
    echo "Estimated time with throttle: ~${ESTIMATED_TIME} seconds"
  fi
  echo ""
  echo "Sample document (first match):"
  curl -s ${AUTH_OPTS} -X GET "http://${ES_HOST}/${INDEX_NAME}/_search?size=1" \
    -H 'Content-Type: application/json' \
    -d "${QUERY}" | python3 -m json.tool 2>/dev/null || cat
  exit 0
fi

echo "Starting migration..."
echo ""

# Build query parameters
QUERY_PARAMS="conflicts=proceed&scroll_size=1000"

if [[ "${ASYNC}" == "true" ]]; then
  QUERY_PARAMS="${QUERY_PARAMS}&wait_for_completion=false"
else
  QUERY_PARAMS="${QUERY_PARAMS}&wait_for_completion=true"
fi

if [[ -n "${THROTTLE}" ]]; then
  QUERY_PARAMS="${QUERY_PARAMS}&requests_per_second=${THROTTLE}"
fi

# Execute _update_by_query
RESPONSE=$(curl -s ${AUTH_OPTS} -X POST "http://${ES_HOST}/${INDEX_NAME}/_update_by_query?${QUERY_PARAMS}" \
  -H 'Content-Type: application/json' \
  -d "{
    \"query\": ${QUERY#*\"query\": },
    \"script\": {
      \"source\": $(echo "${PAINLESS_SCRIPT}" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))'),
      \"lang\": \"painless\"
    }
  }")

if [[ "${ASYNC}" == "true" ]]; then
  # Extract task ID from async response
  TASK_ID=$(echo "${RESPONSE}" | grep -o '"task":"[^"]*"' | cut -d'"' -f4 || echo "")

  if [[ -n "${TASK_ID}" ]]; then
    echo "=== Migration Started (Async) ==="
    echo "Task ID: ${TASK_ID}"
    echo ""
    echo "Monitor progress:"
    echo "  curl -X GET \"http://${ES_HOST}/_tasks/${TASK_ID}\""
    echo ""
    echo "Cancel if needed:"
    echo "  curl -X POST \"http://${ES_HOST}/_tasks/${TASK_ID}/_cancel\""
    echo ""
    echo "The migration is running in the background."
  else
    echo "ERROR: Failed to start async task"
    echo "${RESPONSE}" | python3 -m json.tool 2>/dev/null || echo "${RESPONSE}"
    exit 1
  fi
else
  echo "Response:"
  echo "${RESPONSE}" | python3 -m json.tool 2>/dev/null || echo "${RESPONSE}"
  echo ""

  # Extract stats from response
  UPDATED=$(echo "${RESPONSE}" | grep -o '"updated":[0-9]*' | grep -o '[0-9]*' || echo "0")
  FAILURES=$(echo "${RESPONSE}" | grep -o '"failures":\[[^]]*\]' || echo "[]")

  echo "=== Migration Complete ==="
  echo "Documents updated: ${UPDATED}"

  if [[ "${FAILURES}" != "[]" && "${FAILURES}" != "" ]]; then
    echo "WARNING: Some failures occurred:"
    echo "${FAILURES}"
    exit 1
  fi

  echo "Migration successful!"
fi
