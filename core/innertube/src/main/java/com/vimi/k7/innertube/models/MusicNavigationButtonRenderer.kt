/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * Origin: innertube/src/main/kotlin/com/metrolist/innertube/models/MusicNavigationButtonRenderer.kt
 */

package com.vimi.k7.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicNavigationButtonRenderer(
    val buttonText: Runs,
    val solid: Solid?,
    val iconStyle: IconStyle?,
    val clickCommand: NavigationEndpoint,
) {
    @Serializable
    data class Solid(
        val leftStripeColor: Long,
    )

    @Serializable
    data class IconStyle(
        val icon: Icon,
    )
}
