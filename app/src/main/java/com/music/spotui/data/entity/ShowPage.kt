package com.music.spotui.data.entity

/**
 * A show page as the screen shows it: the header plus the rows.
 *
 * A podcast has episodes; a book has chapters, fetched through another Spotify operation
 * ([audiobook] = true). Both arrive as [SongsModel] rows so they play through the same
 * queue as an episode (url = "episode:<raw id>").
 */
data class ShowPage(
    val show: PodcastModel,
    val items: List<SongsModel> = emptyList(),
    val audiobook: Boolean = false,
    /** Spotify's cover for the page: the show's, or the first item's when the header failed. */
    val coverUri: String = "",
    /**
     * The context a row of this page plays in. A podcast's episodes play in their show; a book's
     * chapters must play in the book itself, or Spotify's player refuses them (reported).
     */
    val contextUri: String = "",
) {
    val complete: Boolean get() = items.isNotEmpty()
}

/**
 * The Spotify context a show page's rows play in. A book's chapters are episodes too, but only
 * play inside the book: a podcast show context makes the player refuse them (reported). [bookUri]
 * is the audiobook's own uri when the header could be read.
 */
internal fun showContextUri(showId: String, bookUri: String?, audiobook: Boolean): String {
    if (!audiobook) return "spotify:show:$showId"
    val uri = bookUri.orEmpty()
    return if (uri.startsWith("spotify:audiobook:")) uri else "spotify:audiobook:$showId"
}
