package com.unhappychoice.norimaki.presentation.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.unhappychoice.circleci.v2.response.Collaboration
import com.jakewharton.rxbinding2.view.clicks
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.subscribeNext
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.PublishSubject

class ProjectAdapter(val context: Context) : RecyclerView.Adapter<ProjectAdapter.ViewHolder>() {
    val collaborations = Variable<List<Collaboration>>(emptyList())
    val onClickItem = PublishSubject.create<Collaboration>()
    private val bag = CompositeDisposable()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = collaborations.value[position].id.hashCode().toLong()

    override fun getItemCount(): Int = collaborations.value.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.project_list_item_view, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(collaborations.value[position])
    }

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        private val projectNameView = view.findViewById<TextView>(R.id.projectNameView)

        fun bind(collaboration: Collaboration) {
            projectNameView.text = collaboration.name

            view.clicks()
                .subscribeNext { onClickItem.onNext(collaboration) }
                .addTo(bag)
        }
    }
}
