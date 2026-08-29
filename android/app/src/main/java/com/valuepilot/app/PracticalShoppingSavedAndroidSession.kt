package com.valuepilot.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Process-scoped Saved runtime.
 *
 * Activity/session recreation must not create independent workers that can overlap app-internal
 * AtomicFile transactions. One process therefore shares the exact store, display store, and
 * serial worker. Work submitted by a newly created UI session is queued behind any Saved work
 * that was already running for the previous session.
 *
 * Only application context is retained. The executor is process-owned rather than Activity-
 * owned and is intentionally not shut down by a surface/session close.
 */
internal class PracticalShoppingSavedProcessRuntime private constructor(
    val exactStore: PracticalShoppingSavedExactPreferenceLocalStore,
    val displayStore: PracticalShoppingSavedDisplayMetadataLocalStore,
    private val executor: ExecutorService
) : PracticalShoppingSavedWorkScheduler {

    override fun schedule(block: () -> Unit) {
        executor.execute(block)
    }

    companion object {
        @Volatile
        private var instance: PracticalShoppingSavedProcessRuntime? = null

        fun get(context: Context): PracticalShoppingSavedProcessRuntime {
            val existing = instance
            if (existing != null) return existing

            return synchronized(this) {
                instance
                    ?: create(context.applicationContext ?: context)
                        .also { created -> instance = created }
            }
        }

        private fun create(
            appContext: Context
        ): PracticalShoppingSavedProcessRuntime {
            val executor =
                Executors.newSingleThreadExecutor { runnable ->
                    Thread(runnable, "valuepilot-saved-persistence").apply {
                        isDaemon = false
                    }
                }

            return PracticalShoppingSavedProcessRuntime(
                exactStore = PracticalShoppingSavedExactPreferenceLocalStore(appContext),
                displayStore = PracticalShoppingSavedDisplayMetadataLocalStore(appContext),
                executor = executor
            )
        }
    }
}

/** Main-Looper completion adapter. No lifecycle or persistence policy lives here. */
internal class PracticalShoppingSavedMainLooperDispatcher(
    private val handler: Handler
) : PracticalShoppingSavedCompletionDispatcher {
    override fun dispatch(block: () -> Unit) {
        handler.post(block)
    }
}

/**
 * Android owner for one Saved lifecycle host.
 *
 * This class is intentionally not a View and does not render Saved content itself. It wires
 * a per-surface host to the process-scoped Saved runtime and Android main Looper. UI owners
 * provide the immutable-state renderer callback.
 *
 * Public lifecycle methods are expected to be called from the Android main thread. All Saved
 * persistence/coordinator work for every session in this process is serialized through
 * [PracticalShoppingSavedProcessRuntime]; typed completions return to the main Looper before
 * the host reducer/renderer is touched.
 *
 * [close] closes only this host. Process-owned queued/running persistence work is allowed to
 * finish; the closed host ignores its eventual completion. A subsequent Activity/session uses
 * the same serial runtime, so its load cannot race ahead of an older queued Saved mutation.
 */
class PracticalShoppingSavedAndroidSession private constructor(
    private val host: PracticalShoppingSavedLifecycleHost
) : AutoCloseable {

    fun refresh() {
        requireMainThread()
        host.refresh()
    }

    fun selectAction(action: PracticalShoppingSavedExactPreferenceUiAction) {
        requireMainThread()
        host.selectAction(action)
    }

    fun currentState(): PracticalShoppingSavedLifecycleState = host.currentState()

    override fun close() {
        requireMainThread()
        host.close()
    }

    fun isClosed(): Boolean = host.isClosed()

    companion object {
        fun create(
            context: Context,
            renderer: PracticalShoppingSavedLifecycleRenderer
        ): PracticalShoppingSavedAndroidSession {
            requireMainThread()

            val appContext = context.applicationContext ?: context
            val runtime = PracticalShoppingSavedProcessRuntime.get(appContext)
            val handler = Handler(Looper.getMainLooper())
            val gateway =
                PracticalShoppingSavedLocalExperienceGateway(
                    exactStore = runtime.exactStore,
                    displayStore = runtime.displayStore
                )
            val host =
                PracticalShoppingSavedLifecycleHost(
                    gateway = gateway,
                    worker = runtime,
                    completionDispatcher = PracticalShoppingSavedMainLooperDispatcher(handler),
                    renderer = renderer
                )

            return PracticalShoppingSavedAndroidSession(host = host)
        }

        private fun requireMainThread() {
            check(Looper.myLooper() == Looper.getMainLooper()) {
                "Saved lifecycle ownership must stay on the Android main thread."
            }
        }
    }
}
