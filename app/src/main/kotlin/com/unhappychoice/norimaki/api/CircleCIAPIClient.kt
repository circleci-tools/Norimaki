package com.unhappychoice.norimaki.api

import android.content.Context
import android.util.Log
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.unhappychoice.norimaki.extension.defaultPreference
import com.unhappychoice.norimaki.model.Build
import com.unhappychoice.norimaki.model.BuildStep
import com.unhappychoice.norimaki.model.Project
import com.unhappychoice.norimaki.model.User
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.converter.GsonConverter
import retrofit.http.GET
import retrofit.http.Path
import retrofit.http.Query
import rx.Observable

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

  private val token: String = context.defaultPreference().getString("token", "")
}

interface CircleCIAPIClientV1 {
  @GET("/me")
  fun getMe(): Observable<User>

  @GET("/projects")
  fun getProjects(): Observable<List<Project>>

  @GET("/recent-builds")
  fun getRecentBuildsAcross(@Query("offset") offset: Int = 0, @Query("limit") limit: Int = 20): Observable<List<Build>>

  @GET("/project/{username}/{project}/{buildnum}")
  fun getBuild(@Path("username") userName: String, @Path("project") project: String, @Path("buildnum") buildNumber: Int): Observable<Build>
}