package com.vimi.k7.innertube

import com.vimi.k7.innertube.models.response.BrowseResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistExtractionTest {
    @Test
    fun parsePlaylistBrowseResponse_separatesSongsContinuationFromSectionSuggestionsContinuation() {
        val rawJson = """
            {
              "contents": {
                "twoColumnBrowseResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": [{
                            "musicResponsiveHeaderRenderer": {
                              "title": { "runs": [{ "text": "My Playlist" }] }
                            }
                          }]
                        }
                      }
                    }
                  }],
                  "secondaryContents": {
                    "sectionListRenderer": {
                      "contents": [{
                        "musicPlaylistShelfRenderer": {
                          "contents": [{
                            "musicResponsiveListItemRenderer": {
                              "playlistItemData": { "videoId": "vid1", "playlistSetVideoId": "set1" },
                              "flexColumns": [{
                                "musicResponsiveListItemFlexColumnRenderer": {
                                  "text": { "runs": [{ "text": "Track One" }] }
                                }
                              }],
                              "thumbnail": {
                                "musicThumbnailRenderer": {
                                  "thumbnail": { "thumbnails": [{ "url": "https://example.com/1.jpg", "width": 120, "height": 120 }] }
                                }
                              }
                            }
                          }]
                        }
                      }],
                      "continuations": [{
                        "nextContinuationData": { "continuation": "SUGGESTIONS_SECTION_TOKEN" }
                      }]
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val page = MetrolistInnerTubeClient.parsePlaylistBrowseResponse("PL123", rawJson)!!
        assertEquals(listOf("vid1"), page.songs.map { it.id })
        assertNull(page.songsContinuation)
        assertEquals("SUGGESTIONS_SECTION_TOKEN", page.continuation)
    }

    @Test
    fun extractPlaylistContinuationPage_ignoresSuggestedMusicShelfAndParsesPlaylistShelfContinuation() {
        val suggestionsJson = """
            {
              "continuationContents": {
                "sectionListContinuation": {
                  "contents": [{
                    "musicShelfRenderer": {
                      "contents": [{
                        "musicResponsiveListItemRenderer": {
                          "playlistItemData": { "videoId": "suggested_vid" },
                          "flexColumns": [{
                            "musicResponsiveListItemFlexColumnRenderer": {
                              "text": { "runs": [{ "text": "Random Suggested Song" }] }
                            }
                          }],
                          "thumbnail": {
                            "musicThumbnailRenderer": {
                              "thumbnail": { "thumbnails": [{ "url": "https://example.com/s.jpg", "width": 120, "height": 120 }] }
                            }
                          }
                        }
                      }]
                    }
                  }],
                  "continuations": [{
                    "nextContinuationData": { "continuation": "NEXT_SUGGESTIONS_TOKEN" }
                  }]
                }
              }
            }
        """.trimIndent()

        val suggestionsResponse = MetrolistInnerTubeClient.defaultJson.decodeFromString<BrowseResponse>(suggestionsJson)
        val suggestionsPage = MetrolistInnerTubeClient.extractPlaylistContinuationPage(suggestionsResponse)
        assertEquals(emptyList<String>(), suggestionsPage.songs.map { it.id })
        assertNull(suggestionsPage.continuation)

        val realContinuationJson = """
            {
              "continuationContents": {
                "musicPlaylistShelfContinuation": {
                  "contents": [{
                    "musicResponsiveListItemRenderer": {
                      "playlistItemData": { "videoId": "vid101", "playlistSetVideoId": "set101" },
                      "flexColumns": [{
                        "musicResponsiveListItemFlexColumnRenderer": {
                          "text": { "runs": [{ "text": "Real Track 101" }] }
                        }
                      }],
                      "thumbnail": {
                        "musicThumbnailRenderer": {
                          "thumbnail": { "thumbnails": [{ "url": "https://example.com/101.jpg", "width": 120, "height": 120 }] }
                        }
                      }
                    }
                  }],
                  "continuations": [{
                    "nextContinuationData": { "continuation": "PAGE_3_TOKEN" }
                  }]
                }
              }
            }
        """.trimIndent()

        val realResponse = MetrolistInnerTubeClient.defaultJson.decodeFromString<BrowseResponse>(realContinuationJson)
        val realPage = MetrolistInnerTubeClient.extractPlaylistContinuationPage(realResponse)
        assertEquals(listOf("vid101"), realPage.songs.map { it.id })
        assertEquals("PAGE_3_TOKEN", realPage.continuation)
    }
}
