package com.valuepilot.app

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck

/**
 * Installs the release-appropriate App Check provider before any future edge
 * request is allowed.  The provider implementation is supplied by the
 * release/debug source set so a debug token path cannot enter a release APK.
 */
class ValuePilotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ValuePilotFirebaseRuntime.initialize(this)
    }
}

internal enum class ValuePilotFirebaseState {
    NOT_INITIALIZED,
    READY,
    CONFIGURATION_UNAVAILABLE,
    INITIALIZATION_FAILED
}

internal object ValuePilotFirebaseRuntime {
    @Volatile
    private var initializationState = ValuePilotFirebaseState.NOT_INITIALIZED

    @Volatile
    private var appCheck: FirebaseAppCheck? = null

    val state: ValuePilotFirebaseState
        get() = initializationState

    /**
     * Initialization is idempotent and fail-closed.  A missing or invalid
     * google-services configuration never produces a client that can send an
     * unverified request.
     */
    fun initialize(context: Context): ValuePilotFirebaseState {
        if (initializationState == ValuePilotFirebaseState.READY) return initializationState
        synchronized(this) {
            if (initializationState == ValuePilotFirebaseState.READY) return initializationState
            return try {
                val firebaseApp = FirebaseApp.initializeApp(context.applicationContext)
                if (firebaseApp == null) {
                    appCheck = null
                    initializationState = ValuePilotFirebaseState.CONFIGURATION_UNAVAILABLE
                    initializationState
                } else {
                    val firebaseAppCheck = FirebaseAppCheck.getInstance()
                    installValuePilotAppCheckProvider(firebaseAppCheck)
                    appCheck = firebaseAppCheck
                    initializationState = ValuePilotFirebaseState.READY
                    initializationState
                }
            } catch (_: Exception) {
                appCheck = null
                initializationState = ValuePilotFirebaseState.INITIALIZATION_FAILED
                initializationState
            }
        }
    }

    /** Returns the initialized instance only; callers must not bypass App Check. */
    fun appCheckOrNull(): FirebaseAppCheck? =
        if (initializationState == ValuePilotFirebaseState.READY) appCheck else null
}
