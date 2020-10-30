#!/usr/bin/env bash

set -ex

${SHELLCHECK_COMMAND?shellcheck} -S style build/*.sh
