package com.archivetv.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveQueriesTest {
    @Test fun thisDayZeroPads() {
        assertThat(LiveQueries.thisDay(3, 5))
            .isEqualTo("collection:(etree) AND mediatype:etree AND title:\"-03-05\"")
        assertThat(LiveQueries.thisDay(11, 21)).endsWith("title:\"-11-21\"")
    }

    @Test fun artistYearQuery() {
        assertThat(LiveQueries.artistYear("GratefulDead", 1977))
            .isEqualTo("collection:(GratefulDead) AND mediatype:etree AND year:1977")
    }

    @Test fun concertQuery() {
        assertThat(LiveQueries.concert("GratefulDead", "1977-05-08"))
            .isEqualTo("collection:(GratefulDead) AND mediatype:etree AND date:[1977-05-08 TO 1977-05-08]")
    }

    @Test fun artistIdIsSanitized() {
        assertThat(LiveQueries.artistRecordings("bad id)")).isEqualTo("collection:(badid) AND mediatype:etree")
    }

    @Test fun artistSearchEmptyForSyntaxOnly() {
        assertThat(LiveQueries.artistSearch("()")).isEmpty()
        assertThat(LiveQueries.recordingSearch("  ")).isEmpty()
    }

    @Test fun searchQueriesUseTitleTerms() {
        assertThat(LiveQueries.artistSearch("grateful dead"))
            .isEqualTo("collection:(etree) AND mediatype:collection AND title:(grateful dead)")
        assertThat(LiveQueries.recordingSearch("cornell 1977"))
            .isEqualTo("collection:(etree) AND mediatype:etree AND title:(cornell 1977)")
    }
}
