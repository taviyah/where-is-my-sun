package com.wheresmysun.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EinkDetectorTest {
    @Test fun recognisesEinkPhones() {
        assertTrue(EinkDetector.isEinkDevice("Bigme", "Bigme", "HiBreak"))
        assertTrue(EinkDetector.isEinkDevice("ONYX", "Onyx", "Palma"))
        assertTrue(EinkDetector.isEinkDevice("Hisense", "Hisense", "A9"))
        assertTrue(EinkDetector.isEinkDevice("Hisense", "Hisense", "HLTE202N"))
    }

    @Test fun ignoresOrdinaryPhones() {
        assertFalse(EinkDetector.isEinkDevice("samsung", "samsung", "SM-S926B"))
        assertFalse(EinkDetector.isEinkDevice("Google", "google", "Pixel 9"))
        // Hisense also makes LCD phones.
        assertFalse(EinkDetector.isEinkDevice("Hisense", "Hisense", "Infinity H60"))
    }
}
