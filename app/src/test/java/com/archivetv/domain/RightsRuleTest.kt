package com.archivetv.domain

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class RightsRuleTest {
    private val day = LocalDate.of(2026, 11, 22)

    @Test fun cutoffMovesWithTheYear() {
        assertThat(RightsRule.cutoffYear(day)).isEqualTo(1930)
        assertThat(RightsRule.cutoffYear(LocalDate.of(2027, 1, 1))).isEqualTo(1931)
    }

    @Test fun anyLicenseUrlPasses() {
        assertThat(RightsRule.passes(RightsMeta(licenseurl = "http://creativecommons.org/licenses/by/3.0/"), day)).isTrue()
    }

    @Test fun oldDatesPassJunkAndNewDatesFail() {
        assertThat(RightsRule.passes(RightsMeta(years = listOf("1930")), day)).isTrue()
        assertThat(RightsRule.passes(RightsMeta(dates = listOf("1925-03-01")), day)).isTrue()
        assertThat(RightsRule.passes(RightsMeta(years = listOf("1931")), day)).isFalse()
        assertThat(RightsRule.passes(RightsMeta(years = listOf("1879")), day)).isFalse()
        assertThat(RightsRule.passes(RightsMeta(years = listOf("0000")), day)).isFalse()
        assertThat(RightsRule.passes(RightsMeta(years = listOf("2009")), day)).isFalse()
        assertThat(RightsRule.passes(RightsMeta(dates = listOf("c. 1920")), day)).isFalse()
    }

    @Test fun governmentProducers() {
        listOf(
            "U.S. Example Army Command", "U. S. Office of Example Affairs", "US Example Navy Unit", "United States Example Agency",
            "U.S. Department of Example",
        ).forEach { assertThat(RightsRule.isGovernmentProducer(it)).isTrue() }
        listOf(
            "U.S. Example Steel Corporation", "United States Example Rubber Company", "Example State Department of Health",
            "National Association of Examples", "Universal Example Pictures",
        ).forEach { assertThat(RightsRule.isGovernmentProducer(it)).isFalse() }
    }

    @Test fun governmentInAnyProducerFieldPasses() {
        assertThat(RightsRule.passes(RightsMeta(sponsors = listOf("U.S. Office of Example")), day)).isTrue()
        assertThat(RightsRule.passes(RightsMeta(publishers = listOf("U.S. Example Army Center")), day)).isTrue()
        assertThat(RightsRule.passes(RightsMeta(creators = listOf("U.S. Example Steel Corporation")), day)).isFalse()
    }

    @Test fun nothingKnownFails() {
        assertThat(RightsRule.passes(RightsMeta(), day)).isFalse()
    }

    @Test fun clauseCarriesTheCutoffAndAllThreeTests() {
        val c = RightsRule.clause(day)
        assertThat(c).contains("licenseurl:*")
        assertThat(c).contains("year:[1880 TO 1930]")
        assertThat(c).contains("date:[1880-01-01 TO 1930-12-31]")
        assertThat(c).contains("creator:(")
        assertThat(c).contains("publisher:(")
        assertThat(c).contains("sponsor:(")
    }

    @Test fun ageClauseIsTheDateTestAlone() {
        assertThat(RightsRule.ageClause(day)).isEqualTo("(year:[1880 TO 1930] OR date:[1880-01-01 TO 1930-12-31])")
    }

    @Test fun communityClauseIgnoresLicensesAndKeepsAge() {
        // Archive search mishandles "NOT x" nested in an OR, hence the explicit match-all.
        assertThat(RightsRule.communityClause(day)).isEqualTo(
            "((*:* -licenseurl:*) OR year:[1880 TO 1930] OR date:[1880-01-01 TO 1930-12-31])",
        )
    }
}
