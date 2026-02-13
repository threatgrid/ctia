#!/bin/bash
#
# Migration script to backfill source_type and target_type fields on relationship documents.
# Uses Elasticsearch _update_by_query with Painless script.
#
# Usage:
#   ./migrate_relationship_types.sh <ES_HOST> <INDEX_NAME> [--dry-run]
#
# Examples:
#   # Dry run (shows how many docs would be updated)
#   ./migrate_relationship_types.sh localhost:9200 ctia_relationship --dry-run
#
#   # Execute migration
#   ./migrate_relationship_types.sh localhost:9200 ctia_relationship
#
#   # With authentication
#   ES_USER=elastic ES_PASS=changeme ./migrate_relationship_types.sh localhost:9200 ctia_relationship
#

set -euo pipefail

ES_HOST="${1:-localhost:9200}"
INDEX_NAME="${2:-ctia_relationship}"
DRY_RUN="${3:-}"

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

if [[ "${DRY_RUN}" == "--dry-run" ]]; then
  echo "[DRY RUN] Would update ${DOC_COUNT} documents."
  echo ""
  echo "Sample document (first match):"
  curl -s ${AUTH_OPTS} -X GET "http://${ES_HOST}/${INDEX_NAME}/_search?size=1" \
    -H 'Content-Type: application/json' \
    -d "${QUERY}" | python3 -m json.tool 2>/dev/null || cat
  exit 0
fi

echo "Starting migration..."
echo ""

# Execute _update_by_query
# - wait_for_completion=true: wait for the operation to complete
# - conflicts=proceed: continue even if there are version conflicts
# - scroll_size=1000: process 1000 docs at a time
RESPONSE=$(curl -s ${AUTH_OPTS} -X POST "http://${ES_HOST}/${INDEX_NAME}/_update_by_query?wait_for_completion=true&conflicts=proceed&scroll_size=1000" \
  -H 'Content-Type: application/json' \
  -d "{
    \"query\": ${QUERY#*\"query\": },
    \"script\": {
      \"source\": $(echo "${PAINLESS_SCRIPT}" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))'),
      \"lang\": \"painless\"
    }
  }")

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
