/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * Origin: innertube/src/main/kotlin/com/metrolist/innertube/pages/ArtistItemsContinuationPage.kt
 */

package com.vimi.k7.innertube.pages

import com.vimi.k7.innertube.models.YTItem

data class ArtistItemsContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
