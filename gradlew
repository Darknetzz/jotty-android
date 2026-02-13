#!/bin/sh
GRADLE_HOME=$(dirname "$0")/gradle/wrapper
exec java -jar "$GRADLE_HOME/gradle-wrapper.jar" "$@"
