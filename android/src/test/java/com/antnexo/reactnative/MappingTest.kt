package com.antnexo.reactnative

import com.antnexo.AntNexoEvent
import com.antnexo.core.AntNexoException
import com.antnexo.core.FeedbackOptions
import com.antnexo.core.FeedbackTab
import com.antnexo.core.FeedbackTheme
import com.antnexo.core.WhatsNewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class MappingTest {
    @Test
    fun everyOption() {
        val o = feedbackOptions(
            mapOf(
                "tab" to "roadmap", "tabs" to listOf("new", "roadmap"), "boards" to listOf("ideas"),
                "theme" to "dark", "color" to "#4f46e5", "prompts" to false, "whatsNew" to "off", "language" to "de",
            )
        )
        assertEquals(
            FeedbackOptions(
                tab = FeedbackTab.ROADMAP, tabs = listOf(FeedbackTab.NEW, FeedbackTab.ROADMAP), boards = listOf("ideas"),
                theme = FeedbackTheme.DARK, color = "#4f46e5", prompts = false, whatsNew = WhatsNewMode.OFF, language = "de",
            ),
            o,
        )
        assertEquals(FeedbackOptions(), feedbackOptions(emptyMap()))
    }

    @Test
    fun unknownValuesAreLeftOutNotThrown() {
        val o = feedbackOptions(mapOf("tab" to "home", "theme" to 3, "tabs" to listOf("new", "x"), "prompts" to "on"))
        assertNull(o.tab)
        assertNull(o.theme)
        assertEquals(listOf(FeedbackTab.NEW), o.tabs)
        assertNull(o.prompts)
    }

    @Test
    fun tabs() {
        assertEquals(FeedbackTab.CHANGELOG, tabOf("changelog"))
        assertNull(tabOf(null))
        assertNull(tabOf("home"))
    }

    @Test
    fun events() {
        assertEquals(mapOf("type" to "posted", "id" to "x"), eventMap(AntNexoEvent.Posted("x")))
        assertEquals(mapOf("type" to "closed"), eventMap(AntNexoEvent.Closed))
        assertEquals(mapOf("type" to "unread", "count" to 4), eventMap(AntNexoEvent.Unread(4)))
        assertEquals(mapOf("type" to "signed_in", "signedIn" to true), eventMap(AntNexoEvent.SignedIn(true)))
        assertEquals(mapOf("type" to "identify_failed", "code" to "expired"), eventMap(AntNexoEvent.IdentifyFailed("expired")))
    }

    @Test
    fun errors() {
        assertEquals("http" to 503, errorCode(AntNexoException.Http(503)))
        assertEquals("invalid_host" to null, errorCode(AntNexoException.InvalidHost()))
        assertEquals("invalid_workspace" to null, errorCode(AntNexoException.InvalidWorkspace()))
        assertEquals("not_configured" to null, errorCode(AntNexoException.NotConfigured()))
        assertEquals("bad_response" to null, errorCode(AntNexoException.BadResponse()))
        assertEquals("network" to null, errorCode(IOException("offline")))
        assertEquals("network" to null, errorCode(IllegalStateException()))
    }
}
