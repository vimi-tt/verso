/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * Origin: innertube/src/main/kotlin/com/metrolist/innertube/models/Icon.kt
 */

package com.vimi.k7.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Icon(
    val iconType: String,
)
