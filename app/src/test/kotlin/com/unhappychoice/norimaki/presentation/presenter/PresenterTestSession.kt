package com.unhappychoice.norimaki.presentation.presenter

import android.content.Context
import android.view.View
import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Single
import mortar.MortarScope
import mortar.bundler.BundleServiceRunner

internal fun testSession(api: CircleCIAPIClientV1_1) = SessionService(
    readToken = { "test-token" },
    writeToken = {},
    createClient = { api },
    verify = { Single.just(mockk()) }
)

internal fun <T : View> PresenterNeedsToken<T>.attachToTestScope(view: T): MortarScope {
    val runner = BundleServiceRunner()
    val scope = MortarScope.buildRootScope()
        .withService(BundleServiceRunner.SERVICE_NAME, runner)
        .build("test")
    val context = mockk<Context> {
        every { getSystemService(any()) } answers { scope.getService<Any>(firstArg()) }
    }
    every { view.context } returns context
    takeView(view)
    return scope
}
