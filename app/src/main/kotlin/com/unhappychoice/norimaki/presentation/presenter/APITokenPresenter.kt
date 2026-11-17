package com.unhappychoice.norimaki.presentation.presenter

import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.presentation.presenter.core.Presenter
import com.unhappychoice.norimaki.presentation.screen.BuildListScreen
import com.unhappychoice.norimaki.presentation.view.APITokenView
import flow.Direction
import flow.Flow
import org.kodein.di.instance

class APITokenPresenter : Presenter<APITokenView>() {
    private val activity: MainActivity by instance()
    private val session: SessionService by instance()

    val savedToken: String get() = session.savedToken

    fun verifyToken(token: String) = session.replace(token)

    fun goToBuildList() {
        Flow.get(activity).replaceHistory(BuildListScreen(""), Direction.REPLACE)
    }
}
