package app.myspoti.extensions

object QueueLookahead {
    fun <T> next(upcoming: List<T>): List<T> = upcoming.take(5)
}
