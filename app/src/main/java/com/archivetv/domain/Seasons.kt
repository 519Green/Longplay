package com.archivetv.domain

import java.time.LocalDate
import java.time.MonthDay

/** When a seasonal Home row shows. */
sealed interface SeasonWindow {
    /** Inclusive calendar dates; [from] after [to] wraps the year end (Dec 26 to Jan 6). */
    data class Dates(val from: MonthDay, val to: MonthDay) : SeasonWindow

    /** Days relative to Western Easter Sunday of the date's year, both ends inclusive. */
    data class Easter(val startOffset: Int, val endOffset: Int) : SeasonWindow
}

/**
 * One configured seasonal row: a calendar window and the catalog query that fills it. [community] lists
 * open-upload collections searched with the same query; with the rights filter on, an uploader's license does not
 * count for them (see RightsRule.communityClause).
 */
data class SeasonalEntry(
    val id: String,
    val title: String,
    val window: SeasonWindow,
    val query: CategoryQuery,
    val community: List<String> = emptyList(),
)

/** Western (Gregorian) Easter Sunday, by the anonymous Gregorian algorithm. */
fun easterSunday(year: Int): LocalDate {
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31
    val day = (h + l - 7 * m + 114) % 31 + 1
    return LocalDate.of(year, month, day)
}

fun isActive(window: SeasonWindow, date: LocalDate): Boolean = when (window) {
    is SeasonWindow.Dates -> {
        val today = MonthDay.from(date)
        if (window.from <= window.to) today >= window.from && today <= window.to
        else today >= window.from || today <= window.to
    }
    is SeasonWindow.Easter -> {
        val easter = easterSunday(date.year)
        !date.isBefore(easter.plusDays(window.startOffset.toLong())) &&
            !date.isAfter(easter.plusDays(window.endOffset.toLong()))
    }
}

/** The entries whose window contains [date], in config order. */
fun activeSeasons(date: LocalDate, entries: List<SeasonalEntry>): List<SeasonalEntry> =
    entries.filter { isActive(it.window, date) }
