#! /bin/bash
set -eu

uploader_dir=$(mktemp -d)
trap 'rm -rf "$uploader_dir"' EXIT
uploader="$uploader_dir/codecov"

curl -fsSL https://github.com/codecov/codecov-cli/releases/download/v11.3.2/codecovcli_linux -o "$uploader"
printf '%s  %s\n' 8e1bfb71661378530f6ea21e82b414095899e565cbb4cf555c613064be17b973 "$uploader" |
    sha256sum --check
chmod +x "$uploader"

"$uploader" upload-coverage --disable-search --file app/build/reports/coverage/test/debug/report.xml -t 401f65aa-84da-46b2-b5a6-a39982425cd0
