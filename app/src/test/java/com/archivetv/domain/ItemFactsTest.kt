package com.archivetv.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ItemFactsTest {
    @Test fun titleYearBeatsTheYearField() {
        assertThat(displayYear("Some Show S01-26 (1963)", "2022")).isEqualTo("1963")
        assertThat(displayYear("Another Show [1974] TV Series HD", "2024")).isEqualTo("1974")
        assertThat(displayYear("Third Show Complete Series (1979-1986)", "2023")).isEqualTo("1979")
    }

    @Test fun yearFieldShownOnlyWhenPlausible() {
        assertThat(displayYear("A Film", "1940")).isEqualTo("1940")
        assertThat(displayYear("Later Upload", "2025")).isNull()
        assertThat(displayYear("Odd", "1066")).isNull()
        assertThat(displayYear("Film", null)).isNull()
        assertThat(displayYear("Film", "1950-01-01")).isEqualTo("1950")
    }

    @Test fun tagsFromSoundAndColor() {
        assertThat(itemTags("silent", "B&W")).containsExactly("Silent", "B&W").inOrder()
        assertThat(itemTags("sound", "color")).containsExactly("Color")
        assertThat(itemTags(null, "Black and White")).containsExactly("B&W")
        assertThat(itemTags("Sound", "unknown")).isEmpty()
    }

    @Test fun averageIgnoresOutOfRangeStars() {
        assertThat(averageStars(listOf(5.0, 4.0, 0.0, 9.0))).isEqualTo(4.5 to 2)
        assertThat(averageStars(emptyList())).isEqualTo(null to 0)
    }

    @Test fun metadataLineJoinsPresentParts() {
        val line = metadataLine(ItemFacts("1940", 5490.0, false, 4.24, 30, listOf("B&W")))
        assertThat(line).isEqualTo("1940 · 1 h 31 min · ★ 4.2 (30) · B&W")
        assertThat(metadataLine(ItemFacts(runtimeSec = 1530.0, totalRuntime = true))).isEqualTo("25 min total")
        assertThat(metadataLine(ItemFacts())).isEmpty()
    }

    @Test fun moreLikeThisSkipsBroadAndCommunityCollections() {
        assertThat(moreLikeThisCollection(listOf("moviesandfilms", "Comedy_Films", "feature_films"))).isEqualTo("Comedy_Films")
        assertThat(moreLikeThisCollection(listOf("opensource_movies", "fav-someone"))).isNull()
        assertThat(moreLikeThisCollection(emptyList())).isNull()
    }
}
