package com.raja.bingehwa.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Reads a public AniList reading list so a reader can bring their shelf over in one tap.
 * Only titles are used; AniList itself is never written to.
 */
class AniListClient(private val http: OkHttpClient, private val json: Json = Json { ignoreUnknownKeys = true }) {

    suspend fun readingTitles(userName: String): List<String> = withContext(Dispatchers.IO) {
        val query = "query(\$u:String){MediaListCollection(userName:\$u,type:MANGA,status_in:[CURRENT,REPEATING,PLANNING]){lists{entries{media{title{romaji english}}}}}}"
        val payload = JsonObject(
            mapOf(
                "query" to kotlinx.serialization.json.JsonPrimitive(query),
                "variables" to JsonObject(mapOf("u" to kotlinx.serialization.json.JsonPrimitive(userName))),
            ),
        ).toString()
        val req = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (res.code == 404) throw IOException("No AniList user called $userName")
            if (!res.isSuccessful) throw IOException("AniList answered ${res.code}")
            parseTitles(body)
        }
    }

    internal fun parseTitles(body: String): List<String> {
        val lists: JsonArray = json.parseToJsonElement(body).jsonObject["data"]?.jsonObject
            ?.get("MediaListCollection")?.jsonObject?.get("lists")?.jsonArray ?: return emptyList()
        return lists.flatMap { l ->
            l.jsonObject["entries"]?.jsonArray.orEmpty().mapNotNull { e ->
                val t = e.jsonObject["media"]?.jsonObject?.get("title")?.jsonObject
                t?.get("english")?.jsonPrimitive?.contentOrNull ?: t?.get("romaji")?.jsonPrimitive?.contentOrNull
            }
        }.distinct()
    }
}
