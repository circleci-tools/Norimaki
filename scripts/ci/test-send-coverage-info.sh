#!/usr/bin/env bash
set -euo pipefail

uploader=scripts/ci/send-coverage-info.sh
stub_dir=$(mktemp -d "${PWD}/.coverage-upload-test.XXXXXX")
trap 'rm -rf "$stub_dir"' EXIT

cat > "$stub_dir/curl" <<'STUB'
#!/usr/bin/env bash
set -eu
[[ "$#" == 4 && "$3" == -o ]]
echo 'Downloading mock uploader' >&2
cat > "$4" <<'UPLOADER'
#!/usr/bin/env bash
[[ "$CODECOV_TOKEN" == 'mock-coverage-secret' && "$#" == 5 &&
    "$1" == upload-coverage && "$2" == --fail-on-error &&
    "$3" == --disable-search && "$4" == --file &&
    "$5" == app/build/reports/coverage/test/debug/report.xml ]] || exit 1
[[ "${MOCK_UPLOAD_FAIL:-}" != 1 ]] || exit 1
echo 'Mock upload succeeded'
UPLOADER
STUB
cat > "$stub_dir/sha256sum" <<'STUB'
#!/usr/bin/env bash
[[ "$#" == 1 && "$1" == --check ]] || exit 1
read -r checksum uploader
[[ "$checksum" =~ ^[0-9a-f]{64}$ && -s "$uploader" && "${MOCK_CHECKSUM_FAIL:-}" != 1 ]]
STUB
chmod +x "$stub_dir/curl" "$stub_dir/sha256sum"
export PATH="$stub_dir:$PATH"

unset CODECOV_TOKEN
for token_state in unset empty; do
    if [[ "$token_state" == empty ]]; then
        export CODECOV_TOKEN=
    fi
    output=$(bash "$uploader" 2>&1)
    [[ "$output" == *'Skipping coverage upload'* && "$output" != *'Downloading mock uploader'* ]]
done

export CODECOV_TOKEN=mock-coverage-secret
output=$(bash -x "$uploader" 2>&1)
[[ "$output" == *'Mock upload succeeded'* && "$output" != *'mock-coverage-secret'* ]]

if MOCK_UPLOAD_FAIL=1 bash "$uploader" >/dev/null 2>&1; then
    echo 'Expected a failed upload to return a nonzero status.' >&2
    exit 1
fi

if output=$(MOCK_CHECKSUM_FAIL=1 bash "$uploader" 2>&1); then
    echo 'Expected an invalid checksum to return a nonzero status.' >&2
    exit 1
fi
[[ "$output" != *'Mock upload succeeded'* ]]
