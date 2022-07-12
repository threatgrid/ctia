#!/usr/bin/env bash

generate_post_data()
{
  cat <<EOF
{
  "password": "$ELASTIC_PASSWORD"
}
EOF
}

echo "$(generate_post_data)"

wait-for-it elasticsearch5:9200 --timeout=30 --strict -- curl -X POST -H "Content-Type: application/json" -d "$(generate_post_data)" --user "elastic:changeme" elasticsearch5:9200/_xpack/security/user/elastic/_password
