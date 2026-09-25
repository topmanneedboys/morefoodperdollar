package com.valuepilot.app

import com.valuepilot.core.OfflineCatalogAdmissionBlocker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledOfflineCatalogAdmissionExceptionTest {

    @Test
    fun `expired admission remains typed for an honest consumer message`() {
        val failure =
            BundledOfflineCatalogAdmissionException(
                regionId = "ca-gta",
                blockers = setOf(OfflineCatalogAdmissionBlocker.EXPIRED_SNAPSHOT)
            )

        assertEquals(BundledOfflineCatalogFailureKind.EXPIRED, failure.kind)
        assertEquals(
            setOf(OfflineCatalogAdmissionBlocker.EXPIRED_SNAPSHOT),
            failure.blockers
        )
        assertTrue(failure.message!!.contains("ca-gta"))
    }

    @Test
    fun `non-expiry admission failures remain unavailable`() {
        val failure =
            BundledOfflineCatalogAdmissionException(
                regionId = "ca-metro-vancouver",
                blockers = setOf(OfflineCatalogAdmissionBlocker.SIGNATURE_NOT_VERIFIED)
            )

        assertEquals(BundledOfflineCatalogFailureKind.UNAVAILABLE, failure.kind)
    }
}
