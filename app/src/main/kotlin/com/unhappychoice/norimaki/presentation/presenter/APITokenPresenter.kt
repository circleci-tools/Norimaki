package com.unhappychoice.norimaki.presentation.presenter

import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.presentation.presenter.core.Presenter
import com.unhappychoice.norimaki.presentation.screen.BuildListScreen
import com.unhappychoice.norimaki.presentation.view.APITokenView
import com.unhappychoice.norimaki.presentation.viewmodel.APITokenViewModel
import com.unhappychoice.norimaki.presentation.viewmodel.TokenVerification
import flow.Direction
import flow.Flow
import org.kodein.di.instance

class APITokenPresenter : Presenter<APITokenView>() {
    private val activity: MainActivity by instance()
    private val session: SessionService by instance()

    val model: APITokenViewModel by lazy {
        val factory = viewModelFactory { initializer { APITokenViewModel(session) } }
        ViewModelProvider(activity, factory)[APITokenViewModel::class.java]
    }

    fun observeVerification(observer: Observer<TokenVerification>) {
        model.verification.observe(activity, observer)
    }

    fun goToBuildList() {
        model.reset()
        Flow.get(activity).replaceHistory(BuildListScreen(null), Direction.REPLACE)
    }
}
