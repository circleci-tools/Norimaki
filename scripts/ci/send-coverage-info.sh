#!/bin/bash
set +x
set -eu

: "${CODECOV_TOKEN:?Set CODECOV_TOKEN in CircleCI project environment variables}"

uploader=$(curl -fsSL https://codecov.io/bash)
bash -s -- -Z <<< "$uploader"
