package com.claudecompanion

import com.claudecompanion.data.CompanionSettings
import com.claudecompanion.data.EventType
import com.claudecompanion.net.EventParser
import com.claudecompanion.net.NtfyMessage
import com.claudecompanion.net.PayloadCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventParserTest {
    private val key = "abcdefghijkmnpqrstuvwxyz23"

    // Messages réellement produits par hooks/claude-companion-notify.sh (openssl)
    private val fromHookPermission = "v1.013d547beae70c0c99519b4081935cda.C57lN2u2tS7LWacnI+bB81Olm6LFEB7JloRLqKOX4DP6JhayW/m7SN3HB/85dlLC6Xt1JcRnkAhTmdbUaZaqEBkJ/aCUnL8RBbp7GP1VWBBr/Nqlbr5Tt6lo/q1T9zJ3j5jYL65oFGBnCbFqAyZ4zZwJp0MLlT5pDnmjNAo5obY=.9f1a9fe94d4434e4c2517d17099d489b67f207df7796b35de088270313ccbb11"
    private val fromHookManual = "v1.d692b7c967423f0bbaf8c8adb5e884a0.FC/VW7H/yFQKK1VIrc+kTMEI8MppwPtSzRMlm+hp8+sq1PCA5RjYHIPLTnOa9bYb8I3LUtrDgT1xmRc5rAAeHoJZv/QNEFzHPN8cb1Moulu+4oW9DfAh7JxtiYiOATCn.6cd4fd75129e20e33b1596f73c6b11dec28276a0d314491c523cc10d8f467d3d"

    private fun ntfy(message: String) = NtfyMessage(id = "id1", time = 1_760_000_000, event = "message", message = message)

    @Test
    fun `dechiffre un message produit par le hook`() {
        val event = EventParser.toEvent(ntfy(fromHookPermission), key)!!
        assertEquals(EventType.PERMISSION, event.type)
        assertEquals("mon-projet", event.project)
        assertEquals("abcdef12", event.session)
        assertEquals("cyril@example.fr", event.account)
        assertEquals("Claude needs your permission to use Bash", event.message)
    }

    @Test
    fun `conserve accents emoji et guillemets`() {
        val event = EventParser.toEvent(ntfy(fromHookManual), key)!!
        assertEquals(EventType.TASK_DONE, event.type)
        assertEquals("perso", event.account)
        assertEquals("Accents éàü & emoji ✅ \"guillemets\"", event.message)
    }

    @Test
    fun `rejette un message signe avec une autre cle`() {
        assertNull(EventParser.toEvent(ntfy(fromHookPermission), "uneautrecledunautreutilisa"))
    }

    @Test
    fun `rejette un message modifie ou en clair`() {
        val tampered = fromHookPermission.replaceRange(40, 41, if (fromHookPermission[40] == 'A') "B" else "A")
        assertNull(EventParser.toEvent(ntfy(tampered), key))
        assertNull(EventParser.toEvent(ntfy("Claude needs your permission"), key))
        assertNull(EventParser.toEvent(ntfy(fromHookPermission), ""))
    }

    @Test
    fun `aller retour chiffrement`() {
        val payload = PayloadCrypto.encrypt("""{"t":"input","m":"Salut"}""", key)
        val event = EventParser.toEvent(ntfy(payload), key)!!
        assertEquals(EventType.INPUT_NEEDED, event.type)
        assertNull(event.account)
    }

    @Test
    fun `ignore les evenements keepalive et open`() {
        assertNull(EventParser.parseLine("""{"id":"x","time":1,"event":"keepalive","topic":"t"}"""))
        assertNull(EventParser.parseLine("""{"id":"x","time":1,"event":"open","topic":"t"}"""))
        assertNull(EventParser.parseLine("pas du json"))
        assertEquals("id9", EventParser.parseLine("""{"id":"id9","time":1,"event":"message","topic":"t","message":"m"}""")?.id)
    }

    @Test
    fun `heures calmes qui traversent minuit`() {
        assertTrue(CompanionSettings.isInQuietHours(23, 22, 7))
        assertTrue(CompanionSettings.isInQuietHours(3, 22, 7))
        assertFalse(CompanionSettings.isInQuietHours(12, 22, 7))
        assertTrue(CompanionSettings.isInQuietHours(13, 12, 14))
        assertFalse(CompanionSettings.isInQuietHours(14, 12, 14))
    }

    @Test
    fun `les urgences passent pendant les heures calmes si autorise`() {
        val s = CompanionSettings(quietHoursEnabled = true, quietStart = 22, quietEnd = 7, quietAllowsUrgent = true)
        assertFalse(s.isMuted(EventType.PERMISSION, 23))
        assertTrue(s.isMuted(EventType.TASK_DONE, 23))
        assertFalse(s.isMuted(EventType.TASK_DONE, 12))
    }

    @Test
    fun `code d'appairage`() {
        assertEquals("claude-companion-ab12.$key", CompanionSettings(topic = "claude-companion-ab12", secret = key).pairingCode)
        assertTrue(CompanionSettings.TOPIC_REGEX.matches("claude-companion-ab12_x"))
        assertFalse(CompanionSettings.TOPIC_REGEX.matches("a/b"))
    }
}
