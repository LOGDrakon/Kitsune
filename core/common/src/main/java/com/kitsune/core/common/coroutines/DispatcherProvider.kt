package com.kitsune.core.common.coroutines

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Indirection over [kotlinx.coroutines.Dispatchers] so callers can be unit-tested
 * with a fake/deterministic dispatcher instead of the real IO/Default dispatchers.
 */
interface DispatcherProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
}
