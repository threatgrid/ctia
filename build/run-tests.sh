#!/bin/bash
set -ex
lein do clean, javac, test :no-gen
