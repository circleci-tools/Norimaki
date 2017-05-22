package com.unhappychoice.norimaki.presentation.screen

import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.di.component.ActivityComponent
import com.unhappychoice.norimaki.di.module.screen.APITokenScreenModule
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.goTo
import com.unhappychoice.norimaki.infrastructure.preference.APITokenPreference
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import com.unhappychoice.norimaki.presentation.view.APITokenView
import com.unhappychoice.norimaki.presentation.core.scope.ViewScope
import dagger.Subcomponent
import mortar.MortarScope
import mortar.ViewPresenter
import javax.inject.Inject

class APITokenScreen : Screen() {
    override fun getTitle() = "Set api token"
    override fun getLayoutResource() = R.layout.api_token_view
    override fun getSubComponent(activityComponent: ActivityComponent) =
        activityComponent.apiTokenScreenComponent(APITokenScreenModule())

    class Presenter(val activity: MainActivity) : ViewPresenter<APITokenView>() {
        val token: Variable<String> = Variable("")

        override fun onEnterScope(scope: MortarScope?) {
            super.onEnterScope(scope)
            token.value = APITokenPreference(activity.applicationContext).token
        }

        fun saveToken() {
            APITokenPreference(activity).token = token.value
        }

        fun goToBuildList() = goTo(activity, BuildListScreen())
    }
}
