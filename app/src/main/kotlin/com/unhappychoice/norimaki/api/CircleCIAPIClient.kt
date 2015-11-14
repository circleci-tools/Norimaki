package com.unhappychoice.norimaki.api

import android.content.Context
import android.util.Log
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.unhappychoice.norimaki.model.AccessToken
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.converter.GsonConverter

class CircleCIAPIClient(val context: Context) {
  fun instance(): CircleCIAPIClientV1 = adapter().create(CircleCIAPIClientV1::class.java)

  private fun adapter(): RestAdapter = RestAdapter
    .Builder()
    .setEndpoint("https://circleci.com/api/v1")
    .setRequestInterceptor(intercepter())
    .setLogLevel(RestAdapter.LogLevel.BASIC)
    .setLog { Log.i("API", it) }
    .setConverter(converter())
    .build()

  private fun intercepter(): RequestInterceptor = RequestInterceptor { request ->
    request.addHeader("Accept", "application/json")
    request.addQueryParam("circle-token", token)
  }

  private fun converter(): GsonConverter = GsonConverter(
    GsonBuilder()
      .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
      .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .create()
  )

  private val token: String = AccessToken(context).value()
}