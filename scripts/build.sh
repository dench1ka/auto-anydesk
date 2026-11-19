#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17 installation}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK directory}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PWD/.gradle-user-home}"
# Keep debug signing state local; preserve this directory when updating an installation.
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$PWD/.tools/android-user}"
mkdir -p "$ANDROID_USER_HOME" artifacts
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest "$@"
cp app/build/outputs/apk/debug/app-debug.apk artifacts/AnyDeskAutoConfirm-1.0.0-debug.apk
python3 scripts/verify-project.py
