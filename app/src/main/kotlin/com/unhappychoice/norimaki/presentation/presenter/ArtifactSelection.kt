package com.unhappychoice.norimaki.presentation.presenter

import android.os.Bundle
import com.github.unhappychoice.circleci.v1.response.Artifact
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.service.VerifiedSession
import java.security.MessageDigest

internal class ArtifactSelection private constructor(
    val buildId: BuildId,
    val url: String,
    val filename: String,
    private val tokenDigest: ByteArray,
    private val session: VerifiedSession?
) {
    constructor(buildId: BuildId, artifact: Artifact, session: VerifiedSession) : this(
        buildId,
        artifact.url,
        artifact.path.substringAfterLast('/').ifBlank { "artifact" },
        digest(session.token),
        session
    )

    val isRestored: Boolean get() = session == null

    fun matches(verified: VerifiedSession): Boolean =
        session?.let { it === verified } ?: MessageDigest.isEqual(tokenDigest, digest(verified.token))

    fun toBundle() = Bundle().apply {
        putString("vcsType", buildId.vcsType)
        putString("owner", buildId.owner)
        putString("repository", buildId.repository)
        putInt("number", buildId.number)
        putString("url", url)
        putString("filename", filename)
        putByteArray("tokenDigest", tokenDigest)
    }

    companion object {
        fun restore(state: Bundle): ArtifactSelection? {
            val vcsType = state.getString("vcsType") ?: return null
            val owner = state.getString("owner") ?: return null
            val repository = state.getString("repository") ?: return null
            val url = state.getString("url") ?: return null
            val filename = state.getString("filename") ?: return null
            val tokenDigest = state.getByteArray("tokenDigest") ?: return null
            val buildId = BuildId(vcsType, owner, repository, state.getInt("number"))
            return ArtifactSelection(buildId, url, filename, tokenDigest, null)
        }

        private fun digest(token: String): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
    }
}
