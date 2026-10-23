package fr.codingpacer.agentchi

import fr.codingpacer.agentchi.data.Agent
import fr.codingpacer.agentchi.data.Ask
import fr.codingpacer.agentchi.data.AskAnswer
import fr.codingpacer.agentchi.data.AskQuestion
import fr.codingpacer.agentchi.data.AgentEvent
import fr.codingpacer.agentchi.data.CompanionSettings
import fr.codingpacer.agentchi.data.EventType
import fr.codingpacer.agentchi.net.EventParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

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
        assertEquals("☁️ Claude · mon-depot · Cloud perso", event.origin)
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

    @Test
    fun `lit une question a laquelle repondre`() {
        val event = event(
            mapOf(
                "id" to "e3",
                "time" to "1792500500",
                "payload" to """{"t":"input","m":"Quelle base ?","p":"p","q":{"id":"qabc","k":"question","tool":"AskUserQuestion","x":1792500610,
                    "qs":[{"q":"Quelle base ?","h":"Base","o":[{"l":"Postgres","d":"Relationnelle"},{"l":"SQLite"}]},
                          {"q":"Étapes ?","mu":true,"o":[{"l":"Tests"}]}]}}""",
            ),
        )
        val ask = event.ask!!
        assertEquals(Ask.QUESTION, ask.kind)
        assertEquals(2, ask.questions.size)
        assertEquals("Relationnelle", ask.questions[0].options[0].description)
        assertTrue(ask.questions[1].multi)
        assertEquals("qabc".hashCode(), event.notificationId)
        assertFalse(ask.isExpired(1_792_500_600))
        assertTrue(ask.isExpired(1_792_500_610))
        assertNull(EventParser.askEnd(permissionPush))
    }

    @Test
    fun `retire une question expiree`() {
        val data = mapOf("id" to "e4", "time" to "1", "payload" to """{"t":"ask_end","e":"qabc"}""")
        assertEquals("qabc", EventParser.askEnd(data))
        assertNull(event(permissionPush).ask)
    }

    @Test
    fun `reponse au format du serveur`() {
        val json = EventParser.json.encodeToString(
            AskAnswer.serializer(),
            AskAnswer(AskAnswer.ALLOW, answers = mapOf("Quelle base ?" to "SQLite")),
        )
        assertEquals("""{"decision":"allow","answers":{"Quelle base ?":"SQLite"}}""", json)
        assertEquals("""{"decision":"pass"}""", EventParser.json.encodeToString(AskAnswer.serializer(), AskAnswer.pass))
    }

    @Test
    fun `resume des reponses pour l historique`() = inLocale(Locale.FRENCH) {
        val permission = Ask(id = "q1", kind = Ask.PERMISSION)
        assertEquals("✅ Autorisé", AskAnswer(AskAnswer.ALLOW).summary(permission))
        assertEquals("♾️ Toujours autorisé", AskAnswer(AskAnswer.ALWAYS).summary(permission))
        assertEquals("❌ Refusé : pas sur la prod", AskAnswer(AskAnswer.DENY, "pas sur la prod").summary(permission))
        assertEquals("💻 Laissée à l'ordinateur", AskAnswer.pass.summary(permission))

        val question = Ask(
            id = "q2",
            kind = Ask.QUESTION,
            questions = listOf(AskQuestion("Base ?"), AskQuestion("Étapes ?", multi = true)),
        )
        assertEquals("✅ SQLite · Tests, Docs", AskAnswer(AskAnswer.ALLOW, answers = mapOf("Étapes ?" to "Tests, Docs", "Base ?" to "SQLite")).summary(question))

        val plan = Ask(id = "q3", kind = Ask.PLAN)
        assertEquals("✍️ Changements demandés : sans la CI", AskAnswer(AskAnswer.DENY, "sans la CI").summary(plan))

        val reply = Ask(id = "q4", kind = Ask.REPLY)
        assertEquals("💬 Continue", AskAnswer(AskAnswer.ALLOW, "Continue").summary(reply))
        assertEquals("🏁 Terminé", AskAnswer.pass.summary(reply))
    }

    @Test
    fun `question en attente tant qu elle n a ni reponse ni echeance passee`() {
        val now = System.currentTimeMillis() / 1000
        val event = AgentEvent(id = "e", type = EventType.PERMISSION, message = "m", time = now, ask = Ask(id = "q", kind = Ask.PERMISSION, expires = now + 60))
        assertTrue(event.isPendingAsk)
        assertFalse(event.copy(answer = "✅ Autorisé").isPendingAsk)
        assertFalse(event.copy(ask = event.ask!!.copy(expires = now - 1)).isPendingAsk)
        assertFalse(event.copy(ask = null).isPendingAsk)
    }

    @Test
    fun `evenement Codex`() {
        val event = message("""{"t":"done","m":"Codex a terminé sa tâche.","p":"api","ag":"codex"}""")!!
        assertEquals(Agent.CODEX, event.agent)
        assertEquals("Codex · api", event.origin)
        assertEquals(Agent.CLAUDE, event(permissionPush).agent)
        assertTrue(CompanionSettings(mutedAgents = setOf(Agent.CODEX)).isFiltered(event))
        assertFalse(CompanionSettings(mutedAgents = setOf(Agent.CODEX)).isFiltered(event(permissionPush)))
    }
}
