# Contributing to Norimaki

Please follow the [Code of Conduct](CODE_OF_CONDUCT.md) when contributing.

## Set up your development environment

Fork and clone the repository, then install JDK 21, Android SDK Platform 37.0, and Android SDK Build Tools 37.0.0. Use the Gradle Wrapper from the repository root.

To try a change on an Android device or emulator, follow the [source installation instructions](README.md#installation) and use the [quick start](README.md#quick-start) to connect to CircleCI.

See [Architecture](docs/architecture.md) for how the screens, presenters, and CircleCI clients fit together before making a change.

## Run the checks

Run the debug unit tests from the repository root.

```sh
./gradlew testDebugUnitTest
```

CircleCI runs the same unit tests with coverage. To reproduce that part of CI locally, run the following commands. The verification script requires Python 3.

```sh
./gradlew jacoco
python3 scripts/ci/test-check-unit-test-results.py
python3 scripts/ci/check-unit-test-results.py
python3 scripts/ci/test-check-line-coverage.py
python3 scripts/ci/check-line-coverage.py app/build/reports/coverage/test/debug/report.xml 89
```

The CircleCI test job reports overall line coverage and requires at least 89%.

## Open a pull request

1. Create a branch in your fork and keep the change focused on one issue or feature.
2. Add or update unit tests for behavior changes and run the relevant checks.
3. Push your branch to your fork and open a pull request against `circleci-tools/Norimaki`.
4. Describe the change and how you verified it. Link the issue if there is one, and include screenshots when the UI changes.
