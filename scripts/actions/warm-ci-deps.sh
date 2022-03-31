#!/bin/bash

lein warm-ci-deps

# -P https://clojure.org/reference/deps_and_cli#_prepare_for_execution
## ./build/run-tests.sh
clojure -P -A:test:bat-test
clojure -P -A:test:bat-test:next-clojure
## ./scripts/uberjar
clojure -P -T:build
