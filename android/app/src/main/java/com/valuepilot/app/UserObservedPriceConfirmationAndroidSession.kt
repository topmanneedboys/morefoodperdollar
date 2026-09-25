package com.valuepilot.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Process-scoped runtime for user-provided observed-price proof retention.
 *
 * Activity/session recreation must not create independent proof stores or workers that can overlap
 * AtomicFile operations against the same digest-backed directory. One process therefore shares the
 * exact proof store and one serial worker. Only application context is retained.
 *
 * The executor is process-owned and intentionally survives individual foreground session closes.
 * Closing a session suppresses that host's late completion but does not interrupt an atomic proof
 * write that was already queued or running.
 */
internal class UserObservedPriceConfirmationProcessRuntime private constructor(
    val proofStore: UserProvidedPriceProofArtifactLocalStore,
    private val executor: ExecutorService
) : UserObservedPriceConfirmationWorkScheduler {

    override fun schedule(block: () -> Unit) {
        executor.execute(block)
    }

    companion object {
        @Volatile
        private var instance: UserObservedPriceConfirmationProcessRuntime? = null

        fun get(context: Context): UserObservedPriceConfirmationProcessRuntime {
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
        ): UserObservedPriceConfirmationProcessRuntime {
            val executor =
                Executors.newSingleThreadExecutor { runnable ->
                    Thread(runnable, "valuepilot-observed-price-proof").apply {
                        isDaemon = false
                    }
                }

            return UserObservedPriceConfirmationProcessRuntime(
                proofStore = UserProvidedPriceProofArtifactLocalStore(appContext),
                executor = executor
            )
        }
    }
}

/** Main-Looper completion adapter. It owns no confirmation or storage policy. */
internal class UserObservedPriceConfirmationMainLooperDispatcher(
    private val handler: Handler
) : UserObservedPriceConfirmationCompletionDispatcher {
    override fun dispatch(block: () -> Unit) {
        handler.post(block)
    }
}

/**
 * Android owner for one future foreground observed-price confirmation flow.
 *
 * Public lifecycle methods must be called on the Android main thread. [submit] forwards only the
 * caller-supplied proof bytes, proof type, identifiers, timestamps, and exact confirmation fields
 * into the already-verified execution host. The process runtime performs proof retention on its
 * serial worker; typed completion returns through the main Looper.
 *
 * This session does not capture camera/OCR input, generate IDs, read a clock, interpret proof,
 * create evidence, resolve quantity, evaluate freshness/ranking, render UI, or activate a route.
 */
internal class UserObservedPriceConfirmationAndroidSession private constructor(
    private val host: UserObservedPriceConfirmationExecutionHost
) : AutoCloseable {

    fun submit(
        artifactId: String,
        proofType: UserProvidedPriceProofType,
        artifactBytes: ByteArray,
        fields: UserObservedPriceConfirmationFields
    ): Boolean {
        requireMainThread()
        return host.submit(
            artifactId = artifactId,
            proofType = proofType,
            artifactBytes = artifactBytes,
            fields = fields
        )
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
            completionListener: UserObservedPriceConfirmationCompletionListener
        ): UserObservedPriceConfirmationAndroidSession {
            requireMainThread()

            val appContext = context.applicationContext ?: context
            val runtime = UserObservedPriceConfirmationProcessRuntime.get(appContext)
            val transaction = UserObservedPriceConfirmationTransaction(runtime.proofStore)
            val gateway = UserObservedPriceConfirmationLocalGateway(transaction)
            val host =
                UserObservedPriceConfirmationExecutionHost(
                    gateway = gateway,
                    worker = runtime,
                    completionDispatcher =
                        UserObservedPriceConfirmationMainLooperDispatcher(
                            Handler(Looper.getMainLooper())
                        ),
                    completionListener = completionListener
                )

            return UserObservedPriceConfirmationAndroidSession(host)
        }

        private fun requireMainThread() {
            check(Looper.myLooper() == Looper.getMainLooper()) {
                "Observed-price confirmation ownership must stay on the Android main thread."
            }
        }
    }
}
