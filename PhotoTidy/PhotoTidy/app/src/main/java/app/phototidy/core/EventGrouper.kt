package app.phototidy.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Finds "events": bursts of camera photos taken close together in time
 * (a trip, a party, a hike). A run of photos with no gap longer than
 * [maxGapMs] that has at least [minPhotos] pictures becomes one event.
 */
object EventGrouper {

    data class Shot(val id: Long, val dateTakenMs: Long)

    data class Event(val key: String, val defaultName: String, val photoIds: List<Long>)

    fun group(
        shots: List<Shot>,
        minPhotos: Int = 15,
        maxGapMs: Long = 3 * 3_600_000L,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Event> {
        val sorted = shots.sortedBy { it.dateTakenMs }
        val events = mutableListOf<Event>()
        var run = mutableListOf<Shot>()

        fun close() {
            if (run.size >= minPhotos) {
                val start = Instant.ofEpochMilli(run.first().dateTakenMs).atZone(zone).toLocalDate()
                val end = Instant.ofEpochMilli(run.last().dateTakenMs).atZone(zone).toLocalDate()
                val day = DateTimeFormatter.ISO_LOCAL_DATE
                val name = if (start == end) "${start.format(day)} Event"
                else "${start.format(day)} to ${end.format(day)} Event"
                events += Event(
                    key = "event:${run.first().id}",
                    defaultName = name,
                    photoIds = run.map { it.id },
                )
            }
            run = mutableListOf()
        }

        for (s in sorted) {
            if (run.isNotEmpty() && s.dateTakenMs - run.last().dateTakenMs > maxGapMs) close()
            run.add(s)
        }
        close()
        return events
    }

    /** Folder names may not contain path separators or odd characters. */
    fun safeFolderName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), " ").replace(Regex("\\s+"), " ").trim().take(80)
            .ifEmpty { "Event" }
}
