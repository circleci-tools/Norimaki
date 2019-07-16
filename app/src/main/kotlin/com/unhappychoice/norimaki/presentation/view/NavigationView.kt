package com.unhappychoice.norimaki.presentation.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import com.bumptech.glide.Glide
import com.github.unhappychoice.circleci.CircleCIAPIClientV1
import com.google.android.material.navigation.NavigationView as AndroidNavigationView
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.extension.withLog
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.navigation_view.view.*
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.generic.instance

class NavigationView(context: Context?, attr: AttributeSet?): AndroidNavigationView(context, attr), KodeinAware {
    override lateinit var kodein: Kodein
    private val client: CircleCIAPIClientV1 by instance()
    private val bag = CompositeDisposable()

    init {
        LayoutInflater.from(context).inflate(R.layout.navigation_view, this, true)
    }

    @SuppressLint("RestrictedApi")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        client.getMe()
            .withLog()
            .subscribeOnIoObserveOnUI()
            .subscribeNext {
                Glide.with(context).load(it.avatarUrl).into(profileImageView)
                nameView.text = it.login
            }
            .addTo(bag)
    }

    @SuppressLint("RestrictedApi")
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }
}
