#!/bin/bash

set -ex

if [[ "$CTIA_TEST_SUITE" == "cron" ]]; then
  lein cron-run-tests
else
  lein ci-run-tests
fi
