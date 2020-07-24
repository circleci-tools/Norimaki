package com.unhappychoice.norimaki.presentation.presenter

import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.goTo
import com.unhappychoice.norimaki.infrastructure.preference.APITokenPreference
import com.unhappychoice.norimaki.presentation.presenter.core.Presenter
import com.unhappychoice.norimaki.presentation.screen.BuildListScreen
import com.unhappychoice.norimaki.presentation.view.APITokenView
import mortar.MortarScope
import org.kodein.di.instance

class APITokenPresenter: Presenter<APITokenView>() {
    val activity: MainActivity by instance()
    val token: Variable<String> = Variable("")

    override fun onEnterScope(scope: MortarScope?) {
        super.onEnterScope(scope)
        token.value = APITokenPreference(activity.applicationContext).token
    }

    fun saveToken() {
        APITokenPreference(activity).token = token.value
    }

    fun goToBuildList() = goTo(activity, BuildListScreen(""))
}