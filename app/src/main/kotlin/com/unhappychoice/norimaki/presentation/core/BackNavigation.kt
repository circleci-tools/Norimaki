package com.unhappychoice.norimaki.presentation.core

import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import flow.Flow

internal fun Flow.goBackFromScreen(session: SessionService, isTokenScreenVisible: Boolean): Boolean {
    if (isTokenScreenVisible && !session.hasVerifiedSession && history.any { it !is APITokenScreen }) {
        return true
    }
    return goBack()
}
