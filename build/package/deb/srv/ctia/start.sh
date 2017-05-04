#!/bin/bash

# ***********************************************
# The following environment-specific settings vary
# between deployments, and are required in order
# to run the CTIA service:
#
# CTIA_MAX_MEMORY
# CTIA_HEADLESS
# CTIA_LOG_LEVEL
# CTIA_LOGFILE

# Load the deployment-specific env variables
source ./environment

# ***********************************************
# These are package-specific settings and do not
# vary between deployment environments.  These
# values should NOT be changed:

MAIN_CLASS=ctia.main
BIN_DIR=/srv/ctia
CTIA_JAR=/srv/ctia/ctia.jar
CLASSPATH=$BIN_DIR:$CTIA_JAR
LANG=en_US.UTF-8

JVM_ARGS="-Xmx$CTIA_MAX_MEMORY -Djava.awt.headless=$CTIA_HEADLESS -Dlog.console.threshold=$CTIA_LOG_LEVEL"

exec java $JVM_ARGS -cp $CLASSPATH $MAIN_CLASS 2>&1 >> $CTIA_LOGFILE
