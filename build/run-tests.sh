#!/bin/bash

set -ex

if [[ "$GITHUB_EVENT_NAME" == "schedule" ]]; then
  lein with-profile +cron ci-run-tests
else
  lein ci-run-tests
fi
