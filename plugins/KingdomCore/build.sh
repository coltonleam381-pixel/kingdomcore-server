#!/bin/bash
set -e

PROJ_DIR="/Users/markginzburg/Library/Application Support/feather/player-server/servers/88710d02-d592-4a10-8e8f-d57454884f95/plugins/KingdomCore"
JAVA21_HOME="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"

echo "Building via Gradle..."
cd "$PROJ_DIR"

# Force Gradle runtime to Java 21 (plugin target/toolchain)
export JAVA_HOME="$JAVA21_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

# Run gradle build - fail fast on any error
./gradlew clean build

if [ $? -ne 0 ]; then
    echo "ERROR: Gradle build failed"
    exit 1
fi

echo "Build successful"

# Copy to server plugins folder
SERVER_PLUGINS_DIR="/Users/markginzburg/Library/Application Support/feather/player-server/servers/88710d02-d592-4a10-8e8f-d57454884f95/plugins"
BUILD_JAR="$PROJ_DIR/build/libs/KingdomCore-1.0.0.jar"

if [ ! -f "$BUILD_JAR" ]; then
    echo "ERROR: Build JAR not found at $BUILD_JAR"
    exit 1
fi

cp "$BUILD_JAR" "$SERVER_PLUGINS_DIR/KingdomCore.jar"
echo ""
echo "===== BUILD COMPLETE ====="
echo "Copied to: $SERVER_PLUGINS_DIR/KingdomCore.jar"
ls -lh "$SERVER_PLUGINS_DIR/KingdomCore.jar"
echo "===== VERIFY PLUGIN ====="
jar tf "$SERVER_PLUGINS_DIR/KingdomCore.jar" | grep -E "plugin.yml|.*Service.*\.class$|.*Listener.*\.class$" | head -15
