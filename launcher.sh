#!/bin/bash
# SeeLoggy+ Launcher for Linux/Mac
# This script reads configuration from launcher.properties and starts the application

# Default values
DEFAULT_MEM=4
MAX_MEM=$DEFAULT_MEM
CUSTOM_JAVA_HOME=""

# Config file path
CONFIG_FILE="launcher.properties"

# Read configuration from properties file if it exists
if [ -f "$CONFIG_FILE" ]; then
    while IFS='=' read -r key value; do
        # Skip comments and empty lines
        [[ "$key" =~ ^#.*$ ]] && continue
        [[ -z "$key" ]] && continue
        
        # Trim whitespace
        key=$(echo "$key" | xargs)
        value=$(echo "$value" | xargs)
        
        if [ "$key" = "max.memory.gb" ]; then
            MAX_MEM=$value
        elif [ "$key" = "java.home" ]; then
            CUSTOM_JAVA_HOME=$value
        fi
    done < "$CONFIG_FILE"
fi

# Determine which Java to use
JAVA_CMD="java"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ -n "$CUSTOM_JAVA_HOME" ]; then
    # Use custom Java path from config
    JAVA_CMD="$CUSTOM_JAVA_HOME/bin/java"
    echo "Using custom Java: $JAVA_CMD"
elif [ -d "$SCRIPT_DIR/jre" ]; then
    # Use bundled JRE
    JAVA_CMD="$SCRIPT_DIR/jre/bin/java"
    echo "Using bundled JRE: $JAVA_CMD"
elif [ -n "$JAVA_HOME" ]; then
    # Use JAVA_HOME environment variable
    JAVA_CMD="$JAVA_HOME/bin/java"
    echo "Using JAVA_HOME: $JAVA_HOME"
else
    # Use java from PATH
    echo "Using Java from PATH"
fi

echo "Starting SeeLoggy+ with ${MAX_MEM}GB max memory..."

# Launch application (JavaFX modules are already bundled in fat JAR)
"$JAVA_CMD" -Xmx${MAX_MEM}g -jar seeloggyplus.jar
