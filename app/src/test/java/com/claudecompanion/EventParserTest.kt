package com.claudecompanion

import com.claudecompanion.data.CompanionSettings
import com.claudecompanion.data.EventType
import com.claudecompanion.net.EventParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventParserTest {
    // Données de push FCM réellement produites par le serveur (server/) pour un hook Claude Code
    private val permissionPush = mapOf(
        "id" to "e1",
        "time" to "1792500475",
        "payload" to """{"t":"permission","m":"Claude needs your permission to use Bash","p":"mon-projet","s":"abcdef12","a":"MacBook"}""",
    )
    private val cloudPush = mapOf(
        "id" to "e2",
        "time" to "1792500490",
        "payload" to """{"t":"done","m":"ok","p":"mon-depot","s":"7652430b","a":"Cloud perso","c":true,"u":"https://claude.ai/code/session_016aBUrCQFd6JdJcmHdCzaBN"}""",
    )

    private fun event(data: Map<String, String>) = EventParser.fromPush(data)!!

    private fun message(payload: String) = EventParser.toEvent("id1", 1_760_000_000, payload)

    @Test
    fun `lit un push du serveur`() {
        val event = event(permissionPush)
        assertEquals("e1", event.id)
        assertEquals(1_792_500_475L, event.time)
        assertEquals(EventType.PERMISSION, event.type)
        assertEquals("mon-projet", event.project)
        assertEquals("abcdef12", event.session)
        assertEquals("MacBook", event.account)
        assertEquals("Claude needs your permission to use Bash", event.message)
        assertFalse(event.cloud)
    }

    @Test
    fun `reconnait une session cloud et son lien`() {
        val event = event(cloudPush)
        assertEquals(EventType.TASK_DONE, event.type)
        assertTrue(event.cloud)
        assertEquals("https://claude.ai/code/session_016aBUrCQFd6JdJcmHdCzaBN", event.url)
        assertEquals("☁️ mon-depot · Cloud perso", event.origin)
    }

    @Test
    fun `ignore un lien qui ne pointe pas vers claude ai`() {
        assertNull(message("""{"t":"done","c":true,"u":"https://evil.example/claude.ai/"}""")!!.url)
    }

    @Test
    fun `filtre par ordinateur et par origine`() {
        val cloud = event(cloudPush)
        val local = event(permissionPush)
        assertFalse(CompanionSettings().isFiltered(cloud))
        assertTrue(CompanionSettings(cloudSessions = false).isFiltered(cloud))
        assertFalse(CompanionSettings(cloudSessions = false).isFiltered(local))
        assertTrue(CompanionSettings(localSessions = false).isFiltered(local))
        assertTrue(CompanionSettings(mutedAccounts = setOf("MacBook")).isFiltered(local))
    }

    @Test
    fun `conserve accents emoji et guillemets`() {
        val event = message("""{"t":"done","m":"Accents éàü & emoji ✅ \"guillemets\""}""")!!
        assertEquals(EventType.TASK_DONE, event.type)
        assertNull(event.account)
        assertEquals("Accents éàü & emoji ✅ \"guillemets\"", event.message)
    }

    @Test
    fun `type inconnu et message vide`() {
        val event = message("""{"t":"nouveau_type","champ_futur":1}""")!!
        assertEquals(EventType.OTHER, event.type)
        assertEquals(EventType.OTHER.label, event.message)
    }

    @Test
    fun `rejette un message illisible`() {
        assertNull(message("Claude needs your permission"))
        assertNull(message(""))
        assertNull(message("""{"m":"sans type"}"""))
    }

    @Test
    fun `rejette un push incomplet`() {
        assertNull(EventParser.fromPush(permissionPush - "id"))
        assertNull(EventParser.fromPush(permissionPush + ("time" to "hier")))
        assertNull(EventParser.fromPush(permissionPush - "payload"))
        assertNull(EventParser.fromPush(emptyMap()))
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
