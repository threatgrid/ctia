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
  -d '{
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
  }')

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
  SAMPLE=$(curl -s ${AUTH_OPTS} -X GET "http://${ES_HOST}/${INDEX_NAME}/_search?size=1" \
    -H 'Content-Type: application/json' \
    -d '{
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
    }')
  if command -v jq &> /dev/null; then
    echo "${SAMPLE}" | jq .
  else
    echo "${SAMPLE}"
  fi
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

# Create temp file for request body to avoid shell escaping issues
TMPFILE=$(mktemp)
trap "rm -f ${TMPFILE}" EXIT

# Write the request body with Painless script
# The script extracts entity type from CTIM reference URLs
# Format: http://host/ctia/<entity_type>/<entity_type>-<uuid>
# Example: http://example.com/ctia/malware/malware-123 -> "malware"
cat > "${TMPFILE}" << 'EOFBODY'
{
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
  },
  "script": {
    "source": "String sourceRef = ctx._source.source_ref; if (sourceRef != null) { int ctiaIdx = sourceRef.indexOf('/ctia/'); if (ctiaIdx != -1) { String path = sourceRef.substring(ctiaIdx + 6); int slashIdx = path.indexOf('/'); if (slashIdx != -1) { ctx._source.source_type = path.substring(0, slashIdx); } } } String targetRef = ctx._source.target_ref; if (targetRef != null) { int ctiaIdx2 = targetRef.indexOf('/ctia/'); if (ctiaIdx2 != -1) { String path2 = targetRef.substring(ctiaIdx2 + 6); int slashIdx2 = path2.indexOf('/'); if (slashIdx2 != -1) { ctx._source.target_type = path2.substring(0, slashIdx2); } } }",
    "lang": "painless"
  }
}
EOFBODY

# Execute _update_by_query
RESPONSE=$(curl -s ${AUTH_OPTS} -X POST "http://${ES_HOST}/${INDEX_NAME}/_update_by_query?${QUERY_PARAMS}" \
  -H 'Content-Type: application/json' \
  -d @"${TMPFILE}")

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
    if command -v jq &> /dev/null; then
      echo "${RESPONSE}" | jq .
    else
      echo "${RESPONSE}"
    fi
    exit 1
  fi
else
  echo "Response:"
  if command -v jq &> /dev/null; then
    echo "${RESPONSE}" | jq .
  else
    echo "${RESPONSE}"
  fi
  echo ""

  # Extract stats from response
  UPDATED=$(echo "${RESPONSE}" | grep -o '"updated":[0-9]*' | grep -o '[0-9]*' || echo "0")
  # Check for non-empty failures array (failures with actual content)
  HAS_FAILURES=$(echo "${RESPONSE}" | grep -o '"failures":\[[^]]*\]' | grep -v '"failures":\[\]' || echo "")

  echo "=== Migration Complete ==="
  echo "Documents updated: ${UPDATED}"

  if [[ -n "${HAS_FAILURES}" ]]; then
    echo "WARNING: Some failures occurred:"
    echo "${HAS_FAILURES}"
    exit 1
  fi

  echo "Migration successful!"
fi
