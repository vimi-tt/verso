/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * Origin: innertube/src/main/kotlin/com/metrolist/innertube/pages/PlaylistContinuationPage.kt
 */

package com.vimi.k7.innertube.pages

import com.vimi.k7.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
