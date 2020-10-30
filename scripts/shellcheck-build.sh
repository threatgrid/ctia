#!/usr/bin/env bash

set -ex

SHELLCHECK_COMMAND="${1:-shellcheck}"

$SHELLCHECK_COMMAND -S style build/*.sh
