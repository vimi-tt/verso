/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * Origin: innertube/src/main/kotlin/com/metrolist/innertube/models/PlaylistPanelRenderer.kt
 */

package com.vimi.k7.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistPanelRenderer(
    val title: String?,
    val titleText: Runs?,
    val shortBylineText: Runs?,
    val contents: List<Content>,
    val isInfinite: Boolean?,
    val numItemsToShow: Int?,
    val playlistId: String?,
    val continuations: List<Continuation>?,
) {
    @Serializable
    data class Content(
        val playlistPanelVideoRenderer: PlaylistPanelVideoRenderer? = null,
        val automixPreviewVideoRenderer: AutomixPreviewVideoRenderer? = null,
        val playlistPanelVideoWrapperRenderer: Wrapper? = null,
    ) {
        @Serializable
        data class Wrapper(
            val primaryRenderer: PrimaryRenderer? = null,
            val counterpart: List<Counterpart>? = null,
        ) {
            @Serializable
            data class PrimaryRenderer(
                val playlistPanelVideoRenderer: PlaylistPanelVideoRenderer? = null,
            )

            @Serializable
            data class Counterpart(
                val counterpartRenderer: PrimaryRenderer? = null,
            )
        }
    }
}
