package com.archivetv.data

import com.archivetv.data.api.MetadataFileDto
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayableFileSelectorTest {
    private val sel = PlayableFileSelector()

    @Test fun prefersMp4OverOtherFormats() {
        val files = listOf(
            MetadataFileDto(name = "movie.ogv", format = "Ogg Video"),
            MetadataFileDto(name = "movie.mp4", format = "h.264"),
        )
        assertThat(sel.select("id1", files)?.fileName).isEqualTo("movie.mp4")
    }

    @Test fun ignoresNonVideoAndDerivativeArtifacts() {
        val files = listOf(
            MetadataFileDto(name = "thumb.jpg", format = "JPEG"),
            MetadataFileDto(name = "movie.mp4", format = "h.264"),
            MetadataFileDto(name = "movie.gif", format = "Animated GIF"),
        )
        assertThat(sel.select("id1", files)?.fileName).isEqualTo("movie.mp4")
    }

    @Test fun fallsBackToOtherWebPlayableWhenNoMp4() {
        val files = listOf(MetadataFileDto(name = "movie.webm", format = "WebM"))
        assertThat(sel.select("id1", files)?.fileName).isEqualTo("movie.webm")
    }

    @Test fun prefersFullSizeH264Over512kbRegardlessOfOrder() {
        val files = listOf(
            MetadataFileDto(name = "film_512kb.mp4", format = "512Kb MPEG4", height = listOf("240")),
            MetadataFileDto(name = "film.ogv", format = "Ogg Video", height = listOf("304")),
            MetadataFileDto(name = "film.mp4", format = "h.264", height = listOf("480")),
        )
        assertThat(sel.select("id1", files)?.fileName).isEqualTo("film.mp4")
    }

    @Test fun prefersOriginalMpeg4Over512kb() {
        val files = listOf(
            MetadataFileDto(name = "film_512kb.mp4", format = "512Kb MPEG4"),
            MetadataFileDto(name = "film-original.mp4", format = "MPEG4"),
        )
        assertThat(sel.select("id1", files)?.fileName).isEqualTo("film-original.mp4")
    }

    @Test fun prefers512kbMp4OverOgg() {
        val files = listOf(
            MetadataFileDto(name = "film.ogv", format = "Ogg Video"),
            MetadataFileDto(name = "film_512kb.mp4", format = "512Kb MPEG4"),
        )
        assertThat(sel.select("id1", files)?.fileName).isEqualTo("film_512kb.mp4")
    }

    @Test fun withinSameFormatPrefersTallerVideo() {
        val files = listOf(
            MetadataFileDto(name = "sd.mp4", format = "h.264", height = listOf("480")),
            MetadataFileDto(name = "hd.mp4", format = "h.264", height = listOf("720")),
        )
        assertThat(sel.select("id1", files)?.fileName).isEqualTo("hd.mp4")
    }

    // --- selectAll: one entry per distinct video ---

    private fun f(
        name: String, format: String, original: String? = null,
        source: String = if (original != null) "derivative" else "original",
        length: String? = null, title: String? = null, height: String? = null,
    ) = MetadataFileDto(
        name = name, format = format, source = source, original = original,
        length = listOfNotNull(length), title = listOfNotNull(title), height = listOfNotNull(height),
    )

    @Test fun selectAllGroupsDerivativesByOriginalAndPicksBestOfEach() {
        val files = listOf(
            f("ep2.mpeg", "MPEG2"),
            f("ep2.mp4", "h.264", original = "ep2.mpeg", length = "1500.5"),
            f("ep2_512kb.mp4", "512Kb MPEG4", original = "ep2.mpeg"),
            f("ep1.mpeg", "MPEG2"),
            f("ep1.ogv", "Ogg Video", original = "ep1.mpeg"),
            f("ep1_512kb.mp4", "512Kb MPEG4", original = "ep1.mpeg"),
            f("thumb.jpg", "JPEG"),
        )
        val all = sel.selectAll("id", files)
        assertThat(all.map { it.fileName }).containsExactly("ep1_512kb.mp4", "ep2.mp4").inOrder()
        assertThat(all[1].durationSec).isWithin(0.01).of(1500.5)
    }

    @Test fun selectAllKeepsPlayableOriginalWhenItIsBest() {
        val files = listOf(
            f("show.mp4", "MPEG4"),
            f("show_512kb.mp4", "512Kb MPEG4", original = "show.mp4"),
        )
        assertThat(sel.selectAll("id", files).map { it.fileName }).containsExactly("show.mp4")
    }

    @Test fun selectAllSortsNaturally() {
        val names = listOf("Ep 10.mp4", "Ep 2.mp4", "Ep 1.mp4", "s01e10.mp4", "s01e02.mp4",
            "Season 2/Show - 001.mp4", "Season 1/Show - 002.mp4", "Season 1/Show - 010.mp4")
        val all = sel.selectAll("id", names.map { f(it, "h.264") })
        assertThat(all.map { it.fileName }).containsExactly(
            "Ep 1.mp4", "Ep 2.mp4", "Ep 10.mp4",
            "s01e02.mp4", "s01e10.mp4",
            "Season 1/Show - 002.mp4", "Season 1/Show - 010.mp4", "Season 2/Show - 001.mp4",
        ).inOrder()
    }

    @Test fun displayTitlePrefersFileTitleElseCleanedName() {
        val all = sel.selectAll("id", listOf(
            f("G:/Videos/Thundarr/Thundarr_the_Barbarian - S01E01 - Secret.mp4", "MPEG4"),
            f("chapter_02.mp4", "h.264", title = "Chapter Two: The Return"),
        ))
        assertThat(all.map { it.title }).containsExactly(
            "Thundarr the Barbarian - S01E01 - Secret", "Chapter Two: The Return",
        )
    }

    @Test fun durationParsesSecondsAndClockFormats() {
        assertThat(parseDurationSec("1500.53")).isWithin(0.01).of(1500.53)
        assertThat(parseDurationSec("25:30")).isWithin(0.01).of(1530.0)
        assertThat(parseDurationSec("1:02:03")).isWithin(0.01).of(3723.0)
        assertThat(parseDurationSec("abc")).isNull()
        assertThat(parseDurationSec(null)).isNull()
    }

    @Test fun selectReturnsFirstOfSelectAll() {
        val files = listOf(f("b.mp4", "h.264"), f("a.mp4", "h.264"))
        assertThat(sel.select("id", files)?.fileName).isEqualTo("a.mp4")
    }

    @Test fun episodeLabelsDropSharedPrefixAtASeparator() {
        assertThat(episodeLabels(listOf(
            "Thundarr the Barbarian - S01E01 - Secret", "Thundarr the Barbarian - S01E02 - Harvest",
        ))).containsExactly("S01E01 - Secret", "S01E02 - Harvest").inOrder()
        assertThat(episodeLabels(listOf("Green Acres - 001 - Oliver", "Green Acres - 002 - Lisa")))
            .containsExactly("001 - Oliver", "002 - Lisa").inOrder()
    }

    @Test fun episodeLabelsKeepTitlesWithoutUsefulPrefix() {
        assertThat(episodeLabels(listOf("Billy the Kid", "Ben Thompson")))
            .containsExactly("Billy the Kid", "Ben Thompson").inOrder()
        assertThat(episodeLabels(listOf("Only one"))).containsExactly("Only one")
        // Never strip a title down to nothing.
        assertThat(episodeLabels(listOf("Show - ", "Show - ")))
            .containsExactly("Show - ", "Show - ").inOrder()
    }

    @Test fun returnsNullWhenNoPlayableFile() {
        val files = listOf(MetadataFileDto(name = "scan.pdf", format = "PDF"))
        assertThat(sel.select("id1", files)).isNull()
    }

    // ---- audio ----

    private fun mp3(name: String, format: String = "VBR MP3", source: String = "original",
                    original: String? = null, title: String? = null, length: String? = null, track: String? = null) =
        MetadataFileDto(name = name, format = format, source = source, original = original,
            title = listOfNotNull(title), length = listOfNotNull(length), track = listOfNotNull(track))

    @Test fun librivoxPicksVbrMp3PerChapterAndSkipsM4b() {
        val files = listOf(
            MetadataFileDto(name = "Adventures of Sherlock Holmes_librivox.m4b", format = "Audiobook", source = "original"),
            mp3("adventureholmes_01_doyle.mp3", title = "01 - A Scandal in Bohemia", length = "3906.74"),
            mp3("adventureholmes_01_doyle.ogg", format = "Ogg Vorbis", source = "derivative", original = "adventureholmes_01_doyle.mp3"),
            mp3("adventureholmes_01_doyle_64kb.mp3", format = "64Kbps MP3", source = "derivative",
                original = "adventureholmes_01_doyle.mp3", title = "01 - A Scandal in Bohemia", length = "65:06"),
            mp3("adventureholmes_02_doyle.mp3", title = "02 - The Red-Headed League", length = "3546.64"),
            mp3("adventureholmes_02_doyle_64kb.mp3", format = "64Kbps MP3", source = "derivative",
                original = "adventureholmes_02_doyle.mp3", title = "02 - The Red-Headed League"),
        )
        val all = sel.selectAll("adventures_holmes", files)
        assertThat(all.map { it.fileName })
            .containsExactly("adventureholmes_01_doyle.mp3", "adventureholmes_02_doyle.mp3").inOrder()
        assertThat(all.map { it.title })
            .containsExactly("01 - A Scandal in Bohemia", "02 - The Red-Headed League").inOrder()
        assertThat(all[0].durationSec).isWithin(0.01).of(3906.74)
    }

    @Test fun flacAndMp3OriginalsWithSameStemGroup() {
        val files = listOf(
            MetadataFileDto(name = "Suzanne.flac", format = "Flac", source = "original", title = listOf("Suzanne")),
            mp3("Suzanne.mp3", title = "Suzanne"),
        )
        assertThat(sel.selectAll("cohen", files).map { it.fileName }).containsExactly("Suzanne.mp3")
    }

    @Test fun ordersByTrackWhenEveryFileHasDistinctTrack() {
        val files = listOf(
            mp3("Blessed Is The Memory.mp3", track = "12"),
            mp3("Master Song.mp3", track = "2"),
            mp3("Suzanne.mp3", track = "1"),
        )
        assertThat(sel.selectAll("cohen", files).map { it.fileName })
            .containsExactly("Suzanne.mp3", "Master Song.mp3", "Blessed Is The Memory.mp3").inOrder()
    }

    @Test fun fallsBackToNaturalOrderWhenTracksIncomplete() {
        val files = listOf(
            mp3("Footloose.mp3", track = "04"),
            mp3("CharlestonRes.mp3", track = "00"),
            mp3("CapriceViennois.mp3"),
        )
        assertThat(sel.selectAll("whiteman", files).map { it.fileName })
            .containsExactly("CapriceViennois.mp3", "CharlestonRes.mp3", "Footloose.mp3").inOrder()
    }

    @Test fun m4bOnlyItemPlaysTheM4b() {
        val files = listOf(
            MetadataFileDto(name = "book.m4b", format = "Audiobook", source = "original"),
            MetadataFileDto(name = "cover.jpg", format = "JPEG", source = "original"),
        )
        assertThat(sel.selectAll("book", files).map { it.fileName }).containsExactly("book.m4b")
    }

    @Test fun videoWinsWhenItemHasBoth() {
        val files = listOf(
            MetadataFileDto(name = "movie.mp4", format = "h.264"),
            mp3("soundtrack.mp3"),
        )
        assertThat(sel.selectAll("film", files).map { it.fileName }).containsExactly("movie.mp4")
        assertThat(sel.hasVideo(files)).isTrue()
        assertThat(sel.hasVideo(listOf(mp3("a.mp3")))).isFalse()
    }

    @Test fun audio64kbRanksBelowVbr() {
        val files = listOf(
            mp3("ep_64kb.mp3", format = "64Kbps MP3", source = "derivative", original = "ep.flac"),
            mp3("ep.mp3", source = "derivative", original = "ep.flac"),
            MetadataFileDto(name = "ep.flac", format = "Flac", source = "original"),
        )
        assertThat(sel.selectAll("ep", files).map { it.fileName }).containsExactly("ep.mp3")
    }
}
