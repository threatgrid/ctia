#!/bin/bash

set -ex

lein with-profile -dev do clean, javac, split-test :no-gen
