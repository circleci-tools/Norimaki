package com.unhappychoice.norimaki.presentation.core

import android.os.Parcel
import android.os.Parcelable
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.unhappychoice.norimaki.presentation.screen.core.Screen
import flow.KeyParceler
import java.io.StringReader
import java.io.StringWriter

/**
 * From https://github.com/pyricau/dagger2-mortar-flow-experiment/blob/master/app/src/main/java/dagger/demo/mortarflow/GsonParceler.java
 */
class GsonParceler : KeyParceler {
    override fun toParcelable(key: Any): Parcelable = when (key) {
        is Screen -> Wrapper(encode(key))
        else -> throw IllegalArgumentException()
    }

    override fun toKey(parcelable: Parcelable): Any = decode((parcelable as Wrapper).json)

    private fun encode(instance: Screen): String {
        val stringWriter = StringWriter()
        val writer = JsonWriter(stringWriter)

        try {
            val type = instance.javaClass

            writer.beginObject()
            writer.name(type.name)
            gson.toJson(instance, type, writer)
            writer.endObject()
            return stringWriter.toString()
        } finally {
            writer.close()
        }
    }

    private fun decode(json: String): Screen {
        val reader = JsonReader(StringReader(json))

        try {
            reader.beginObject()
            val type = Class.forName(reader.nextName())
            return gson.fromJson(reader, type)
        } finally {
            reader.close()
        }
    }

    private val gson = Gson()

    private class Wrapper(val json: String) : Parcelable {
        override fun describeContents(): Int = 0
        override fun writeToParcel(parcel: Parcel, flags: Int) = parcel.writeString(json)

        companion object {
            @JvmField @Suppress("unused") val CREATOR = object : Parcelable.Creator<Wrapper> {
                override fun createFromParcel(data: Parcel) = Wrapper(data.readString() ?: "")
                override fun newArray(size: Int): Array<out Wrapper?>? = arrayOfNulls(size)
            }
        }
    }
}