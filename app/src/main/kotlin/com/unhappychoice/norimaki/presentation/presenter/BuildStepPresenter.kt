package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.response.Build
import com.github.unhappychoice.circleci.response.BuildAction
import com.github.unhappychoice.circleci.response.BuildStep
import com.unhappychoice.norimaki.extension.*
import com.unhappychoice.norimaki.presentation.core.scope.ViewScope
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.view.BuildStepView
import io.reactivex.Observable
import io.reactivex.rxkotlin.addTo
import mortar.MortarScope
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import javax.inject.Inject

@ViewScope
class BuildStepPresenter @Inject constructor(
    val build: Build,
    val buildStep: BuildStep,
    val stepIndex: Int
) : PresenterNeedsToken<BuildStepView>() {
    val logString: Variable<String> = Variable("")

    override fun onEnterScope(scope: MortarScope?) {
        super.onEnterScope(scope)

        pusher.appendActionEvents(build)
            .filter { it.step == stepIndex }
            .subscribeNext { logString.value = logString.value + it.out.message.removeAnsiEscapeCode().replaceAnsiColorCodeToHtml() }
            .addTo(bag)

        getActions()
    }

    fun getActions() {
        val actions = buildStep.actions.filter { it.outputUrl != null }
        Observable.concat(actions.map { getAction(it) })
            .subscribeOnIoObserveOnUI()
            .subscribeNext { logString.value = logString.value + it.removeAnsiEscapeCode().replaceAnsiColorCodeToHtml() }
            .addTo(bag)
    }

    private fun getAction(action: BuildAction): Observable<String> =
        Observable.create { observer ->
            val client = OkHttpClient()
            val request = Request.Builder().url(action.outputUrl).build()
            val response = client.newCall(request).execute().body().string()
            val out = JSONArray(response).asSequence()
                .map { it.getString("message") }
                .reduce { acc, string -> acc + string }
            observer.onNext(out)
            observer.onComplete()
        }
}