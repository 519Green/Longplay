package com.archivetv.data.api

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Internet Archive metadata fields (title, description, year, ...) arrive as a
 * string on most items but as an array, a number, or null on others. Fields
 * marked with this qualifier are read leniently into a list of strings.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
@JsonQualifier
annotation class StringOrList

object StringOrListAdapter {
    @FromJson @StringOrList
    fun fromJson(reader: JsonReader): List<String> = when (reader.peek()) {
        JsonReader.Token.NULL -> { reader.nextNull<Unit>(); emptyList() }
        JsonReader.Token.BEGIN_ARRAY -> buildList {
            reader.beginArray()
            while (reader.hasNext()) readScalar(reader)?.let(::add)
            reader.endArray()
        }
        else -> listOfNotNull(readScalar(reader))
    }

    @ToJson
    fun toJson(writer: JsonWriter, @StringOrList value: List<String>) {
        writer.beginArray()
        value.forEach { writer.value(it) }
        writer.endArray()
    }

    private fun readScalar(reader: JsonReader): String? = when (reader.peek()) {
        JsonReader.Token.STRING, JsonReader.Token.NUMBER -> reader.nextString()
        JsonReader.Token.BOOLEAN -> reader.nextBoolean().toString()
        else -> { reader.skipValue(); null }
    }
}

fun archiveMoshi(): Moshi = Moshi.Builder()
    .add(StringOrListAdapter)
    .add(KotlinJsonAdapterFactory())
    .build()
