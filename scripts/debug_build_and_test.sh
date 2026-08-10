#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_dir"

if [[ -z "${JAVA_HOME:-}" ]]; then
    bundled_jbr="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    if [[ -x "$bundled_jbr/bin/java" ]]; then
        export JAVA_HOME="$bundled_jbr"
    else
        echo "JAVA_HOME is unset and no bundled JBR was found; Java 17+ is required." >&2
        exit 1
    fi
fi

if [[ -z "${ANDROID_HOME:-}" ]]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
fi
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

./scripts/smoke_test.sh
./gradlew testDebugUnitTest lintDebug assembleDebug "$@"
