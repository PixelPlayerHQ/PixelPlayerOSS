package com.lostf1sh.pixelplayeross.data.listenbrainz

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit interface for the ListenBrainz API.
 * Authorization header format: `Token <user token>`.
 */
interface ListenBrainzApiService {

    @POST("1/submit-listens")
    suspend fun submitListens(
        @Header("Authorization") authorization: String,
        @Body submission: ListenBrainzSubmission
    ): Response<Unit>

    @GET("1/validate-token")
    suspend fun validateToken(
        @Header("Authorization") authorization: String
    ): Response<ListenBrainzTokenValidation>
}
