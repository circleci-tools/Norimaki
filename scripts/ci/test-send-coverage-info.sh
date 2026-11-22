#!/usr/bin/env bash
set -euo pipefail

uploader=scripts/ci/send-coverage-info.sh
stub_dir=$(mktemp -d "${PWD}/.coverage-upload-test.XXXXXX")
trap 'rm -rf "$stub_dir"' EXIT

cat > "$stub_dir/curl" <<'STUB'
#!/usr/bin/env bash
echo 'Downloading mock uploader' >&2
cat <<'UPLOADER'
[[ "$CODECOV_TOKEN" == 'mock-coverage-secret' && "$#" == 1 && "$1" == '-Z' ]] || exit 1
[[ "${MOCK_UPLOAD_FAIL:-}" != 1 ]] || exit 1
echo 'Mock upload succeeded'
UPLOADER
STUB
chmod +x "$stub_dir/curl"
export PATH="$stub_dir:$PATH"

unset CODECOV_TOKEN
for token_state in unset empty; do
    if [[ "$token_state" == empty ]]; then
        export CODECOV_TOKEN=
    fi
    output=$(bash "$uploader" 2>&1)
    [[ "$output" == *'Skipping coverage upload'* && "$output" != *'Downloading mock uploader'* ]]
done

output=$(CODECOV_TOKEN=mock-coverage-secret bash -x "$uploader" 2>&1)
[[ "$output" == *'Mock upload succeeded'* && "$output" != *'mock-coverage-secret'* ]]

if MOCK_UPLOAD_FAIL=1 CODECOV_TOKEN=mock-coverage-secret bash "$uploader" >/dev/null 2>&1; then
    echo 'Expected a failed upload to return a nonzero status.' >&2
    exit 1
fi
