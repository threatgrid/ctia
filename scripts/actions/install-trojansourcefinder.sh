#!/bin/bash

set -Eeuxo pipefail

if ! command -v tsfinder &> /dev/null
then
  rm -rf tmp/trojansourcefinder
  mkdir -p tmp/trojansourcefinder
  cd tmp/trojansourcefinder
  curl -lO -L https://github.com/ariary/TrojanSourceFinder/releases/latest/download/tsfinder && chmod +x tsfinder
  sha256sum tsfinder | grep dc330acaaf155430001ec6afe52caac33443dca782695a147f79ac819f873b51
  cp tsfinder "${BIN_PATH}"
  cd ../../
  rm -rf tmp/trojansourcefinder
fi
