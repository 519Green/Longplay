package com.archivetv.data.config

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BrowseConfigLoaderTest {
    @Test fun parsesRowsFromJson() {
        val json = """{"rows":[{"title":"Classic Cinema","collectionId":"feature_films"}]}"""
        val rows = BrowseConfigLoader().parse(json)
        assertThat(rows).hasSize(1)
        assertThat(rows[0].title).isEqualTo("Classic Cinema")
        assertThat(rows[0].collectionId).isEqualTo("feature_films")
        assertThat(rows[0].items).isEmpty()
    }
}
