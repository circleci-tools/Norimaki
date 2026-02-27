package com.unhappychoice.norimaki.presentation.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.unhappychoice.circleci.CircleCIAPIClientV2
import com.gojuno.koptional.None
import com.gojuno.koptional.Some
import com.jakewharton.rxbinding2.view.clicks
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.databinding.NavigationViewBinding
import com.unhappychoice.norimaki.domain.service.EventBusService
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.presentation.adapter.ProjectAdapter
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import com.google.android.material.navigation.NavigationView as AndroidNavigationView

class NavigationView(context: Context, attr: AttributeSet): AndroidNavigationView(context, attr), DIAware {
    override lateinit var di: DI

    private val binding by lazy {
        NavigationViewBinding.bind(this)
    }

    private val client: CircleCIAPIClientV2 by instance()
    private val eventBus: EventBusService by instance()
    private val adapter = ProjectAdapter(context)
    private val bag = CompositeDisposable()

    init {
        LayoutInflater.from(context).inflate(R.layout.navigation_view, this, true)
    }

    @SuppressLint("RestrictedApi")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        binding.projectsView.adapter = adapter
        binding.projectsView.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)

        client.getCollaborations()
            .subscribeOnIoObserveOnUI()
            .subscribeNext {
                adapter.collaborations.value = it
                adapter.notifyDataSetChanged()
            }.addTo(bag)

        client.getMe()
            .subscribeOnIoObserveOnUI()
            .subscribeNext {
                binding.nameView.text = it.name.ifEmpty { it.login }
            }
            .addTo(bag)

        binding.recentView.clicks()
            .subscribeNext {
                (context as? MainActivity)?.binding?.drawerLayout?.closeDrawers()
                eventBus.selectProject.onNext(None)
            }
            .addTo(bag)

        adapter.onClickItem
            .subscribeNext {
                (context as? MainActivity)?.binding?.drawerLayout?.closeDrawers()
                eventBus.selectProject.onNext(Some(it))
            }
            .addTo(bag)
    }

    @SuppressLint("RestrictedApi")
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }
}
