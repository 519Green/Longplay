package com.archivetv.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HtmlTextTest {
    @Test fun plainTextPassesThrough() {
        assertThat(HtmlText.toPlain("Just a film.")).isEqualTo("Just a film.")
    }

    @Test fun breaksAndParagraphsBecomeNewlines() {
        assertThat(HtmlText.toPlain("<p>One</p><p>Two<br>Three</p>"))
            .isEqualTo("One\n\nTwo\nThree")
    }

    @Test fun tagsAreStripped() {
        assertThat(HtmlText.toPlain("""A <a href="x">link</a> and <b>bold</b>"""))
            .isEqualTo("A link and bold")
    }

    @Test fun entitiesAreDecoded() {
        assertThat(HtmlText.toPlain("Tom &amp; Jerry &quot;hi&quot; &#39;x&#39; &#x2014; &lt;3&gt;"))
            .isEqualTo("Tom & Jerry \"hi\" 'x' — <3>")
    }

    @Test fun whitespaceIsCollapsed() {
        assertThat(HtmlText.toPlain("  a \n\n\n\n  b\t\tc  ")).isEqualTo("a\n\nb c")
    }

    @Test fun scriptAndStyleContentIsDropped() {
        assertThat(HtmlText.toPlain("<style>p{}</style>Text<script>x()</script>"))
            .isEqualTo("Text")
    }
}
