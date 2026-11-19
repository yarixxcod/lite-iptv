package app.liteiptv.parse

import app.liteiptv.data.parse.M3uParser
import app.liteiptv.data.parse.XmltvParser
import app.liteiptv.data.parse.parseXmltvTime
import app.liteiptv.domain.ChannelDeduper
import app.liteiptv.domain.FavoritesLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ParserTest {
    private val m3u = """
        #EXTM3U url-tvg="http://epg.example/guide.xml.gz" catchup="append" catchup-days="5"
        #EXTINF:-1 tvg-id="news" tvg-name="News TV" tvg-logo="http://img/news.png" group-title="News, World" tvg-chno="4" catchup="shift" catchup-days="3" catchup-source="http://cu/{id}",News HD
        http://stream.example/news.ts
        #EXTGRP:Kids
        #EXTINF:-1 tvg-id="kids" tvg-logo='http://img/kids.png',Kids
        https://stream.example/kids.m3u8
        #EXTINF:-1,Duplicate News
        http://stream.example/news.ts
        #EXTINF:-1 tvg-id="news" tvg-logo="http://img/news2.png",News backup
        http://stream.example/news-backup.ts
        #EXTINF:0 group-title="Music",Radio
        rtsp://stream.example/radio
    """.trimIndent()

    @Test
    fun parsesAttributesGroupsAndHeader() {
        val parser = M3uParser()
        var epg = ""
        val all = ArrayList<app.liteiptv.data.parse.ParsedChannel>()
        val count = parser.parse(m3u.reader().buffered(), batchSize = 2, onHeader = {
            epg = it.epgUrls.single()
            assertEquals("append", it.catchup)
            assertEquals(5, it.catchupDays)
        }) { all += it }
        assertEquals("http://epg.example/guide.xml.gz", epg)
        assertEquals(5, count)
        assertEquals(5, all.size)
        val news = all.first()
        assertEquals("News HD", news.name)
        assertEquals("news", news.tvgId)
        assertEquals("News, World", news.group)
        assertEquals(4, news.number)
        assertEquals("shift", news.catchup)
        assertEquals(3, news.catchupDays)
        assertEquals("http://cu/{id}", news.catchupSource)
        assertEquals("http://img/news.png", news.logo)
        assertEquals("Kids", all[1].group)
        assertEquals("rtsp://stream.example/radio", all.last().url)
    }

    @Test
    fun streamsManyEntries() {
        val body = buildString {
            append("#EXTM3U\n")
            repeat(2_500) { i ->
                append("#EXTINF:-1 tvg-id=\"id$i\" group-title=\"G${i % 7}\",Channel $i\n")
                append("http://example/$i.ts\n")
            }
        }
        var batches = 0
        var total = 0
        val count = M3uParser().parse(body.reader().buffered(), batchSize = 400, onBatch = {
            batches++
            total += it.size
            assertTrue(it.size <= 400)
        })
        assertEquals(2_500, count)
        assertEquals(2_500, total)
        assertTrue(batches >= 7)
    }

    @Test
    fun dedupeKeepsAlternateUrls() {
        val parser = M3uParser()
        val parsed = ArrayList<app.liteiptv.data.parse.ParsedChannel>()
        parser.parse(m3u.reader().buffered()) { parsed += it }
        val deduper = ChannelDeduper()
        var n = 1
        val inserted = parsed.mapNotNull { ch ->
            when (val d = deduper.accept(ch, n)) {
                is ChannelDeduper.Decision.Insert -> {
                    if (ch.number == null) n++
                    d
                }
                ChannelDeduper.Decision.Skip -> null
            }
        }
        assertEquals(3, inserted.size)
        val alts = deduper.altUrls()
        assertEquals(listOf("http://stream.example/news-backup.ts"), alts["http://stream.example/news.ts"])
    }

    @Test
    fun xmltvWindowTimezoneAndEntities() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="c1"><display-name>One</display-name></channel>
              <!-- ignore me -->
              <programme start="20261119120000 +0000" stop="20261119130000 +0000" channel="c1">
                <title lang="en">News & Weather</title>
                <desc>Hello <world></desc>
              </programme>
              <programme start="20261119150000 +0300" stop="20261119160000 +0300" channel="c1">
                <title><![CDATA[Afternoon & Live]]></title>
              </programme>
              <programme start="20200101120000 +0000" stop="20200101130000 +0000" channel="old">
                <title>Ancient</title>
              </programme>
            </tv>
        """.trimIndent()
        val start = parseXmltvTime("20261119100000 +0000")!!
        val end = parseXmltvTime("20261119180000 +0000")!!
        val programs = ArrayList<app.liteiptv.data.parse.ParsedProgram>()
        val count = XmltvParser().parse(
            ByteArrayInputStream(xml.toByteArray()),
            windowStart = start,
            windowEnd = end,
            offsetMinutes = 0,
        ) { programs += it }
        assertEquals(2, count)
        assertEquals("News & Weather", programs[0].title)
        assertEquals("Hello <world>", programs[0].description)
        assertEquals("c1", programs[0].channelId)
        // 15:00 at +0300 is 12:00 UTC
        assertEquals(parseXmltvTime("20261119120000 +0000"), programs[1].startUtc)
        assertEquals("Afternoon & Live", programs[1].title)
        assertNull(programs.find { it.title == "Ancient" })
    }

    @Test
    fun playlistOffsetShiftsProgrammes() {
        val xml = """
            <tv>
              <programme start="20261119120000 +0000" stop="20261119130000 +0000" channel="a">
                <title>Noon</title>
              </programme>
            </tv>
        """.trimIndent()
        val programs = ArrayList<app.liteiptv.data.parse.ParsedProgram>()
        XmltvParser().parse(
            ByteArrayInputStream(xml.toByteArray()),
            windowStart = 0,
            windowEnd = Long.MAX_VALUE,
            offsetMinutes = 30,
        ) { programs += it }
        val base = parseXmltvTime("20261119120000 +0000")!!
        assertEquals(base + 30 * 60_000L, programs.single().startUtc)
    }

    @Test
    fun favoritesToggleAndReorder() {
        val added = FavoritesLogic.toggle(emptyList(), "a")
        assertEquals(listOf("a"), added)
        val both = FavoritesLogic.toggle(added, "b")
        assertEquals(listOf("a", "b"), both)
        val removed = FavoritesLogic.toggle(both, "a")
        assertEquals(listOf("b"), removed)
        val order = listOf("a", "b", "c", "d")
        assertEquals(listOf("a", "c", "b", "d"), FavoritesLogic.move(order, 2, 1))
        assertEquals(listOf("b", "c", "d", "a"), FavoritesLogic.move(order, 0, 3))
        assertEquals(order, FavoritesLogic.move(order, 1, 1))
        assertEquals(order, FavoritesLogic.move(order, 9, 0))
    }
}
