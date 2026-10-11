package com.nudge.app

import com.nudge.app.data.TimetableParser
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableParserTest {
    // Shaped like a real operator timetable PDF's text: two columns per line, footer with the operator's details,
    // and an "AM" typo in the evening column.
    private val sample = """
MORNING PICK POINT M - PICKUP TIME EVENING PICK POINT E - PICKUP TIME
Green Valley Apartments 07:42 AM Tech Park Tower B 05:00 PM
Lakeview Residency 07:43 AM Orchid Business Centre 05:02 PM
Sunrise Heights Opp 07:44 AM Central Business Park 05:20 PM
Palm Grove Gate No - 1 Phase 2 07:50 AM Riverside Internatioal School 132 05:31 PM
Market Chowk Noida 08:04 AM Infotech Sector 126 (Gate no 1) 05:43 PM
CNG Gas Station Sector 121 Noida 08:10 AM Summit Corporate 05:45 PM
Apex /Meridian 08:32 AM Infotech Sector 125 05:56 PM
Summit Corporate 08:44 AM CNG Gas Station Sector 121 Noida 06:35 AM
Infotech Sector 126 Gate No - 1 08:46 AM Market Chowk Noida 06:45 PM
Tech Park Tower B 09:17 AM Green global 07:20 PM
 Acme Shuttle Service
 Office No. 1, Sector 12 Noida 201301
 www.example.com | +91-9000000001 | +91-9000000002
"""

    @Test
    fun fullSample() {
        val r = TimetableParser.parse(sample)
        assertEquals(10, r.morning.size)
        assertEquals(10, r.evening.size)
        assertEquals("Green Valley Apartments", r.morning.first().name)
        assertEquals(LocalTime.of(7, 42), r.morning.first().time)
        assertEquals("Tech Park Tower B", r.morning.last().name)
        assertEquals(LocalTime.of(9, 17), r.morning.last().time)
        assertEquals("Tech Park Tower B", r.evening.first().name)
        assertEquals(LocalTime.of(17, 0), r.evening.first().time)
        assertEquals("Green global", r.evening.last().name)
        assertEquals(LocalTime.of(19, 20), r.evening.last().time)
        assertEquals("Palm Grove Gate No - 1 Phase 2", r.morning[3].name)
        assertEquals("Riverside Internatioal School 132", r.evening[3].name)
        assertEquals("Infotech Sector 126 (Gate no 1)", r.evening[4].name)
        assertEquals("Apex /Meridian", r.morning[6].name)
        val fixed = r.evening[7]
        assertEquals("CNG Gas Station Sector 121 Noida", fixed.name)
        assertEquals(LocalTime.of(18, 35), fixed.time)
        assertEquals(
            listOf("Changed 06:35 AM to 06:35 PM at CNG Gas Station Sector 121 Noida (evening)"),
            r.warnings,
        )
        assertTrue(r.morning.none { it.name.contains("Office") || it.name.contains("Acme") })
    }

    @Test
    fun normalizeMatches() {
        assertEquals(TimetableParser.normalize("Apex /Meridian"), TimetableParser.normalize("apex  meridian"))
        assertEquals("apex meridian", TimetableParser.normalize("Apex /Meridian"))
    }

    @Test
    fun singleColumnAndFormats() {
        val r = TimetableParser.parse("A Stop 7.42 am\nA Stop 7.42 am\nB Stop 07:50AM -\nC Stop 17:05")
        assertEquals(3, r.morning.size)
        assertTrue(r.evening.isEmpty())
        assertEquals(LocalTime.of(7, 42), r.morning[0].time)
        assertEquals(LocalTime.of(7, 50), r.morning[1].time)
        assertEquals("B Stop", r.morning[1].name)
        assertEquals(LocalTime.of(17, 5), r.morning[2].time)
        assertTrue(r.warnings.isEmpty())
    }

    @Test
    fun backwardsWarning() {
        val r = TimetableParser.parse("A 10:00 PM\nB 09:00 PM")
        assertEquals(2, r.morning.size)
        assertEquals(listOf("Time goes backwards at B"), r.warnings)
    }
}
