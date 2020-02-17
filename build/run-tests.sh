#!/bin/bash

set -ex

# shellcheck disable=SC1010
lein do clean, javac, test :no-gen
