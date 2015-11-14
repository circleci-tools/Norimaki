package com.unhappychoice.norimaki.api

import com.unhappychoice.norimaki.model.Build
import com.unhappychoice.norimaki.model.Project
import com.unhappychoice.norimaki.model.User
import retrofit.http.GET
import retrofit.http.Path
import retrofit.http.Query
import rx.Observable

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