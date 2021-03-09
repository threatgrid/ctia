#!/bin/bash

set -ex

cd "${BIN_PATH}"
# poor man's travis_retry
for i in {1..${ACTIONS_RETRY_TIMES}}; do curl -sLO "https://raw.githubusercontent.com/technomancy/leiningen/${LEIN_VERSION}/bin/lein" && break; done
chmod a+x lein
# poor man's travis_retry
for i in {1..${ACTIONS_RETRY_TIMES}}; do lein version && break; done
