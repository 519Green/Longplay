package com.archivetv.data.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface InternetArchiveService {
    @GET("advancedsearch.php?output=json&fl[]=identifier&fl[]=title&fl[]=year&fl[]=mediatype&fl[]=collection")
    suspend fun search(
        @Query("q") query: String,
        @Query("rows") rows: Int,
        @Query("sort[]") sort: String,
        @Query("page") page: Int = 1,
    ): SearchResponseDto

    @GET("metadata/{identifier}")
    suspend fun metadata(@Path("identifier") identifier: String): MetadataResponseDto
}
