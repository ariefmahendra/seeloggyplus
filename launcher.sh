#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

DEFAULT_MEM=4
MAX_MEM=$DEFAULT_MEM
CUSTOM_JAVA_HOME=""

CONFIG_FILE="launcher.properties"

if [ -f "$CONFIG_FILE" ]; then
    while IFS='=' read -r key value; do
        [[ "$key" =~ ^#.*$ ]] && continue
        [[ -z "$key" ]] && continue
        
        key=$(echo "$key" | xargs)
        value=$(echo "$value" | xargs)
        
        if [ "$key" = "max.memory.gb" ]; then
            MAX_MEM=$value
        elif [ "$key" = "java.home" ]; then
            CUSTOM_JAVA_HOME=$value
        fi
    done < "$CONFIG_FILE"
fi

JAVA_CMD="java"

if [ -n "$CUSTOM_JAVA_HOME" ]; then
    JAVA_CMD="$CUSTOM_JAVA_HOME/bin/java"
    echo "Using custom Java: $JAVA_CMD"
elif [ -d "$SCRIPT_DIR/jre" ]; then
    JAVA_CMD="$SCRIPT_DIR/jre/bin/java"
    echo "Using bundled JRE: $JAVA_CMD"
elif [ -n "$JAVA_HOME" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
    echo "Using JAVA_HOME: $JAVA_HOME"
else
    echo "Using Java from PATH"
fi

if [ "$1" = "--apply-update" ] && [ -n "$2" ]; then
    printf '%s' "$2" > "$SCRIPT_DIR/current"
    echo "Applied update version $2"
    shift 2
fi

APP_JAR="$SCRIPT_DIR/seeloggyplus.jar"
if [ -f "$SCRIPT_DIR/current" ]; then
    CURRENT_VER="$(cat "$SCRIPT_DIR/current")"
    if [ -f "$SCRIPT_DIR/versions/$CURRENT_VER/seeloggyplus.jar" ]; then
        APP_JAR="$SCRIPT_DIR/versions/$CURRENT_VER/seeloggyplus.jar"
    fi
fi

echo "Starting SeeLoggy+ with ${MAX_MEM}GB max memory..."

"$JAVA_CMD" -Xmx${MAX_MEM}g -jar "$APP_JAR" "$@"
