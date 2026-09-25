#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if ! command -v java >/dev/null 2>&1; then
  echo "Java 17 is missing. Install JDK 17 and set JAVA_HOME." >&2
  exit 1
fi
java_version="$(java -version 2>&1 | head -n 1)"
if [[ ! "$java_version" =~ \"17([.\"]|$) ]]; then
  echo "Expected Java 17, found: $java_version" >&2
  exit 1
fi

sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk_root" || ! -d "$sdk_root" ]]; then
  echo "Android SDK missing. Set ANDROID_HOME to your Android SDK directory." >&2
  exit 1
fi
if [[ ! -f "$sdk_root/platforms/android-35/android.jar" ]]; then
  echo "Install Android SDK platform 35 using sdkmanager 'platforms;android-35'." >&2
  exit 1
fi
if [[ ! -d "$sdk_root/build-tools/35.0.0" ]]; then
  echo "Install Android Build Tools 35.0.0 using sdkmanager 'build-tools;35.0.0'." >&2
  exit 1
fi
if [[ ! -f "$project_root/gradle/wrapper/gradle-wrapper.jar" || ! -x "$project_root/gradlew" ]]; then
  echo "Gradle wrapper is incomplete. Restore gradlew and gradle/wrapper from Git." >&2
  exit 1
fi
if ! grep -q '^distributionSha256Sum=' "$project_root/gradle/wrapper/gradle-wrapper.properties"; then
  echo "Gradle distribution checksum is not pinned." >&2
  exit 1
fi
echo "Java 17, Android SDK 35, Build Tools 35.0.0, and verified Gradle wrapper: ready."
