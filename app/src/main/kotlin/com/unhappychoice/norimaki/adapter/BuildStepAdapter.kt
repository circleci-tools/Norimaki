package com.unhappychoice.norimaki.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.unhappychoice.norimaki.model.BuildStep

class BuildStepAdapter(context: Context): BaseAdapter() {
  var buildSteps: List<BuildStep> = listOf()

  override fun getCount(): Int = buildSteps.count()
  override fun getItem(position: Int): BuildStep = buildSteps[position]
  override fun getItemId(position: Int): Long = position.toLong()

  override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
    return if (convertView == null) {
      itemView(position, parent)
    } else {
      itemViewByView(convertView, position)
    }
  }

  private fun itemView(position: Int, parent: ViewGroup): View {
    val view = inflater.inflate(android.R.layout.simple_list_item_1, parent, false)
    return itemViewByView(view, position)
  }

  private fun itemViewByView(view: View, position: Int): View {
    val text = view.findViewById(android.R.id.text1) as TextView
    text.text = getItem(position).name
    return view
  }

  private val inflater = LayoutInflater.from(context)
}