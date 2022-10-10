#!/bin/bash
set -e
lein with-profile +memory-test test ctia.task.migration.migrate-es-stores-memory-test
