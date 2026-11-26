# FAQ and troubleshooting

Common questions about using Norimaki, and the known failure modes behind each error message the
app shows.

## Signing in

### "Could not verify this API token."

Norimaki sends the token to CircleCI before saving it and shows this message when CircleCI rejects
it. Confirm the token is still valid in CircleCI's [Personal API
Tokens](https://circleci.com/docs/guides/toolkit/managing-api-tokens/#create-a-personal-api-token)
settings, check your network connection, then try again. To enter a different token later, open the
build list's overflow menu and select **Change API Token**.

### Norimaki asks for my token again after I restored a backup or switched devices

This is expected: Norimaki excludes the saved token from Android backups and device transfers, so
it is never written to a backup in the first place. Enter your token again on the new device or
after a restore.

## Projects and builds

### "Could not load projects." / "Could not load the build details."

These appear when the request to CircleCI fails, for example due to a network error. Tap **Retry**
to try again; no other action is needed.

### "Could not load the build log."

The log request itself failed; tap **Retry** on the step to try again.

## Test results

### "Could not load test results." / "Test results are incomplete. Some pages could not be loaded."

Norimaki paginates through CircleCI's test results API for a job. The first error means the initial
page failed; tap **Retry** to fetch it again. The second means some pages loaded but a later page
did not — tapping **Retry** resumes from the next page instead of starting over, and the results
already shown remain visible.

### "No test results are available for this job."

CircleCI has no recorded test results for that job (for example, the job did not run a test
runner CircleCI recognizes). This is not an error.

## Artifacts

### "Could not load artifacts." / "No artifacts for this job."

The first means the artifact list request failed; tap **Retry**. The second means CircleCI
returned no artifacts for that job.

### "Could not save the artifact."

The download or write to device storage failed; tap the artifact again to retry.

### "An artifact from another job is being saved."

Norimaki downloads one artifact at a time. Wait for the current download to finish, then tap the
artifact again.

## Rebuilding a build

### "Could not rebuild. Try again."

The rebuild request to CircleCI failed; use **Clear cache and rebuild** or the plain rebuild action
again from the build details screen.

### What does "Clear project cache and rebuild" actually clear?

It clears CircleCI's build cache for the entire project the build belongs to, not just the one
build, before retrying it. Other builds in that project can be affected even if the retry itself
fails, so only use it when you specifically need a clean cache.

## Workflow approvals

### "CircleCI denied approval. Check your token and approval permissions."

CircleCI rejected the approval request with an HTTP 401 or 403. The token itself may be invalid, or
the account it belongs to may lack permission to approve jobs in that project.

### "This job is no longer waiting for approval."

Someone else (or another client) already approved or resolved the job between when Norimaki loaded
the workflow and when you tapped approve. Refresh the workflow to see the current state.

### "Could not confirm approval. Refresh to check the job status."

CircleCI accepted or rejected the request, but Norimaki could not confirm which; manually refresh
the workflow to check whether the job actually moved out of the held state.

## Where to go next

See [Getting started](getting-started.md) for the first-run walkthrough, or
[Architecture](architecture.md) if the fix for your issue requires a code change.
