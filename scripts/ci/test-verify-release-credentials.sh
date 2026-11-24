#!/usr/bin/env bash
set -euo pipefail

script=scripts/ci/verify-release-credentials.sh
variables=(RELEASE_KEYSTORE_BASE64 RELEASE_KEYSTORE_PASSWORD RELEASE_KEYSTORE_KEY_ALIAS
    RELEASE_KEYSTORE_KEY_PASSWORD PLAY_ACCOUNT_JSON_BASE64)

for variable in "${variables[@]}"; do
    unset "$variable"
done

if output=$(bash "$script" 2>&1); then
    echo "Expected verification to fail when no release credentials are set." >&2
    exit 1
fi
for variable in "${variables[@]}"; do
    if [[ "$output" != *"$variable"* ]]; then
        echo "Expected the missing-credentials message to name $variable." >&2
        exit 1
    fi
done

for variable in "${variables[@]}"; do
    export "$variable=secret-value-for-$variable"
done

missing_variable=${variables[2]}
unset "$missing_variable"
if output=$(bash "$script" 2>&1); then
    echo "Expected verification to fail when $missing_variable is missing." >&2
    exit 1
fi
if [[ "$output" != *"$missing_variable"* ]]; then
    echo "Expected the missing-credentials message to name $missing_variable." >&2
    exit 1
fi
export "$missing_variable=secret-value-for-$missing_variable"

if ! output=$(bash -x "$script" 2>&1); then
    echo "Expected verification to succeed when all release credentials are set." >&2
    exit 1
fi
for variable in "${variables[@]}"; do
    if [[ "$output" == *"secret-value-for-$variable"* ]]; then
        echo "Verification leaked the value of $variable." >&2
        exit 1
    fi
done
