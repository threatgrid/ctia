#!/bin/bash

set -Eeuxo pipefail

if ! command -v trojansourcedetector &> /dev/null 
then
  rm -rf tmp/trojansourcedetector
  mkdir -p tmp/trojansourcedetector
  cd tmp/trojansourcedetector
  curl -sLO https://github.com/haveyoudebuggedit/trojansourcedetector/releases/download/v1.0.1/trojansourcedetector_1.0.1_linux_amd64.tar.gz
  sha256sum trojansourcedetector_1.0.1_linux_amd64.tar.gz | grep 62dfc2afb37c0124b755dbcee52e5af5cea2da372609cc83a8c2cbb62caf7598 
  tar -xf trojansourcedetector_1.0.1_linux_amd64.tar.gz
  cp trojansourcedetector "${BIN_PATH}"
fi
