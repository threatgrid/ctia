#!/bin/bash

set -Eeuxo pipefail

./scripts/generate-trojan-config.clj

trojansourcedetector -config .do_not_edit-trojansourcedetector.json
