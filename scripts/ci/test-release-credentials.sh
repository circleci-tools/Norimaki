#!/usr/bin/env bash
set -euo pipefail

mkdir -p build
test_dir=$(mktemp -d "$PWD/build/release-credentials-test.XXXXXXXX")
trap 'rm -rf "$test_dir"' EXIT
mkdir "$test_dir/tmp"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Djava.io.tmpdir=$test_dir/tmp"

export RELEASE_KEYSTORE_KEY_ALIAS=test
export RELEASE_KEYSTORE_PASSWORD=test-password
export RELEASE_KEYSTORE_KEY_PASSWORD=$RELEASE_KEYSTORE_PASSWORD
keytool -genkeypair -storetype JKS -keystore "$test_dir/test.jks" \
    -alias "$RELEASE_KEYSTORE_KEY_ALIAS" -storepass "$RELEASE_KEYSTORE_PASSWORD" \
    -keypass "$RELEASE_KEYSTORE_KEY_PASSWORD" -keyalg RSA -dname CN=Test -validity 1 \
    >"$test_dir/keytool.log" 2>&1
export RELEASE_KEYSTORE_BASE64=$(base64 <"$test_dir/test.jks" | tr -d '\n')
export PLAY_ACCOUNT_JSON_BASE64=e30=

cat >"$test_dir/assertions.gradle" <<'GRADLE'
def credentials = {
    new File(System.getProperty('java.io.tmpdir')).listFiles().findAll {
        it.name.startsWith('norimaki-release-')
    }
}
gradle.rootProject {
    tasks.matching { it.name == 'help' }.configureEach {
        doLast {
            assert credentials().empty
        }
    }
}
gradle.beforeProject { project ->
    if (project.path == ':app') {
        project.tasks.matching { it.name == 'validateSigningRelease' }.configureEach {
            doLast {
                assert credentials().size() == 1
                def directory = credentials().first()
                assert directory.list().toList() == ['release.jks']
                assert new File(directory, 'release.jks').bytes ==
                    System.getenv('RELEASE_KEYSTORE_BASE64').decodeBase64()
                if (directory.toPath().fileSystem.supportedFileAttributeViews().contains('posix')) {
                    assert java.nio.file.attribute.PosixFilePermissions.toString(
                        java.nio.file.Files.getPosixFilePermissions(directory.toPath())) == 'rwx------'
                }
            }
        }
    }
}
GRADLE

assert_clean() {
    local leftovers
    leftovers=$(find "$test_dir/tmp" \( -name 'norimaki-release-*' -o -name 'tmp_ks_*.jks' -o -name 'norimaki*.json' \) -print)
    if [[ -n "$leftovers" ]]; then
        printf 'Decoded credentials or credential directories remain:\n%s\n' "$leftovers" >&2
        exit 1
    fi
}

gradle=(./gradlew --daemon --init-script "$test_dir/assertions.gradle")
"${gradle[@]}" help
assert_clean
"${gradle[@]}" :app:validateSigningRelease :app:signingReport >"$test_dir/signing.log" 2>&1 ||
    { cat "$test_dir/signing.log" >&2; exit 1; }
if ! sed -n '/^Variant: release$/,/^----------/p' "$test_dir/signing.log" | grep -q '^SHA-256: '; then
    cat "$test_dir/signing.log" >&2
    exit 1
fi
assert_clean

# The Play plugin must open the decoded JSON and reject it before making a network request.
mkdir "$test_dir/artifacts"
touch "$test_dir/artifacts/test.apk"
if "${gradle[@]}" :app:validateSigningRelease :app:publishReleaseApk --artifact-dir "$test_dir/artifacts" \
    --stacktrace >"$test_dir/play.log" 2>&1 ||
    ! grep -Fq "'type' field not specified" "$test_dir/play.log"; then
    cat "$test_dir/play.log" >&2
    exit 1
fi
assert_clean
