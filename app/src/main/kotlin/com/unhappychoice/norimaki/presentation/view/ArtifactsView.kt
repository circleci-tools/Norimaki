package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.databinding.ArtifactsViewBinding
import com.unhappychoice.norimaki.presentation.presenter.ArtifactListState
import com.unhappychoice.norimaki.presentation.presenter.ArtifactsPresenter
import com.unhappychoice.norimaki.presentation.view.core.BaseView
import org.kodein.di.instance

class ArtifactsView(context: Context, attr: AttributeSet) : BaseView<ArtifactsView>(context, attr) {
    override val presenter: ArtifactsPresenter by instance()
    private val binding by lazy { ArtifactsViewBinding.bind(this) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        presenter.takeView(this)
        presenter.observeDownloads(this)
        binding.retryArtifacts.setOnClickListener { presenter.load() }
        presenter.render()
    }

    override fun onDetachedFromWindow() {
        presenter.dropView(this)
        super.onDetachedFromWindow()
    }

    internal fun render(state: ArtifactListState) {
        val artifacts = (state as? ArtifactListState.Loaded)?.artifacts.orEmpty()
        binding.artifactLoading.isVisible = state == ArtifactListState.Loading
        binding.retryArtifacts.isVisible = state == ArtifactListState.Failed
        binding.artifactStatus.isVisible = state == ArtifactListState.Failed ||
            (state is ArtifactListState.Loaded && artifacts.isEmpty())
        binding.artifactStatus.setText(
            if (state == ArtifactListState.Failed) R.string.artifacts_load_failed else R.string.artifacts_empty
        )
        binding.artifactList.isVisible = artifacts.isNotEmpty()
        binding.artifactList.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, artifacts.map { it.path })
        binding.artifactList.setOnItemClickListener { _, _, position, _ ->
            presenter.chooseDestination(artifacts[position])
        }
    }

    internal fun setDownloading(downloading: Boolean) {
        binding.artifactList.isEnabled = !downloading
        binding.artifactDownloading.isVisible = downloading
    }

    internal fun showMessage(message: Int) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
