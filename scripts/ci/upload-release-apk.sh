#! /bin/bash
# Uploads the release APK to the GitHub Release of $CIRCLE_TAG, creating the release if needed.
set -euo pipefail

: "${GITHUB_TOKEN:?GITHUB_TOKEN must be set}"
: "${CIRCLE_TAG:?CIRCLE_TAG must be set}"

REPOSITORY="${CIRCLE_PROJECT_USERNAME}/${CIRCLE_PROJECT_REPONAME}"
API_URL="https://api.github.com/repos/${REPOSITORY}"
UPLOAD_URL="https://uploads.github.com/repos/${REPOSITORY}"
APK_DIR="app/build/outputs/apk/release"

main() {
  local apk name release_id
  apk="$(find_apk)"
  name="$(asset_name "$apk")"
  release_id="$(find_release || create_release)"
  delete_asset "$release_id" "$name"
  upload_asset "$release_id" "$apk" "$name"
  echo "Uploaded ${name} to release ${CIRCLE_TAG}"
}

github() {
  curl -fsSL \
    -H "Authorization: Bearer ${GITHUB_TOKEN}" \
    -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "$@"
}

find_apk() {
  local file
  for file in app-release.apk app-release-unsigned.apk; do
    [ -f "${APK_DIR}/${file}" ] && echo "${APK_DIR}/${file}" && return
  done
  echo "No release APK found in ${APK_DIR}" >&2
  return 1
}

asset_name() {
  case "$1" in
    *-unsigned.apk) echo "norimaki-${CIRCLE_TAG}-unsigned.apk" ;;
    *) echo "norimaki-${CIRCLE_TAG}.apk" ;;
  esac
}

find_release() {
  github "${API_URL}/releases/tags/${CIRCLE_TAG}" 2>/dev/null | jq -er '.id'
}

create_release() {
  jq -n --arg tag "$CIRCLE_TAG" '{tag_name: $tag, name: $tag, generate_release_notes: true}' |
    github -X POST "${API_URL}/releases" --data @- | jq -er '.id'
}

delete_asset() {
  github "${API_URL}/releases/$1/assets?per_page=100" |
    jq -r --arg name "$2" '.[] | select(.name == $name) | .id' |
    xargs -r -I {} curl -fsSL -X DELETE \
      -H "Authorization: Bearer ${GITHUB_TOKEN}" \
      "${API_URL}/releases/assets/{}" > /dev/null
}

upload_asset() {
  github -X POST \
    -H "Content-Type: application/vnd.android.package-archive" \
    --data-binary "@$2" \
    "${UPLOAD_URL}/releases/$1/assets?name=$3" > /dev/null
}

main
