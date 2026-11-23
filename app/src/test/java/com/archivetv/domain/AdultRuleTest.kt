package com.archivetv.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdultRuleTest {
    private fun adult(meta: AdultMeta) = AdultRule.isAdult(meta)

    @Test fun flagFields() {
        listOf("true", "gore", "1", "TRUE").forEach { assertThat(adult(AdultMeta(adult = listOf(it)))).isTrue() }
        listOf("false", "0", "no", "", " ").forEach { assertThat(adult(AdultMeta(adult = listOf(it)))).isFalse() }
        assertThat(adult(AdultMeta(nsfw = listOf("yes")))).isTrue()
        assertThat(adult(AdultMeta())).isFalse()
    }

    @Test fun ratingTokens() {
        listOf("R", "NC-17", "X", "TV-MA", "TV-MA;L", "Rated R", "tv-ma").forEach {
            assertThat(adult(AdultMeta(ratings = listOf(it)))).isTrue()
        }
        listOf("PG", "TV-PG", "PG-13;L", "TV-14-D", "PASSED", "G", "Graphic Violence; Language").forEach {
            assertThat(adult(AdultMeta(ratings = listOf(it)))).isFalse()
        }
    }

    @Test fun wholeTagsOnly() {
        listOf("Adult", "NSFW", "x-rated", "Adults Only", "hentai").forEach {
            assertThat(adult(AdultMeta(subjects = listOf(it)))).isTrue()
        }
        listOf("adult education", "young adult", "sex education", "nature", "adulthood").forEach {
            assertThat(adult(AdultMeta(subjects = listOf(it)))).isFalse()
        }
    }

    @Test fun splitSubjectValues() {
        assertThat(adult(AdultMeta(subjects = listOf("comedy; Nudity")))).isTrue()
        assertThat(adult(AdultMeta(subjects = listOf("drama, porn")))).isTrue()
        assertThat(adult(AdultMeta(subjects = listOf("comedy; adult education")))).isFalse()
    }

    @Test fun clauseText() {
        assertThat(AdultRule.clause()).isEqualTo(
            "(NOT adult:* AND NOT nsfw:* AND NOT rating:(R OR \"NC-17\" OR X OR \"TV-MA\") AND " +
                "NOT subject:(porn OR pornography OR xxx OR hentai OR nsfw OR sexploitation OR erotica))",
        )
    }
}
