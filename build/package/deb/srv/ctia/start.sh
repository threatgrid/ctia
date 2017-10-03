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
# CTIA_JMX_PORT

# Load the deployment-specific env variables
source /srv/ctia/environment

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
JMX_ARGS="-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=$CTIA_JMX_PORT -Dcom.sun.management.jmxremote.local.only=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"

exec java $JVM_ARGS $JMX_ARGS -cp $CLASSPATH $MAIN_CLASS 2>&1 >> $CTIA_LOGFILE

