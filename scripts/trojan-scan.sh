#!/bin/bash

set -Eeuxo pipefail

tsfinder -v -e scripts/trojansourcefinder_excludes_file.txt .
