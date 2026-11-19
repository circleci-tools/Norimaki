#!/usr/bin/env bash
set -euo pipefail

configured_version=$(sed -n 's/^[[:space:]]*versionName "\([0-9.]*\)"$/\1/p' app/build.gradle)
: "${configured_version:?Could not read the configured APK version name}"

CIRCLE_TAG="v${configured_version}" ./gradlew :app:validateReleaseTag

IFS=. read -r major minor patch <<< "$configured_version"
mismatched_tag="v${major}.${minor}.$((patch + 1))"
if output=$(CIRCLE_TAG="$mismatched_tag" ./gradlew :app:validateReleaseTag 2>&1); then
    echo "Expected validation to reject ${mismatched_tag}." >&2
    exit 1
fi

if [[ "$output" != *"Release tag '${mismatched_tag}' does not match APK version tag 'v${configured_version}'."* ]]; then
    printf '%s\n' "$output" >&2
    exit 1
fi
