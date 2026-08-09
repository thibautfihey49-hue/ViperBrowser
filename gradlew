#!/bin/sh
APP_HOME=$( cd "$( dirname "$0" )" && pwd )
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
if [ -n "$JAVA_HOME" ] ; then
    CMD="$JAVA_HOME/bin/java"
else
    CMD="java"
fi
exec "$CMD" -Xmx64m -Xms64m -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
