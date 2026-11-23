package com.archivetv.domain

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.MonthDay
import org.junit.Test

class SeasonsTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun md(m: Int, day: Int) = MonthDay.of(m, day)
    private fun entry(id: String, window: SeasonWindow) = SeasonalEntry(id, id, window, CategoryQuery())

    @Test fun easterDates() {
        assertThat(easterSunday(2025)).isEqualTo(d("2025-04-20"))
        assertThat(easterSunday(2026)).isEqualTo(d("2026-04-05"))
        assertThat(easterSunday(2027)).isEqualTo(d("2027-03-28"))
    }

    @Test fun easterWindow() {
        val w = SeasonWindow.Easter(-7, 1)
        assertThat(isActive(w, d("2026-03-29"))).isTrue()
        assertThat(isActive(w, d("2026-04-06"))).isTrue()
        assertThat(isActive(w, d("2026-03-28"))).isFalse()
        assertThat(isActive(w, d("2026-04-07"))).isFalse()
    }

    @Test fun wrappingWindow() {
        val w = SeasonWindow.Dates(md(12, 26), md(1, 6))
        assertThat(isActive(w, d("2026-12-30"))).isTrue()
        assertThat(isActive(w, d("2027-01-03"))).isTrue()
        assertThat(isActive(w, d("2027-01-07"))).isFalse()
        assertThat(isActive(w, d("2026-12-25"))).isFalse()
    }

    @Test fun plainWindowIncludesBothEnds() {
        val w = SeasonWindow.Dates(md(11, 1), md(11, 30))
        assertThat(isActive(w, d("2026-11-01"))).isTrue()
        assertThat(isActive(w, d("2026-11-30"))).isTrue()
        assertThat(isActive(w, d("2026-12-01"))).isFalse()
    }

    @Test fun leapDay() {
        val w = SeasonWindow.Dates(md(2, 1), md(2, 29))
        assertThat(isActive(w, d("2028-02-29"))).isTrue()
        assertThat(isActive(w, d("2027-02-28"))).isTrue()
    }

    @Test fun overlapKeepsConfigOrder() {
        val christmas = entry("christmas", SeasonWindow.Dates(md(12, 1), md(12, 31)))
        val newYear = entry("new-year", SeasonWindow.Dates(md(12, 26), md(1, 6)))
        val halloween = entry("halloween", SeasonWindow.Dates(md(10, 1), md(10, 31)))
        assertThat(activeSeasons(d("2026-12-28"), listOf(christmas, halloween, newYear)).map { it.id })
            .containsExactly("christmas", "new-year").inOrder()
    }
}
