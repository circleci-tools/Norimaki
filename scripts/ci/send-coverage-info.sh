#! /bin/bash
set -eu
set +x

: "${CODECOV_TOKEN:?Set CODECOV_TOKEN in the CircleCI environment}"

uploader_dir=$(mktemp -d)
trap 'rm -rf "$uploader_dir"' EXIT
uploader="$uploader_dir/codecov"

curl -fsSL https://github.com/codecov/codecov-cli/releases/download/v11.3.2/codecovcli_linux -o "$uploader"
printf '%s  %s\n' 8e1bfb71661378530f6ea21e82b414095899e565cbb4cf555c613064be17b973 "$uploader" |
    sha256sum --check
chmod +x "$uploader"

"$uploader" upload-coverage --fail-on-error --disable-search --file app/build/reports/coverage/test/debug/report.xml
