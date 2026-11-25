# Changelog

Notable changes to Norimaki, newest first. See the [GitHub releases](https://github.com/circleci-tools/Norimaki/releases) for the full generated changelog, including dependency updates. Releases before v1.1.2 do not have recorded release notes.

## [1.2.0] - 2026-11-07

### Added

- Two-pane tablet layout ([#486](https://github.com/circleci-tools/Norimaki/pull/486))
- Workflow and job names, and a list of jobs in a workflow ([#487](https://github.com/circleci-tools/Norimaki/pull/487))
- Animated screen transitions ([#485](https://github.com/circleci-tools/Norimaki/pull/485))

### Changed

- Replaced Pusher with polling for realtime build updates ([#483](https://github.com/circleci-tools/Norimaki/pull/483))
- Migrated the CircleCI config to version 2.1 ([#479](https://github.com/circleci-tools/Norimaki/pull/479))

### Fixed

- Handled the system back gesture/button through `OnBackPressedDispatcher` ([#490](https://github.com/circleci-tools/Norimaki/pull/490))

[1.2.0]: https://github.com/circleci-tools/Norimaki/compare/v1.1.2...v1.2.0

## [1.1.2] - 2026-09-12

### Added

- Projects: view builds for a specific project ([#39](https://github.com/circleci-tools/Norimaki/pull/39))

### Changed

- Migrated from the CircleCI API v1 to v1.1 ([#416](https://github.com/circleci-tools/Norimaki/pull/416))
- Raised `minSdk` to 23 ([#434](https://github.com/circleci-tools/Norimaki/pull/434))
- Raised `targetSdk` to 36 for Play Store publishing ([#470](https://github.com/circleci-tools/Norimaki/pull/470))

### Fixed

- Fixed the Pusher real-time updates not working ([#33](https://github.com/circleci-tools/Norimaki/pull/33))
- Fixed timezone offsets not being considered properly ([#35](https://github.com/circleci-tools/Norimaki/pull/35))
- Fixed build failures with AGP 9.0 and Gradle 9.x ([#408](https://github.com/circleci-tools/Norimaki/pull/408))

[1.1.2]: https://github.com/circleci-tools/Norimaki/compare/v1.0.2...v1.1.2
