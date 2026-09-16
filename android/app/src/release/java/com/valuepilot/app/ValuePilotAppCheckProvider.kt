package com.valuepilot.app

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** Production-only provider. Play Integrity is never selected in debug code. */
internal fun installValuePilotAppCheckProvider(firebaseAppCheck: FirebaseAppCheck) {
    firebaseAppCheck.installAppCheckProviderFactory(
        PlayIntegrityAppCheckProviderFactory.getInstance()
    )
}
