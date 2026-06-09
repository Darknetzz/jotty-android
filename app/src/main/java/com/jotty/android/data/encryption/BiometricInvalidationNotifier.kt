package com.jotty.android.data.encryption

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-wide signal when biometric-protected passphrases are cleared because device
 * biometric enrollment changed (or the Keystore key is otherwise permanently invalidated).
 */
object BiometricInvalidationNotifier {
    private val _events = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    val events: SharedFlow<Int> = _events.asSharedFlow()

    fun notifyInvalidated(count: Int) {
        if (count > 0) {
            _events.tryEmit(count)
        }
    }
}
