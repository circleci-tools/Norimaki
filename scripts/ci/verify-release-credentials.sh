#!/bin/bash
set +x
set -eu

missing=()
for variable in RELEASE_KEYSTORE_BASE64 RELEASE_KEYSTORE_PASSWORD RELEASE_KEYSTORE_KEY_ALIAS \
    RELEASE_KEYSTORE_KEY_PASSWORD PLAY_ACCOUNT_JSON_BASE64; do
    if [[ -z "${!variable:-}" ]]; then
        missing+=("$variable")
    fi
done

if [[ ${#missing[@]} -gt 0 ]]; then
    printf 'Missing release credential(s): %s\n' "${missing[*]}" >&2
    printf 'Confirm the deploy job runs with the restricted release CircleCI context attached.\n' >&2
    exit 1
fi

echo 'All release credentials are present.'
