#!/usr/bin/env bash
set -euo pipefail

workflow_jobs=$(awk '
    /^workflows:$/ { in_workflows = 1; next }
    in_workflows && /^[^ ]/ { in_workflows = 0 }
    in_workflows { print }
' .circleci/config.yml)

extract_invocation() {
    local job_name=$1
    awk -v job="      - ${job_name}:" '
        $0 == job { capture = 1; print; next }
        capture && /^      - / { capture = 0 }
        capture { print }
    ' <<< "$workflow_jobs"
}

build_invocation=$(extract_invocation build)
test_invocation=$(extract_invocation test)
deploy_invocation=$(extract_invocation deploy)

if grep -q '^ *context:' <<< "$build_invocation"; then
    echo "Expected the build job invocation not to request a context, so branch builds can't read release credentials." >&2
    exit 1
fi

if grep -q '^ *context:' <<< "$test_invocation"; then
    echo "Expected the test job invocation not to request a context, so branch builds can't read release credentials." >&2
    exit 1
fi

if ! grep -Eq '^ *context: +[A-Za-z0-9_.-]+$' <<< "$deploy_invocation"; then
    echo "Expected the deploy job invocation to request a restricted CircleCI context." >&2
    exit 1
fi

deploy_job=$(awk '
    /^  deploy:$/ { deploy = 1; next }
    /^  [^ ]|^[^ ]/ { deploy = 0 }
    deploy { print }
' .circleci/config.yml)

if ! grep -Fq 'scripts/ci/verify-release-credentials.sh' <<< "$deploy_job"; then
    echo "Expected the deploy job to verify release credentials before using them." >&2
    exit 1
fi

verify_line=$(grep -n 'Verify release credentials$' <<< "$deploy_job" | head -1 | cut -d: -f1)
publish_line=$(grep -n 'publishReleaseApk' <<< "$deploy_job" | head -1 | cut -d: -f1)
if [[ -z "$verify_line" || -z "$publish_line" || "$verify_line" -ge "$publish_line" ]]; then
    echo "Expected release credentials to be verified before publishing the APK." >&2
    exit 1
fi

release_checklist=$(sed -n '/^## Release checklist$/,/^## /p' README.md)

if ! grep -Fiq 'context' <<< "$release_checklist"; then
    echo "Expected the README release checklist to document the restricted CircleCI context." >&2
    exit 1
fi

if grep -Fiq "project's settings for the deploy job" <<< "$release_checklist"; then
    echo "Expected the README release checklist to stop directing maintainers to set release credentials in the CircleCI project's settings." >&2
    exit 1
fi

if ! grep -Fiq 'remove' <<< "$release_checklist"; then
    echo "Expected the README release checklist to document removing the project-level credential copies." >&2
    exit 1
fi
