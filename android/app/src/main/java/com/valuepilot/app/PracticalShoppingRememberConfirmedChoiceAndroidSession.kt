package com.valuepilot.app

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Android owner for one future confirmation/Remember surface.
 *
 * This session deliberately reuses [PracticalShoppingSavedProcessRuntime]. Remember writes are
 * therefore queued on the same single process worker as active Saved loads/deletes and cannot
 * race those app-internal AtomicFile transactions within this process.
 *
 * The session is not a View and does not create or confirm identity. Callers submit only the
 * typed request produced after their confirmation flow. Completion is returned on the Android
 * main Looper. Closing the session closes only this host; process-owned queued/running work is
 * allowed to finish, and the closed host suppresses its late completion.
 */
class PracticalShoppingRememberConfirmedChoiceAndroidSession private constructor(
    private val host: PracticalShoppingRememberConfirmedChoiceHost
) : AutoCloseable {

    fun remember(request: PracticalShoppingRememberConfirmedChoiceRequest): Boolean {
        requireMainThread()
        return host.remember(request)
    }

    fun isBusy(): Boolean = host.isBusy()

    fun isClosed(): Boolean = host.isClosed()

    override fun close() {
        requireMainThread()
        host.close()
    }

    companion object {
        fun create(
            context: Context,
            completionListener: PracticalShoppingRememberConfirmedChoiceCompletionListener
        ): PracticalShoppingRememberConfirmedChoiceAndroidSession {
            requireMainThread()

            val appContext = context.applicationContext ?: context
            val runtime = PracticalShoppingSavedProcessRuntime.get(appContext)
            val gateway =
                PracticalShoppingRememberConfirmedChoiceLocalGateway(
                    exactStore = runtime.exactStore,
                    displayStore = runtime.displayStore
                )
            val host =
                PracticalShoppingRememberConfirmedChoiceHost(
                    gateway = gateway,
                    worker = runtime,
                    completionDispatcher =
                        PracticalShoppingSavedMainLooperDispatcher(
                            Handler(Looper.getMainLooper())
                        ),
                    completionListener = completionListener
                )

            return PracticalShoppingRememberConfirmedChoiceAndroidSession(host)
        }

        private fun requireMainThread() {
            check(Looper.myLooper() == Looper.getMainLooper()) {
                "Remember-confirmed-choice ownership must stay on the Android main thread."
            }
        }
    }
}
