package com.unhappychoice.norimaki.api

import android.content.Context
import com.unhappychoice.norimaki.extension.defaultPreference
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.http.GET
import rx.Observable

class CircleCIAPIClient(val context: Context) {
  fun instance(): CircleCIAPIClientV1 = adapter().create(CircleCIAPIClientV1::class.java)

  private fun adapter(): RestAdapter = RestAdapter
    .Builder()
    .setEndpoint("https://circleci.com/api/v1")
    .setRequestInterceptor(intercepter())
    .build()

  private fun intercepter(): RequestInterceptor = RequestInterceptor { request ->
    request.addHeader("Accept", "application/json")
    request.addQueryParam("circle-token", token)
  }

  private val token: String = context.defaultPreference().getString("token", "")
}

interface CircleCIAPIClientV1 {
  @GET("/me")
  fun getMe(): Observable<Map<String, Any>>
}