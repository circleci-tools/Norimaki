package com.unhappychoice.norimaki.extension

import org.json.JSONArray
import org.json.JSONObject

fun JSONArray.asSequence(): Sequence<JSONObject> = iterator().asSequence()

private fun JSONArray.iterator(): Iterator<JSONObject> {
    return object : Iterator<JSONObject> {
        private val size = this@iterator.length()
        private var index = 0

        override fun hasNext(): Boolean = index != size
        override fun next(): JSONObject {
            val obj = getJSONObject(index)
            index += 1
            return obj
        }
    }
}