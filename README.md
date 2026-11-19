# Norimaki
[![play store](https://raw.githubusercontent.com/circleci-tools/Norimaki/image/google-play-badge.png)](https://play.google.com/store/apps/details?id=com.unhappychoice.norimaki)

[![Version](https://img.shields.io/github/v/release/circleci-tools/Norimaki?display_name=tag&label=version&color=blue)](https://github.com/circleci-tools/Norimaki/releases/latest)
[![CircleCI](https://circleci.com/gh/circleci-tools/Norimaki.svg?style=shield)](https://circleci.com/gh/circleci-tools/Norimaki)
[![codecov](https://codecov.io/gh/circleci-tools/Norimaki/branch/master/graph/badge.svg)](https://codecov.io/gh/circleci-tools/Norimaki)
[![Libraries.io dependency status for GitHub repo](https://img.shields.io/librariesio/github/circleci-tools/Norimaki.svg)](https://libraries.io/github/circleci-tools/Norimaki)
![GitHub](https://img.shields.io/github/license/circleci-tools/Norimaki.svg)

![Norimaki](https://raw.githubusercontent.com/circleci-tools/Norimaki/image/logo.png)

Norimaki is a [CircleCI](https://circleci.com) client for Android.

## Quick start

1. Install Norimaki from [Google Play](https://play.google.com/store/apps/details?id=com.unhappychoice.norimaki).
2. Create a [personal API token](https://circleci.com/docs/guides/toolkit/managing-api-tokens/#create-a-personal-api-token) for your CircleCI account.
3. Open Norimaki, paste the token into the token field, and tap **Apply**.
4. Tap a build in **Recent Builds** to see its details.

## Running tests

With JDK 21, Android SDK Platform 37.0, and Android SDK Build Tools 37.0.0 installed, run the full unit test suite from the repository root:

```sh
./gradlew testDebugUnitTest
```

Run `python3 scripts/test-gradle-wrapper.py` to verify that the Wrapper rejects a ZIP with a mismatched checksum. This test uses a local ZIP and requires a JDK and Python 3.

## Release checklist

- When upgrading the Gradle Wrapper, update `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties` with the [official SHA-256 checksum](https://gradle.org/release-checksums/) for the exact distribution in `distributionUrl`.
- When updating `versionName` in `app/build.gradle`, review and update `app/src/main/play/release-notes/en-US/production.txt` with the user-facing changes for that version before publishing to Google Play.

## Author

[@unhappychoice](https://unhappychoice.com)

Logo, app icons are designed by [Carlos Liu](https://github.com/morita7453)

## Screenshot

<img src="app/src/main/play/listings/en-US/graphics/phone-screenshots/328936894989412098.png" alt="Norimaki showing a list of CircleCI builds on Android" width="300">
