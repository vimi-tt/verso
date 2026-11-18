/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * Origin: innertube/src/main/kotlin/com/metrolist/innertube/utils/Utils.kt
 */

package com.vimi.k7.innertube.utils

fun String.parseTime(): Int? =
    try {
        val parts = split(":").map { it.toInt() }
        when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    } catch (e: Exception) {
        null
    }
