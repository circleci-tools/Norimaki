package com.unhappychoice.norimaki.di.module.screen

import com.github.unhappychoice.circleci.response.Build
import com.unhappychoice.norimaki.presentation.core.scope.ViewScope
import dagger.Provides

@dagger.Module
class BuildScreenModule(val build: Build) {
    @Provides @ViewScope fun provideBuild(): Build = build
}
