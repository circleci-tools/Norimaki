package com.unhappychoice.norimaki.presentation.screen

import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.presentation.screen.core.Screen

class APITokenScreen : Screen() {
    override fun getTitle() = "Set api token"
    override fun getLayoutResource() = R.layout.api_token_view
}
