# Getting started with Norimaki

Use Norimaki to investigate a CircleCI build from your Android device. For this walkthrough, use a CircleCI account with access to a project that has already run a build.

## Install Norimaki

Install [Norimaki from Google Play](https://play.google.com/store/apps/details?id=com.unhappychoice.norimaki). The app requires Android 6.0 (API 23) or later. To try a local checkout on a device or emulator, follow the [source installation instructions](../README.md#installation).

## Connect your CircleCI account

1. Open **Personal API Tokens** in your CircleCI user settings and follow the [CircleCI instructions to create a personal API token](https://circleci.com/docs/guides/toolkit/managing-api-tokens/#create-a-personal-api-token). Copy the token when CircleCI displays it.
2. Open Norimaki, paste the token into the token field, and tap **Apply**. After CircleCI verifies the token, Norimaki opens **Recent Builds**.
3. If verification fails, check your network connection and confirm the token is still valid. If it has expired, create a new personal token. To update a saved token, select **Change API Token** from the build list's overflow menu.

## Inspect a build

1. In **Recent Builds**, open a build you want to inspect. To narrow the list to one project, open the navigation menu and select that project. Select **Recent Builds** there to return to the combined list.
2. In the build details, tap a step to read its log. If you're investigating a failed build, start with a step marked as failed.
3. Go back to the build details. If **Show workflow jobs** appears in the overflow menu, select it to see the other jobs in the same workflow.

## Next steps

See [FAQ and troubleshooting](faq.md) if something didn't work as expected, or
[Architecture](architecture.md) if you want to contribute a change and need an overview of the
codebase.
