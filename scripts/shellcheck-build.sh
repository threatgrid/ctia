#!/usr/bin/env bash

set -ex

shellcheck -S style build/*.sh
