# Architecture

Norimaki is a single-module Android app (`app/`) built with Kotlin. This page describes how its
main parts fit together so contributors know where to start reading.

## Layers

The source under `app/src/main/kotlin/com/unhappychoice/norimaki` is split into three packages:

- `domain` — API-agnostic models (`BuildId`, `BuildStep`, `WorkflowJob`, ...) and services
  (`SessionService`, `ArtifactDownloader`, `EventBusService`, ...) that hold app-wide state and
  business rules. Domain code does not depend on Android views.
- `infrastructure` — clients that talk to CircleCI's HTTP APIs directly, such as `WorkflowClient`
  (workflow jobs and approvals, CircleCI API v2), `TestResultsClient`, and `FlakyTestsClient`
  (project-level flaky-test insights, CircleCI API v2). Most other CircleCI
  calls (builds, rebuilds, artifacts) go through the `com.github.unhappychoice:circleci` library
  via `CircleCIAPIClientV1_1`, which `SessionService` creates and holds per verified session.
- `presentation` — screens, views, and presenters, described below.

## Navigation: Screen, View, Presenter

Norimaki uses [Square Flow](https://github.com/square/flow) for navigation and
[Mortar](https://github.com/square/mortar) for view/presenter lifecycle, with
[Kodein](https://kodein.org/Kodein-DI/) for dependency injection. Each screen is a triple:

- A `Screen` (`presentation/screen/*Screen.kt`) is a Flow navigation key: it carries the
  arguments for the screen (for example `BuildScreen(buildId)`), names its layout resource and
  title, and can extend the activity's Kodein `DI` module with screen-scoped bindings
  (`Screen.module()`).
- A `View` (`presentation/view/*View.kt`) is the Android `ViewGroup` for that screen, extending
  `BaseView`. It owns a `Presenter` and renders what the presenter exposes.
- A `Presenter` (`presentation/presenter/*Presenter.kt`) extends Mortar's `ViewPresenter` through
  the `Presenter` base class and holds the screen's logic: it loads data, exposes RxJava streams
  or `Variable`s for the view to subscribe to, and calls `goTo(activity, SomeScreen(...))` to
  navigate.

`MainActivity` installs Flow with a `ScreenChanger` (`presentation/core/ScreenChanger.kt`) as the
`KeyChanger`. On every navigation, `ScreenChanger` inflates the incoming screen's layout, builds
its Kodein module from the screen (extending the activity module), injects that module into the
view and its presenter, and animates the transition (`ScreenTransition`).

Presenters that require a signed-in CircleCI session extend `PresenterNeedsToken`
(`presentation/presenter/core/PresenterNeedsToken.kt`) instead of `Presenter` directly. It
restores the session via `SessionService`, calls the subclass's `onAuthenticated()` once a
`VerifiedSession` is available, redirects to `APITokenScreen` if restoration fails, and calls
`onSessionEnded()` if the session is replaced or cleared while the presenter is active. Mixins
such as `Loadable`, `Paginatable`, and `StartedPolling` add shared behavior (pull-to-refresh,
pagination, foreground polling) to presenters that need it.

## Dependency injection scopes

Kodein modules are layered, matching the Mortar scopes:

- `applicationModule` (`di/ApplicationModule.kt`), built once in `NorimakiApplication`, binds
  app-wide singletons: `SessionService`, `EventBusService`, `ArtifactDownloader`, and a shared
  `Gson` instance configured for the CircleCI API's JSON conventions.
- `activityModule` (`di/ActivityModule.kt`), built once in `MainActivity` and extending the
  application module, binds the activity instance and most presenters (singleton for
  `APITokenPresenter`, provider for the rest, so each navigation gets a fresh presenter). Presenters
  bound here that need the current screen's arguments (for example `BuildPresenter`'s `BuildId`)
  read them with `by instance()`, which resolves against the screen module below even though the
  `bind<...>()` call itself lives in `activityModule`.
- Each `Screen.module()` extends the activity module with screen-scoped bindings, most often the
  screen's own arguments (project name, build ID, workflow ID, ...) bound via `instance()`.
- A couple of presenters are bound in their own `Screen.module()` instead of `activityModule`:
  `WorkflowScreen` binds `WorkflowPresenter` there because the presenter's constructor takes the
  screen instance itself (`WorkflowPresenter(this@WorkflowScreen)`), which only `this@WorkflowScreen`
  can supply; `TestResultsScreen` binds `TestResultsPresenter` the same way even though it only
  needs `BuildId` via `by instance()`. When adding a new presenter, bind it in `activityModule`
  unless it needs the screen instance itself, in which case bind it in that screen's `module()`.

## Session and authentication

`SessionService` (`domain/service/SessionService.kt`) owns the current CircleCI session as a
`BehaviorSubject<Optional<VerifiedSession>>`. `restore()` re-authenticates the token saved on
device at app start; `replace(token)` authenticates a newly entered token (used by
`APITokenPresenter`) and persists it only once verification against CircleCI's `/me` endpoint
succeeds. A `VerifiedSession` holds the authenticated `CircleCIAPIClientV1_1` and tracks
in-flight rebuilds and per-workflow approval state (`WorkflowApprovals`) so presenters across
screens share the same state for a build or workflow.

## Where to start reading

- `MainActivity.kt` and `ScreenChanger.kt` for how navigation and DI wiring fit together.
- `presentation/presenter/core/PresenterNeedsToken.kt` for the authenticated-presenter lifecycle
  that most screens build on.
- `presentation/presenter/BuildListPresenter.kt` for a presenter that combines pagination,
  polling, and branch filtering — the most feature-complete example in the app.
- `domain/service/SessionService.kt` and `infrastructure/workflow/WorkflowClient.kt` for how the
  app talks to CircleCI.
