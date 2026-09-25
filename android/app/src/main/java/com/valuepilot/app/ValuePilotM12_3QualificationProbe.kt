package com.valuepilot.app

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Temporary, release-only M12.3 qualification probe.
 *
 * The probe is hard-gated to the dedicated 10102 internal-test build. It makes
 * one bounded /v1/status request per process through the already-qualified
 * public Consumer Edge transport. It never logs response bodies, App Check
 * tokens, URLs, headers, or exception messages.
 */
internal object ValuePilotM12_3QualificationProbe {
    private const val TAG = "ValuePilotM12_3"
    private const val QUALIFICATION_VERSION_CODE = 10102
    private val started = AtomicBoolean(false)

    fun startIfEligible() {
        if (!BuildConfig.M12_3_QUALIFICATION_PROBE ||
            BuildConfig.VERSION_CODE != QUALIFICATION_VERSION_CODE ||
            !started.compareAndSet(false, true)
        ) {
            return
        }

        Thread(
            {
                val safeLine =
                    qualificationLogLine {
                        ValuePilotArgentinaEdgeTransportFactory.create()
                    }
                Log.i(TAG, safeLine)
            },
            "ValuePilot-M12.3-probe"
        ).apply {
            isDaemon = true
            start()
        }
    }

    internal fun qualificationLogLine(
        transportFactory: () -> ArgentinaBackendTransport
    ): String =
        try {
            val response =
                transportFactory().execute(
                    ArgentinaBackendConsumerBridge.STATUS_ENDPOINT,
                    null
                )
            response.statusCode
                .takeIf { it in 100..599 }
                ?.let { "M12_3_STATUS=$it" }
                ?: "M12_3_FAILURE=UNEXPECTED"
        } catch (error: ArgentinaBackendTransportException) {
            "M12_3_FAILURE=${error.error.name}"
        } catch (_: Exception) {
            "M12_3_FAILURE=UNEXPECTED"
        }
}
