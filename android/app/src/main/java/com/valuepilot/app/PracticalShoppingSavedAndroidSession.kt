package com.valuepilot.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Thin scheduler adapter around one owned Saved persistence executor. */
internal class PracticalShoppingSavedExecutorScheduler(
    private val executor: ExecutorService
) : PracticalShoppingSavedWorkScheduler {
    override fun schedule(block: () -> Unit) {
        executor.execute(block)
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
 * This class is intentionally not a View and does not render Saved content itself. It only
 * wires the verified app-internal stores/gateway/host to one background executor and the
 * Android main Looper. UI owners provide the immutable-state renderer callback.
 *
 * Public lifecycle methods are expected to be called from the Android main thread. Saved
 * file work always runs on the owned single-thread executor; typed completions are posted
 * back to the main Looper before the host reducer/renderer is touched.
 *
 * [close] closes the host before shutting down the executor. `shutdown()` is deliberate:
 * already queued/running AtomicFile work is allowed to finish rather than being interrupted,
 * while the closed host ignores its eventual completion. No Handler-wide callback removal is
 * performed, so this owner cannot accidentally remove callbacks belonging to another feature.
 */
class PracticalShoppingSavedAndroidSession private constructor(
    private val host: PracticalShoppingSavedLifecycleHost,
    private val executor: ExecutorService
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
        executor.shutdown()
    }

    fun isClosed(): Boolean = host.isClosed()

    companion object {
        fun create(
            context: Context,
            renderer: PracticalShoppingSavedLifecycleRenderer
        ): PracticalShoppingSavedAndroidSession {
            requireMainThread()

            val appContext = context.applicationContext ?: context
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "valuepilot-saved-persistence").apply {
                    isDaemon = false
                }
            }
            val handler = Handler(Looper.getMainLooper())
            val exactStore = PracticalShoppingSavedExactPreferenceLocalStore(appContext)
            val displayStore = PracticalShoppingSavedDisplayMetadataLocalStore(appContext)
            val gateway =
                PracticalShoppingSavedLocalExperienceGateway(
                    exactStore = exactStore,
                    displayStore = displayStore
                )
            val host =
                PracticalShoppingSavedLifecycleHost(
                    gateway = gateway,
                    worker = PracticalShoppingSavedExecutorScheduler(executor),
                    completionDispatcher = PracticalShoppingSavedMainLooperDispatcher(handler),
                    renderer = renderer
                )

            return PracticalShoppingSavedAndroidSession(
                host = host,
                executor = executor
            )
        }

        private fun requireMainThread() {
            check(Looper.myLooper() == Looper.getMainLooper()) {
                "Saved lifecycle ownership must stay on the Android main thread."
            }
        }
    }
}
