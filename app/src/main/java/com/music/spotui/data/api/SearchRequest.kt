package com.music.spotui.data.api

internal inline fun <T> searchRequest(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}
