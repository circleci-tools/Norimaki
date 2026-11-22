#!/bin/bash
set +x
set -eu

if [[ -z "${CODECOV_TOKEN:-}" ]]; then
    echo 'Skipping coverage upload. Set CODECOV_TOKEN in CircleCI project environment variables to enable it.' >&2
    exit 0
fi

uploader=$(curl -fsSL https://codecov.io/bash)
bash -s -- -Z <<< "$uploader"
