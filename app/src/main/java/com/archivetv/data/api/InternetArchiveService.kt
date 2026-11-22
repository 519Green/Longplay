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

    /** Search with caller-chosen fields (Live Music Archive). */
    @GET("advancedsearch.php?output=json")
    suspend fun searchLive(
        @Query("q") query: String,
        @Query("fl[]") fields: List<String>,
        @Query("rows") rows: Int,
        @Query("sort[]") sort: String,
        @Query("page") page: Int = 1,
    ): LiveResponseDto

    /** Search returning only [fields], for derived catalog groups. */
    @GET("advancedsearch.php?output=json")
    suspend fun searchFields(
        @Query("q") query: String,
        @Query("fl[]") fields: List<String>,
        @Query("rows") rows: Int,
        @Query("sort[]") sort: String,
    ): FieldsResponseDto

    @GET("metadata/{identifier}")
    suspend fun metadata(@Path("identifier") identifier: String): MetadataResponseDto
}
