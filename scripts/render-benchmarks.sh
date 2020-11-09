#!/bin/sh

set -xe

lein with-profile render-benchmarks-script run -m ctia.dev.render-benchmarks
