package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.response.Build
import com.github.unhappychoice.circleci.response.BuildAction
import com.github.unhappychoice.circleci.response.BuildStep
import com.unhappychoice.norimaki.domain.model.step
import com.unhappychoice.norimaki.extension.*
import com.unhappychoice.norimaki.presentation.presenter.core.PresenterNeedsToken
import com.unhappychoice.norimaki.presentation.view.BuildStepView
import io.reactivex.Observable
import io.reactivex.rxkotlin.addTo
import mortar.MortarScope
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.kodein.di.instance

class BuildStepPresenter: PresenterNeedsToken<BuildStepView>() {
    val build: Build by instance()
    val buildStep: BuildStep by instance()

    val logString: Variable<String> = Variable("")

    override fun onEnterScope(scope: MortarScope?) {
        super.onEnterScope(scope)

        pusher.appendActionEvents(build)
            .filter { it.step == buildStep.step() }
            .map { it.out.message.removeAnsiEscapeCode().replaceAnsiColorCodeToHtml() }
            .subscribeOnIoObserveOnUI()
            .subscribeNext { logString.value = logString.value + it }
            .addTo(bag)

        getActions()
    }

    fun getActions() {
        val actions = buildStep.actions.filter { it.outputUrl != null }
        Observable.concat(actions.map { getAction(it) })
            .map { it.removeAnsiEscapeCode().replaceAnsiColorCodeToHtml() }
            .subscribeOnIoObserveOnUI()
            .subscribeNext { logString.value = logString.value + it }
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