package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackTest {

    @Test
    fun defaultTracks_areNotEmptyAndHaveValidAudioUrls() {
        val tracks = InnerTubeService.getDefaultTracks()
        assertTrue("Default tracks list should not be empty", tracks.isNotEmpty())
        assertEquals(6, tracks.size)

        for (track in tracks) {
            assertTrue("Title should not be blank", track.title.isNotBlank())
            assertTrue("Artist should not be blank", track.artist.isNotBlank())
            assertFalse("Artist should not be 'YouTube Music'", track.artist.equals("YouTube Music", ignoreCase = true))
            assertTrue("Album should not be blank", track.album.isNotBlank())
            assertFalse("Album should not be 'YouTube Music'", track.album.equals("YouTube Music", ignoreCase = true))
            assertTrue("AudioUrl should start with http", track.audioUrl.startsWith("http"))
            assertTrue("ThumbnailUrl should start with http", track.thumbnailUrl.startsWith("http"))
        }
    }

    @Test
    fun defaultTracks_filterWorks() {
        val filtered = InnerTubeService.getDefaultTracks("Daft")
        assertTrue(filtered.isNotEmpty())
        for (track in filtered) {
            val matches = track.title.contains("Daft", ignoreCase = true) ||
                    track.artist.contains("Daft", ignoreCase = true) ||
                    track.album.contains("Daft", ignoreCase = true)
            assertTrue(matches)
        }
    }

    @Test
    fun playerController_initialStateIsCorrect() {
        val controller = PlayerController()
        assertFalse(controller.isPlaying)
        assertFalse(controller.isBuffering)
        assertEquals(0f, controller.currentSeconds, 0.001f)
        assertEquals(0f, controller.totalSeconds, 0.001f)
        assertFalse(controller.isLoaded())
    }

    @Test
    fun artistGrid_trackerIdExtraction() {
        assertEquals("yetracker.net", ArtistGridService.extractTrackerId("yetracker.net"))
        assertEquals("1FyFFSiTma6zcqtuYaldoZg0HUoEip7xwDSEReL7JmH4", ArtistGridService.extractTrackerId("1FyFFSiTma6zcqtuYaldoZg0HUoEip7xwDSEReL7JmH4"))
        val pubHtml = "https://docs.google.com/spreadsheets/d/e/2PACX-1vR1234567890/pubhtml"
        assertEquals("2PACX-1vR1234567890", ArtistGridService.extractTrackerId(pubHtml))
    }
}

