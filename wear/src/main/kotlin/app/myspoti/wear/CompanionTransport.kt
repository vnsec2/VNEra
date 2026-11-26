package app.myspoti.wear

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun <T> companionFallback(layer: suspend () -> T, lan: suspend () -> T): T {
    val layerFailure = try {
        return layer()
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        // A deadline inside the Layer attempt can fall back; cancellation from its caller cannot.
        if (e is CancellationException && e !is TimeoutCancellationException) throw e
        e
    }
    try {
        return lan()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (layerFailure !== e) layerFailure.addSuppressed(e)
        throw layerFailure
    }
}
