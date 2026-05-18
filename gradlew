#!/bin/sh
#
# Gradle start up script for UN*X
#
APP_HOME=$(dirname "$(readlink -f "$0" 2>/dev/null || echo "$0")")
APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

JAVA_EXE="java"

exec "$JAVA_EXE"  -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
