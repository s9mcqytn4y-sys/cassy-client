package com.azuredevid.cassy.core.coroutines

import kotlinx.coroutines.CancellationException

/**
 * Like [runCatching], but rethrows [CancellationException] so a cancelled coroutine stops instead of
 * reporting a failure and running on. Use it in suspend code wherever [runCatching] would wrap a suspend call.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Result.failure(t)
    }
