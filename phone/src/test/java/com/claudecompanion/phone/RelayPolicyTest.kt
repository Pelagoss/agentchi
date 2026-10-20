package com.claudecompanion.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayPolicyTest {
    @Test
    fun `ne relaie que les notifications utiles de l'appli Claude`() {
        assertTrue(RelayPolicy.shouldRelay("com.anthropic.claude", ongoing = false, groupSummary = false, hasContent = true))
        assertFalse(RelayPolicy.shouldRelay("com.whatsapp", ongoing = false, groupSummary = false, hasContent = true))
        assertFalse(RelayPolicy.shouldRelay("com.anthropic.claude", ongoing = true, groupSummary = false, hasContent = true))
        assertFalse(RelayPolicy.shouldRelay("com.anthropic.claude", ongoing = false, groupSummary = true, hasContent = true))
        assertFalse(RelayPolicy.shouldRelay("com.anthropic.claude", ongoing = false, groupSummary = false, hasContent = false))
    }

    @Test
    fun `empreinte stable pour une mise a jour identique`() {
        val a = RelayedNotification(key = "k", title = "t", text = "x", time = 1)
        assertEquals(RelayPolicy.fingerprint(a), RelayPolicy.fingerprint(a.copy(time = 2)))
        assertNotEquals(RelayPolicy.fingerprint(a), RelayPolicy.fingerprint(a.copy(text = "y")))
    }

    @Test
    fun `format JSON echange avec la montre`() {
        val n = RelayedNotification(key = "0|com.anthropic.claude|1|null|10", title = "Session", text = "Fini", time = 5)
        val json = relayJson.encodeToString(RelayedNotification.serializer(), n)
        assertEquals("""{"key":"0|com.anthropic.claude|1|null|10","title":"Session","text":"Fini","time":5}""", json)
    }

    @Test
    fun `ne masque sur le telephone que si la montre a recu la notification`() {
        assertTrue(RelayPolicy.shouldHideOnPhone(hideEnabled = true, deliveredToWatches = 1))
        assertFalse(RelayPolicy.shouldHideOnPhone(hideEnabled = true, deliveredToWatches = 0))
        assertFalse(RelayPolicy.shouldHideOnPhone(hideEnabled = false, deliveredToWatches = 1))
    }
}
