#!/bin/bash

set -e

# for visibility inside this script
BIN_PATH="${HOME}/bin"
LOG_PATH="${HOME}/log"

echo "BIN_PATH=${BIN_PATH}" >> $GITHUB_ENV
echo "LOG_PATH=${LOG_PATH}" >> $GITHUB_ENV

echo "Setup PATH and directories"

mkdir -p "${BIN_PATH}"
echo "${BIN_PATH}" >> $GITHUB_PATH

mkdir -p "${LOG_PATH}"
echo "${BIN_PATH}" >> $GITHUB_PATH

if [[ "$GITHUB_EVENT_NAME" == "schedule" || "$TRAVIS_EVENT_TYPE" == "cron" ]]; then
  echo "CTIA_TEST_SUITE=cron" >> $GITHUB_ENV
else
  echo "CTIA_TEST_SUITE=ci" >> $GITHUB_ENV
fi
