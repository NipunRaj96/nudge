package com.nudge.app.data

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ParsedStop(val name: String, val time: LocalTime)
data class ParsedTimetable(val morning: List<ParsedStop>, val evening: List<ParsedStop>, val warnings: List<String>)

object TimetableParser {
    // hh:mm / h:mm / hh.mm, optional am/pm; not glued to other digits or dots/colons.
    private val TIME = Regex("""(?<![\d:.])(\d{1,2})[:.](\d{2})(?!\d)(?:\s*([AaPp])[Mm]\b)?""")
    private val FMT = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)
    private const val SEPARATORS = " \t-|,:;"

    fun parse(text: String): ParsedTimetable {
        val cols = listOf(mutableListOf<ParsedStop>(), mutableListOf<ParsedStop>())
        var prevRow: List<ParsedStop>? = null
        for (line in text.lines()) {
            val matches = TIME.findAll(line).mapNotNull { m ->
                val h = m.groupValues[1].toInt()
                val min = m.groupValues[2].toInt()
                val ap = m.groupValues[3].lowercase()
                val hour = when {
                    min > 59 -> return@mapNotNull null
                    ap.isEmpty() -> if (h > 23) return@mapNotNull null else h
                    h !in 1..12 -> return@mapNotNull null
                    else -> (h % 12) + if (ap == "p") 12 else 0
                }
                m to LocalTime.of(hour, min)
            }.take(2).toList()
            if (matches.isEmpty()) continue
            var start = 0
            val row = matches.map { (m, t) ->
                val name = clean(line.substring(start, m.range.first))
                start = m.range.last + 1
                ParsedStop(name, t)
            }
            if (row == prevRow) continue
            prevRow = row
            row.forEachIndexed { i, s -> if (s.name.isNotEmpty()) cols[i].add(s) }
        }
        val warnings = mutableListOf<String>()
        return ParsedTimetable(
            fixColumn(cols[0], "morning", warnings),
            fixColumn(cols[1], "evening", warnings),
            warnings,
        )
    }

    private fun clean(s: String) = s.trim(*SEPARATORS.toCharArray()).replace(Regex("\\s+"), " ").trim(*SEPARATORS.toCharArray())

    private fun fixColumn(stops: List<ParsedStop>, label: String, warnings: MutableList<String>): List<ParsedStop> {
        val out = mutableListOf<ParsedStop>()
        for (s in stops) {
            var t = s.time
            val prev = out.lastOrNull()?.time
            if (prev != null && t < prev) {
                val plus = t.plusHours(12)
                if (t.hour < 12 && plus >= prev) {
                    warnings += "Changed ${FMT.format(t)} to ${FMT.format(plus)} at ${s.name} ($label)"
                    t = plus
                } else {
                    warnings += "Time goes backwards at ${s.name}"
                }
            }
            out.add(ParsedStop(s.name, t))
        }
        return out
    }

    /** Lower-case, collapse whitespace, strip punctuation; used to match stop names across routes. */
    fun normalize(name: String): String =
        name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
