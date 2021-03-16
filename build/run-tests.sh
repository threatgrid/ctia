#!/bin/bash
# Callers should bind CTIA_TEST_SUITE=cron if this is a
# cron build, otherwise use CTIA_TEST_SUITE=ci or 
# leave unset for default test suite.

set -ex

if [[ "$CTIA_TEST_SUITE" == "cron" ]]; then
  lein cron-run-tests
else
  lein ci-run-tests
fi
