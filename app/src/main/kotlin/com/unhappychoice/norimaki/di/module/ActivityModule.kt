package com.unhappychoice.norimaki.di.module

import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.presentation.core.scope.ActivityScope
import dagger.Provides

@dagger.Module
class ActivityModule(val activity: MainActivity) {
    @Provides @ActivityScope fun provideActivity() = activity
}