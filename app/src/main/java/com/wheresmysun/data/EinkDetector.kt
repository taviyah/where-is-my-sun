package com.wheresmysun.data

import android.os.Build
import java.util.Locale

/**
 * Guesses whether this phone has an e-ink screen. Android has no API for it, so this matches the
 * makers that ship e-ink phones and tablets. The setting can force e-ink mode on or off either way.
 */
object EinkDetector {
    private val einkMakers = setOf(
        "bigme", // HiBreak, HiBreak Pro, B751...
        "onyx", "boox",
        "mudita",
        "meebook",
        "pocketbook",
        "likebook",
        "dasung",
        "moaan",
        "xrz", // display-driver vendor used by several Chinese e-ink brands
    )

    // Hisense makes both kinds; its e-ink line is the A-series (A5, A7, A9) and HLTE2xx models.
    private val hisenseEink = Regex("^(A5|A7|A9|HLTE2\\d\\d)", RegexOption.IGNORE_CASE)

    fun isEinkDevice(
        manufacturer: String = Build.MANUFACTURER,
        brand: String = Build.BRAND,
        model: String = Build.MODEL,
    ): Boolean {
        val maker = manufacturer.lowercase(Locale.ROOT)
        val brandLower = brand.lowercase(Locale.ROOT)
        if (einkMakers.any { maker.contains(it) || brandLower.contains(it) }) return true
        if (maker.contains("hisense") && hisenseEink.containsMatchIn(model)) return true
        return false
    }
}
