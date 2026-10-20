package com.claudecompanion

import com.claudecompanion.data.CompanionSettings
import com.claudecompanion.data.EventType
import com.claudecompanion.net.EventParser
import com.claudecompanion.net.NtfyMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventParserTest {
    // Lignes réellement produites par le serveur (server/) pour un hook Claude Code
    private val permissionLine = """{"id":"e1","time":1792500475,"event":"message","topic":"wce8njn2bmz5kpfx","message":"{\"t\":\"permission\",\"m\":\"Claude needs your permission to use Bash\",\"p\":\"mon-projet\",\"s\":\"abcdef12\",\"a\":\"MacBook\"}"}"""
    private val cloudLine = """{"id":"e2","time":1792500490,"event":"message","topic":"w9fc5939jntnm365","message":"{\"t\":\"done\",\"m\":\"ok\",\"p\":\"mon-depot\",\"s\":\"7652430b\",\"a\":\"Cloud perso\",\"c\":true,\"u\":\"https://claude.ai/code/session_016aBUrCQFd6JdJcmHdCzaBN\"}"}"""

    private fun event(line: String) = EventParser.toEvent(EventParser.parseLine(line)!!)!!

    private fun message(payload: String) = NtfyMessage(id = "id1", time = 1_760_000_000, event = "message", message = payload)

    @Test
    fun `lit un evenement du serveur`() {
        val event = event(permissionLine)
        assertEquals("e1", event.id)
        assertEquals(EventType.PERMISSION, event.type)
        assertEquals("mon-projet", event.project)
        assertEquals("abcdef12", event.session)
        assertEquals("MacBook", event.account)
        assertEquals("Claude needs your permission to use Bash", event.message)
        assertFalse(event.cloud)
    }

    @Test
    fun `reconnait une session cloud et son lien`() {
        val event = event(cloudLine)
        assertEquals(EventType.TASK_DONE, event.type)
        assertTrue(event.cloud)
        assertEquals("https://claude.ai/code/session_016aBUrCQFd6JdJcmHdCzaBN", event.url)
        assertEquals("☁️ mon-depot · Cloud perso", event.origin)
    }

    @Test
    fun `ignore un lien qui ne pointe pas vers claude ai`() {
        assertNull(EventParser.toEvent(message("""{"t":"done","c":true,"u":"https://evil.example/claude.ai/"}"""))!!.url)
    }

    @Test
    fun `filtre par ordinateur et par origine`() {
        val cloud = event(cloudLine)
        val local = event(permissionLine)
        assertFalse(CompanionSettings().isFiltered(cloud))
        assertTrue(CompanionSettings(cloudSessions = false).isFiltered(cloud))
        assertFalse(CompanionSettings(cloudSessions = false).isFiltered(local))
        assertTrue(CompanionSettings(localSessions = false).isFiltered(local))
        assertTrue(CompanionSettings(mutedAccounts = setOf("MacBook")).isFiltered(local))
    }

    @Test
    fun `conserve accents emoji et guillemets`() {
        val event = EventParser.toEvent(message("""{"t":"done","m":"Accents éàü & emoji ✅ \"guillemets\""}"""))!!
        assertEquals(EventType.TASK_DONE, event.type)
        assertNull(event.account)
        assertEquals("Accents éàü & emoji ✅ \"guillemets\"", event.message)
    }

    @Test
    fun `type inconnu et message vide`() {
        val event = EventParser.toEvent(message("""{"t":"nouveau_type","champ_futur":1}"""))!!
        assertEquals(EventType.OTHER, event.type)
        assertEquals(EventType.OTHER.label, event.message)
    }

    @Test
    fun `rejette un message illisible`() {
        assertNull(EventParser.toEvent(message("Claude needs your permission")))
        assertNull(EventParser.toEvent(message("")))
        assertNull(EventParser.toEvent(message("""{"m":"sans type"}""")))
    }

    @Test
    fun `ignore les evenements keepalive et open`() {
        assertNull(EventParser.parseLine("""{"id":"x","time":1,"event":"keepalive","topic":"t"}"""))
        assertNull(EventParser.parseLine("""{"id":"x","time":1,"event":"open","topic":"t"}"""))
        assertNull(EventParser.parseLine("pas du json"))
        assertEquals("id9", EventParser.parseLine("""{"id":"id9","time":1,"event":"message","topic":"t","message":"m"}""")?.id)
    }

    @Test
    fun `montre connectee seulement avec serveur identifiant et jeton`() {
        val connected = CompanionSettings(serverUrl = "https://cc.example.fr", deviceId = "wce8njn2bmz5kpfx", deviceToken = "ccd_x")
        assertTrue(connected.isConnected)
        assertFalse(connected.copy(serverUrl = "").isConnected)
        assertFalse(connected.copy(deviceToken = "").isConnected)
        assertFalse(connected.copy(deviceId = "../autre").isConnected)
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
}
