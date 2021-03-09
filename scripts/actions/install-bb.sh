#!/bin/bash

set -ex

if ! command -v bb &> /dev/null || ! bb --version | grep "^babashka v${BB_VERSION}$"
then
  mkdir -p tmp
  cd tmp
  curl -sLO https://raw.githubusercontent.com/borkdude/babashka/master/install
  chmod +x install
  ./install --dir "${BIN_PATH}" --version 0.2.3
fi
bb --version
