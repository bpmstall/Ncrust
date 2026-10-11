package com.takahashirinta.ncrust.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligenceModeTest {

    @Test
    fun `parses standard songInfo payload correctly`() {
        val json = """
            {
                "code": 200,
                "data": [
                    {
                        "id": 101,
                        "recommended": true,
                        "songInfo": {
                            "id": 101,
                            "name": "Intelligence Track 1",
                            "ar": [{"id": 11, "name": "Artist 1"}],
                            "al": {"id": 21, "name": "Album 1", "picUrl": "https://p1.music.126.net/cover.jpg"},
                            "dt": 240000
                        }
                    }
                ]
            }
        """.trimIndent()

        val (direct, missingIds) = PlaylistApi.parseIntelligenceData(json)
        assertEquals(1, direct.size)
        assertTrue(missingIds.isEmpty())

        val song = direct.first()
        assertEquals(101L, song.id)
        assertEquals("Intelligence Track 1", song.name)
        assertEquals(1, song.artists?.size)
        assertEquals("Artist 1", song.artists?.first()?.name)
        assertEquals("Album 1", song.album?.name)
        assertEquals(240000L, song.duration)
    }

    @Test
    fun `parses legacy song field payload correctly`() {
        val json = """
            {
                "code": 200,
                "data": [
                    {
                        "id": 102,
                        "recommended": false,
                        "song": {
                            "id": 102,
                            "name": "Legacy Track",
                            "artists": [{"id": 12, "name": "Legacy Artist"}],
                            "album": {"id": 22, "name": "Legacy Album"},
                            "duration": 180000
                        }
                    }
                ]
            }
        """.trimIndent()

        val (direct, missingIds) = PlaylistApi.parseIntelligenceData(json)
        assertEquals(1, direct.size)
        assertTrue(missingIds.isEmpty())

        val song = direct.first()
        assertEquals(102L, song.id)
        assertEquals("Legacy Track", song.name)
        assertEquals("Legacy Artist", song.artists?.first()?.name)
        assertEquals(180000L, song.duration)
    }

    @Test
    fun `collects missing ids when item contains only id`() {
        val json = """
            {
                "code": 200,
                "data": [
                    {
                        "id": 999,
                        "recommended": true
                    }
                ]
            }
        """.trimIndent()

        val (direct, missingIds) = PlaylistApi.parseIntelligenceData(json)
        assertTrue(direct.isEmpty())
        assertEquals(listOf(999L), missingIds)
    }

    @Test
    fun `handles empty or invalid data gracefully`() {
        val (direct1, missing1) = PlaylistApi.parseIntelligenceData("{}")
        assertTrue(direct1.isEmpty())
        assertTrue(missing1.isEmpty())

        val (direct2, missing2) = PlaylistApi.parseIntelligenceData("""{"code": 200, "data": []}""")
        assertTrue(direct2.isEmpty())
        assertTrue(missing2.isEmpty())
    }
}
