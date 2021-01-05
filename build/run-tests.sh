#!/bin/bash

set -ex

# shellcheck disable=SC1010
lein with-profile -dev,+ci do clean, javac, split-test :no-gen
