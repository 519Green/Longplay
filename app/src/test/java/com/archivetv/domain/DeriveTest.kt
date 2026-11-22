package com.archivetv.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeriveTest {
    private val shows = DeriveRule.TitleSeries(listOf("classic_tv"), "movies", sample = 1500, minItems = 4, limit = 40)
    private fun creators(minItems: Int) = DeriveRule.FieldValues("creator", listOf("prelinger"), "movies", 5000, minItems, 30)
    private val radio = DeriveRule.Subcollections("oldtimeradio", "audio", 40)

    private fun docs(title: String, n: Int, downloads: Int = 1) = List(n) { TitleDoc("$title$it", downloads) }

    @Test fun seriesKeyCutsAtSeparators() {
        assertThat(seriesKey("Dragnet - The Big Thief")).isEqualTo("dragnet")
        assertThat(seriesKey("Bonanza S01E01 A Rose for Lotta")).isEqualTo("bonanza")
        assertThat(seriesKey("The Dick Van Dyke Show: Sick Boy")).isEqualTo("dick van dyke show")
        assertThat(seriesKey("'Tales of Tomorrow' - Frankenstein")).isEqualTo("tales of tomorrow")
        assertThat(seriesKey("One Step Beyond Episode 12")).isEqualTo("one step beyond")
        assertThat(seriesKey("The Hypnotic Eye episode 10")).isEqualTo("hypnotic eye")
    }

    @Test fun seriesEntriesKeepRankAndLabel() {
        val entries = seriesEntries(
            docs("Dragnet - x", 5, 10) + docs("Bonanza - y", 4, 100) + docs("Lassie - z", 3, 1000),
            shows,
        )
        assertThat(entries.map { it.label }).containsExactly("Bonanza", "Dragnet").inOrder()
        assertThat(entries[0].query.titleAny).containsExactly("Bonanza")
        assertThat(entries[0].query.collections).containsExactly("classic_tv")
        assertThat(entries[0].query.mediatype).isEqualTo("movies")
    }

    @Test fun seriesLabelUsesMostFrequentCase() {
        val entries = seriesEntries(
            listOf(TitleDoc("DRAGNET - a", 1), TitleDoc("'Dragnet' - c", 1)) + docs("Dragnet - b", 3),
            shows,
        )
        assertThat(entries.map { it.label }).containsExactly("Dragnet")
    }

    @Test fun valueKeyNormalises() {
        assertThat(valueKey("Handy (Jam) Organization")).isEqualTo("jam handy")
        assertThat(valueKey("Encyclopaedia Britannica Films, Inc.")).isEqualTo("encyclopedia britannica")
        assertThat(valueKey("Chevrolet Division, General Motors Corporation")).isEqualTo("chevrolet")
        assertThat(valueKey("Unknown")).isEmpty()
        assertThat(valueKey("Internet Archive")).isEmpty()
        assertThat(valueKey("Filecoin Foundation for the Decentralized Web (FFDW) and Internet Archive")).isEmpty()
    }

    @Test fun valueEntriesMergeSpellings() {
        val values = List(6) { "Encyclopaedia Britannica Films" } + List(3) { "Encyclopedia Britannica" } +
            List(2) { "Encyclopaedia Britannica Educational Corporation" } +
            List(10) { "Coronet Instructional Films" } + List(2) { "Coronet" } + List(40) { "Unknown" }
        val entries = valueEntries(values, creators(minItems = 10))
        assertThat(entries.map { it.label })
            .containsExactly("Coronet Instructional Films", "Encyclopaedia Britannica Films").inOrder()
        assertThat(entries[1].query.creatorAny).containsExactly(
            "Encyclopaedia Britannica Educational Corporation", "Encyclopaedia Britannica Films", "Encyclopedia Britannica",
        )
        assertThat(entries[1].query.collections).containsExactly("prelinger")
    }

    @Test fun sponsorEntriesUseSponsorAny() {
        val rule = DeriveRule.FieldValues("sponsor", listOf("prelinger"), "movies", 5000, 5, 30)
        val values = List(3) { "Chevrolet Motor Company" } + List(3) { "Chevrolet Division, General Motors Corporation" }
        val entries = valueEntries(values, rule)
        assertThat(entries).hasSize(1)
        assertThat(entries[0].query.sponsorAny)
            .containsExactly("Chevrolet Division, General Motors Corporation", "Chevrolet Motor Company")
        assertThat(entries[0].query.creatorAny).isEmpty()
    }

    @Test fun ambiguousPrefixDoesNotMerge() {
        val values = List(5) { "National" } + List(6) { "National Film Board of Canada" } +
            List(6) { "National Geographic Society" }
        assertThat(valueEntries(values, creators(minItems = 5))).hasSize(3)
    }

    @Test fun subcollectionsDropFavAndEmpty() {
        val entries = subcollectionEntries(
            listOf(
                CollectionDoc("fav-x", "Favs", 90),
                CollectionDoc("radioindex", "Radio Index", 0),
                CollectionDoc("suspenseradio", "Suspense", 50),
                CollectionDoc("ytjdradio", "Yours Truly, Johnny Dollar", 70),
            ),
            radio,
        )
        assertThat(entries.map { it.key }).containsExactly("ytjdradio", "suspenseradio").inOrder()
        assertThat(entries[0].label).isEqualTo("Yours Truly, Johnny Dollar")
        assertThat(entries[0].query.collections).containsExactly("ytjdradio")
        assertThat(entries[0].query.mediatype).isEqualTo("audio")
    }

    @Test fun keysAreRouteSafe() {
        val safe = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
        val keys = seriesEntries(listOf(TitleDoc("'Tales of Tomorrow' - a", 1)) + docs("Ozzie & Harriet - b", 4), shows).map { it.key } +
            valueEntries(List(5) { "U.S. Office of War Information" }, creators(5)).map { it.key } +
            subcollectionEntries(listOf(CollectionDoc("the-big-story_project", "Big Story", 3)), radio).map { it.key }
        assertThat(keys).isNotEmpty()
        keys.forEach { assertThat(it).matches(safe.pattern) }
        assertThat(slug("Ozzie & Harriet")).isEqualTo("ozzie-harriet")
    }

    @Test fun labelsReadNaturally() {
        assertThat(displayLabel("Handy (Jam) Organization")).isEqualTo("Jam Handy Organization")
        assertThat(displayLabel("duPont (E.I.) de Nemours & Company")).isEqualTo("E.I. duPont de Nemours & Company")
        assertThat(displayLabel("National Geographic Society (U.S.)")).isEqualTo("National Geographic Society (U.S.)")
        assertThat(displayLabel("STORIES OF THE CENTURY")).isEqualTo("Stories of the Century")
        assertThat(displayLabel("Hill street blues")).isEqualTo("Hill Street Blues")
        assertThat(displayLabel("WKRP In Cincinnati")).isEqualTo("WKRP In Cincinnati")
        assertThat(displayLabel("NASA")).isEqualTo("NASA")
        val studios = valueEntries(List(10) { "Handy (Jam) Organization" }, creators(10))
        assertThat(studios.single().label).isEqualTo("Jam Handy Organization")
        assertThat(studios.single().query.creatorAny).containsExactly("Handy (Jam) Organization")
        val series = seriesEntries(docs("STORIES OF THE CENTURY - x", 4), shows)
        assertThat(series.single().label).isEqualTo("Stories of the Century")
        assertThat(series.single().query.titleAny).containsExactly("STORIES OF THE CENTURY")
    }
}
