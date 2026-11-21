#!/usr/bin/env bash
set -euo pipefail

publish_command=$(awk '
    /^  deploy:$/ { deploy = 1; next }
    /^  [^ ]|^[^ ]/ { deploy = 0 }
    deploy && /publishReleaseApk/ { sub(/^ +command: /, ""); print }
' .circleci/config.yml)

if [[ "$publish_command" != "./gradlew publishReleaseApk --track internal" ]]; then
    echo "Expected the deploy job to publish explicitly to the internal Play track." >&2
    exit 1
fi

track=${publish_command##* }
notes_directory=app/src/main/play/release-notes/en-US
for notes_file in "$notes_directory/$track.txt" "$notes_directory/production.txt"; do
    if ! test -s "$notes_file" || ! grep -q '[^[:space:]]' "$notes_file"; then
        echo "Missing or empty Play release notes: $notes_file" >&2
        exit 1
    fi
done

listing_title=$(<app/src/main/play/listings/en-US/title.txt)
if [[ "$listing_title" != *Norimaki* || ${#listing_title} -gt 30 ]]; then
    echo "Expected the en-US Play title to contain Norimaki and be at most 30 characters." >&2
    exit 1
fi
