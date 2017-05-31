package com.unhappychoice.norimaki.presentation.view.core

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import io.reactivex.disposables.CompositeDisposable

abstract class BaseView(context: Context?, attr: AttributeSet?) : LinearLayout(context, attr) {
    protected val bag = CompositeDisposable()

    override fun onDetachedFromWindow() {
        bag.dispose()
        super.onDetachedFromWindow()
    }
}
