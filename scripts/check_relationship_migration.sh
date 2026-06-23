#!/bin/bash
#
# Script to check the status of relationship type migration.
# Verifies that source_type and target_type fields are properly populated.
#
# Usage:
#   ./check_relationship_migration.sh <ES_HOST> <INDEX_NAME>
#
# Examples:
#   ./check_relationship_migration.sh localhost:9200 ctia_relationship
#   ES_USER=elastic ES_PASS=changeme ./check_relationship_migration.sh prod-es:9200 ctia_relationship
#

set -euo pipefail

ES_HOST="${1:-localhost:9200}"
INDEX_NAME="${2:-ctia_relationship}"

# Build auth header if credentials provided
AUTH_OPTS=""
if [[ -n "${ES_USER:-}" && -n "${ES_PASS:-}" ]]; then
  AUTH_OPTS="-u ${ES_USER}:${ES_PASS}"
fi

echo "=== Relationship Type Migration Check ==="
echo "ES Host: ${ES_HOST}"
echo "Index: ${INDEX_NAME}"
echo ""

# Total relationship documents
TOTAL=$(curl -s ${AUTH_OPTS} "http://${ES_HOST}/${INDEX_NAME}/_count" \
  -H 'Content-Type: application/json' \
  -d '{"query":{"term":{"type":"relationship"}}}' | grep -o '"count":[0-9]*' | grep -o '[0-9]*' || echo "0")

# Documents with source_type
WITH_SOURCE_TYPE=$(curl -s ${AUTH_OPTS} "http://${ES_HOST}/${INDEX_NAME}/_count" \
  -H 'Content-Type: application/json' \
  -d '{"query":{"bool":{"must":[{"term":{"type":"relationship"}},{"exists":{"field":"source_type"}}]}}}' | grep -o '"count":[0-9]*' | grep -o '[0-9]*' || echo "0")

# Documents with target_type
WITH_TARGET_TYPE=$(curl -s ${AUTH_OPTS} "http://${ES_HOST}/${INDEX_NAME}/_count" \
  -H 'Content-Type: application/json' \
  -d '{"query":{"bool":{"must":[{"term":{"type":"relationship"}},{"exists":{"field":"target_type"}}]}}}' | grep -o '"count":[0-9]*' | grep -o '[0-9]*' || echo "0")

# Documents with BOTH fields
WITH_BOTH=$(curl -s ${AUTH_OPTS} "http://${ES_HOST}/${INDEX_NAME}/_count" \
  -H 'Content-Type: application/json' \
  -d '{"query":{"bool":{"must":[{"term":{"type":"relationship"}},{"exists":{"field":"source_type"}},{"exists":{"field":"target_type"}}]}}}' | grep -o '"count":[0-9]*' | grep -o '[0-9]*' || echo "0")

# Documents missing EITHER field (need migration)
NEEDS_MIGRATION=$(curl -s ${AUTH_OPTS} "http://${ES_HOST}/${INDEX_NAME}/_count" \
  -H 'Content-Type: application/json' \
  -d '{
    "query": {
      "bool": {
        "must": [{"term": {"type": "relationship"}}],
        "should": [
          {"bool": {"must_not": {"exists": {"field": "source_type"}}}},
          {"bool": {"must_not": {"exists": {"field": "target_type"}}}}
        ],
        "minimum_should_match": 1
      }
    }
  }' | grep -o '"count":[0-9]*' | grep -o '[0-9]*' || echo "0")

echo "=== Migration Statistics ==="
echo ""
echo "Total relationships:        ${TOTAL}"
echo "With source_type:           ${WITH_SOURCE_TYPE}"
echo "With target_type:           ${WITH_TARGET_TYPE}"
echo "With both fields:           ${WITH_BOTH}"
echo "Needs migration:            ${NEEDS_MIGRATION}"
echo ""

if [[ "${TOTAL}" -gt 0 ]]; then
  PERCENT_COMPLETE=$((WITH_BOTH * 100 / TOTAL))
  echo "Migration progress:         ${PERCENT_COMPLETE}%"
  echo ""
fi

if [[ "${NEEDS_MIGRATION}" == "0" ]]; then
  echo "✅ Migration complete! All documents have source_type and target_type."
else
  echo "⚠️  Migration incomplete. ${NEEDS_MIGRATION} documents still need migration."
fi
echo ""

# Show breakdown by source_type
echo "=== Breakdown by source_type ==="
curl -s ${AUTH_OPTS} "http://${ES_HOST}/${INDEX_NAME}/_search?size=0" \
  -H 'Content-Type: application/json' \
  -d '{
    "query": {"term": {"type": "relationship"}},
    "aggs": {
      "source_types": {
        "terms": {"field": "source_type", "size": 50, "missing": "(not set)"}
      }
    }
  }' | jq -r '.aggregations.source_types.buckets[] | "\(.key): \(.doc_count)"' 2>/dev/null || echo "(jq not available for aggregation display)"

echo ""

# Show breakdown by target_type
echo "=== Breakdown by target_type ==="
curl -s ${AUTH_OPTS} "http://${ES_HOST}/${INDEX_NAME}/_search?size=0" \
  -H 'Content-Type: application/json' \
  -d '{
    "query": {"term": {"type": "relationship"}},
    "aggs": {
      "target_types": {
        "terms": {"field": "target_type", "size": 50, "missing": "(not set)"}
      }
    }
  }' | jq -r '.aggregations.target_types.buckets[] | "\(.key): \(.doc_count)"' 2>/dev/null || echo "(jq not available for aggregation display)"

echo ""

# Validate a sample of migrated documents
echo "=== Sample Validation (5 random migrated docs) ==="
SAMPLE=$(curl -s ${AUTH_OPTS} "http://${ES_HOST}/${INDEX_NAME}/_search?size=5" \
  -H 'Content-Type: application/json' \
  -d '{
    "query": {
      "bool": {
        "must": [
          {"term": {"type": "relationship"}},
          {"exists": {"field": "source_type"}},
          {"exists": {"field": "target_type"}}
        ]
      }
    },
    "_source": ["source_ref", "target_ref", "source_type", "target_type"],
    "sort": [{"_script": {"type": "number", "script": "Math.random()", "order": "asc"}}]
  }')

# Validate each sample
VALIDATION_ERRORS=0
if command -v jq &> /dev/null; then
  echo "${SAMPLE}" | jq -r '.hits.hits[]._source | "source_ref: \(.source_ref)\nsource_type: \(.source_type)\ntarget_ref: \(.target_ref)\ntarget_type: \(.target_type)\n---"' 2>/dev/null

  # Check if source_type matches what's in source_ref
  while IFS= read -r line; do
    source_ref=$(echo "$line" | jq -r '.source_ref // empty')
    source_type=$(echo "$line" | jq -r '.source_type // empty')
    target_ref=$(echo "$line" | jq -r '.target_ref // empty')
    target_type=$(echo "$line" | jq -r '.target_type // empty')

    if [[ -n "$source_ref" && -n "$source_type" ]]; then
      # Extract expected type from source_ref
      expected_source=$(echo "$source_ref" | sed -n 's|.*/ctia/\([^/]*\)/.*|\1|p')
      if [[ "$expected_source" != "$source_type" ]]; then
        echo "❌ Mismatch: source_type='$source_type' but source_ref suggests '$expected_source'"
        VALIDATION_ERRORS=$((VALIDATION_ERRORS + 1))
      fi
    fi

    if [[ -n "$target_ref" && -n "$target_type" ]]; then
      expected_target=$(echo "$target_ref" | sed -n 's|.*/ctia/\([^/]*\)/.*|\1|p')
      if [[ "$expected_target" != "$target_type" ]]; then
        echo "❌ Mismatch: target_type='$target_type' but target_ref suggests '$expected_target'"
        VALIDATION_ERRORS=$((VALIDATION_ERRORS + 1))
      fi
    fi
  done < <(echo "${SAMPLE}" | jq -c '.hits.hits[]._source' 2>/dev/null)
else
  echo "(jq not available for detailed validation)"
fi

echo ""
echo "=== Summary ==="
if [[ "${NEEDS_MIGRATION}" == "0" && "${VALIDATION_ERRORS}" == "0" ]]; then
  echo "✅ Migration verified successfully!"
  echo "   - All ${TOTAL} documents have source_type and target_type"
  echo "   - Sample validation passed"
  exit 0
elif [[ "${NEEDS_MIGRATION}" == "0" ]]; then
  echo "⚠️  Migration complete but validation found ${VALIDATION_ERRORS} mismatches"
  exit 1
else
  echo "⏳ Migration in progress: ${WITH_BOTH}/${TOTAL} complete (${NEEDS_MIGRATION} remaining)"
  exit 2
fi
