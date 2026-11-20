package com.unhappychoice.norimaki.presentation.core

import android.os.Parcel
import android.os.Parcelable
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.BuildStepId
import com.unhappychoice.norimaki.presentation.screen.BuildListScreen
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.screen.BuildStepScreen
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
            val type = Class.forName(reader.nextName()).asSubclass(Screen::class.java)
            return when (type) {
                BuildScreen::class.java, BuildStepScreen::class.java ->
                    decodeBuildScreen(gson.fromJson(reader, JsonObject::class.java), type)
                else -> gson.fromJson(reader, type)
            }
        } finally {
            reader.close()
        }
    }

    private fun decodeBuildScreen(json: JsonObject, type: Class<out Screen>): Screen {
        if (!json.has("build")) return gson.fromJson(json, type)
        val build = gson.fromJson(json.get("build"), Build::class.java)
        val buildId = BuildId.from(build) ?: return BuildListScreen("")
        val step = gson.fromJson(json.get("buildStep"), BuildStep::class.java)
            ?: return BuildScreen(buildId)
        return BuildStepScreen(buildId, BuildStepId.from(step, build.steps.orEmpty().indexOf(step)))
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