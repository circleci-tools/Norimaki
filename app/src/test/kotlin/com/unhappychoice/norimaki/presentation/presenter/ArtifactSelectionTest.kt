package com.unhappychoice.norimaki.presentation.presenter

import android.os.Bundle
import com.github.unhappychoice.circleci.v1.response.Artifact
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.service.VerifiedSession
import io.mockk.mockk
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtifactSelectionTest {
    @Test
    fun discardsSavedSelectionsMissingRequiredFields() {
        val buildId = BuildId("github", "team", "repo", 42)
        val artifact = Artifact(0, "reports/check.txt", "check.txt", "https://dl.circleci.com/file")
        val session = VerifiedSession("test-token", mockk(), mockk())
        val saved = ArtifactSelection(buildId, artifact, session).toBundle()

        listOf("vcsType", "owner", "repository", "url", "filename", "tokenDigest").forEach { field ->
            val incomplete = Bundle(saved).apply { remove(field) }
            assertNull("Missing $field", ArtifactSelection.restore(incomplete))
        }
    }
}
