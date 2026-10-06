package com.raja.bingehwa.data.model

/** App-level series. Kept separate from API DTOs so a MangaDex change never leaks into the UI. */
data class Series(
    val id: String,
    val title: String,
    val description: String,
    val coverUrl: String?,
    val tags: List<String>,
    val status: String,
    val year: Int?,
    /** Official English release link when MangaDex lists one (used for licensed series). */
    val officialUrl: String?,
)

data class Chapter(
    val id: String,
    val seriesId: String,
    /** Kept as text because MangaDex uses values like "12.5" and "Oneshot". */
    val number: String,
    val title: String?,
    val pages: Int,
    val publishAt: String,
    /** Set when the chapter lives on an official site and cannot be read in the app. */
    val externalUrl: String?,
) {
    val readableInApp: Boolean get() = externalUrl == null && pages > 0
}

data class SeriesStats(val rating: Double?, val follows: Int?)

/** One page of a chapter: either a remote URL or a file that was downloaded earlier. */
sealed interface PageSource {
    data class Remote(val url: String) : PageSource
    data class Local(val path: String) : PageSource
}

/** Result wrapper for screens: one shape for loading, failure and success. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>
    data class Data<T>(val value: T) : UiState<T>
}

/** The loaded value, or null while loading or after an error. Avoids unchecked casts at call sites. */
fun <T> UiState<T>.dataOrNull(): T? = if (this is UiState.Data<T>) this.value else null
