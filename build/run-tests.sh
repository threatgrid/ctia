#!/bin/bash

set -ex

if [[ "$GITHUB_EVENT_NAME" == "schedule" || "$TRAVIS_EVENT_TYPE" == "cron" ]]; then
  lein cron-run-tests
else
  lein ci-run-tests
fi
