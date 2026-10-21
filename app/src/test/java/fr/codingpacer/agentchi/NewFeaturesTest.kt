package fr.codingpacer.agentchi

import fr.codingpacer.agentchi.data.Ask
import fr.codingpacer.agentchi.data.AskAnswer
import fr.codingpacer.agentchi.data.AgentEvent
import fr.codingpacer.agentchi.data.CompanionSettings
import fr.codingpacer.agentchi.data.EventType
import fr.codingpacer.agentchi.data.SessionState
import fr.codingpacer.agentchi.data.Sessions
import fr.codingpacer.agentchi.net.EventParser
import fr.codingpacer.agentchi.notify.Digest
import fr.codingpacer.agentchi.pet.Achievement
import fr.codingpacer.agentchi.pet.Achievements
import fr.codingpacer.agentchi.pet.PetState
import fr.codingpacer.agentchi.pet.Progress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Résumé groupé, Ne pas déranger, séries et succès, sessions en cours. */
class NewFeaturesTest {
    private fun event(
        id: String,
        type: EventType,
        project: String? = "demo",
        session: String? = "s1",
        time: Long = 1_000,
        ask: Ask? = null,
    ) = AgentEvent(id = id, type = type, message = "Message $id", project = project, session = session, time = time, ask = ask)

    // --- Résumé groupé ---

    @Test
    fun `resume de taches sur plusieurs projets`() {
        val s = Digest.summary(
            listOf(event("3", EventType.TASK_DONE, "api"), event("2", EventType.TASK_DONE), event("1", EventType.TASK_DONE)),
        )
        assertEquals("✅ 3 tâches terminées", s.title)
        assertEquals("sur 2 projets : api, demo", s.text)
        assertEquals("✅ api Message 3", s.lines.first())
    }

    @Test
    fun `resume de types differents sur un projet`() {
        val s = Digest.summary(listOf(event("2", EventType.SESSION_END), event("1", EventType.TASK_DONE), event("0", EventType.TASK_DONE)))
        assertEquals("✅ 2 tâches terminées · 1 session terminée", s.title)
        assertEquals("sur demo", s.text)
    }

    @Test
    fun `ni les urgences ni les questions ne sont regroupees`() {
        assertTrue(Digest.accepts(event("1", EventType.TASK_DONE)))
        assertFalse(Digest.accepts(event("2", EventType.PERMISSION)))
        assertFalse(Digest.accepts(event("3", EventType.TASK_DONE, ask = Ask(id = "a", kind = Ask.REPLY))))
    }

    // --- Ne pas déranger ---

    @Test
    fun `ne pas deranger de la montre rend muet`() {
        val s = CompanionSettings(quietAllowsUrgent = true)
        assertTrue(s.isMuted(EventType.TASK_DONE, 12, systemQuiet = true))
        assertFalse(s.isMuted(EventType.PERMISSION, 12, systemQuiet = true))
        assertFalse(s.isMuted(EventType.TASK_DONE, 12, systemQuiet = false))
        assertFalse(s.copy(followSystemQuiet = false).isMuted(EventType.TASK_DONE, 12, systemQuiet = true))
        assertTrue(s.copy(quietAllowsUrgent = false).isMuted(EventType.PERMISSION, 12, systemQuiet = true))
    }

    // --- Séries et succès ---

    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 10, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `serie de jours d'affilee`() {
        var p = Progress()
        p = Achievements.onTaskDone(p, at(1, 10), ZoneOffset.UTC)
        p = Achievements.onTaskDone(p, at(1, 18), ZoneOffset.UTC)
        p = Achievements.onTaskDone(p, at(2, 9), ZoneOffset.UTC)
        p = Achievements.onTaskDone(p, at(3, 2), ZoneOffset.UTC)
        assertEquals(3, p.streak)
        assertEquals(1, p.nightTasks)
        assertEquals(3, Achievements.currentStreak(p, at(4, 12), ZoneOffset.UTC))
        assertEquals(0, Achievements.currentStreak(p, at(5, 12), ZoneOffset.UTC))
        // Un jour sauté : la série recommence, le record reste.
        p = Achievements.onTaskDone(p, at(5, 12), ZoneOffset.UTC)
        assertEquals(1, p.streak)
        assertEquals(3, p.bestStreak)
    }

    @Test
    fun `reponses comptees depuis la montre`() {
        val permission = Ask(id = "a", kind = Ask.PERMISSION)
        var p = Achievements.onAnswer(Progress(), permission, AskAnswer(AskAnswer.ALLOW))
        p = Achievements.onAnswer(p, permission, AskAnswer(AskAnswer.ALWAYS))
        p = Achievements.onAnswer(p, permission, AskAnswer(AskAnswer.DENY, message = "Pas en prod"))
        p = Achievements.onAnswer(p, permission, AskAnswer.pass)
        p = Achievements.onAnswer(p, Ask(id = "b", kind = Ask.REPLY), AskAnswer(AskAnswer.ALLOW, message = "Continue"))
        p = Achievements.onAnswer(p, Ask(id = "c", kind = Ask.PLAN), AskAnswer(AskAnswer.ALLOW))
        assertEquals(2, p.allowed)
        assertEquals(1, p.denied)
        assertEquals(2, p.messages)
        assertEquals(1, p.plans)
    }

    @Test
    fun `succes debloques une seule fois`() {
        val pet = PetState(tasksCompleted = 1, xp = 450)
        val (p, new) = Achievements.unlock(Progress(allowed = 1), pet, now = 42)
        assertEquals(setOf(Achievement.FIRST_TASK, Achievement.FIRST_ALLOW, Achievement.LEVEL_5), new.toSet())
        assertEquals(42L, p.unlocked[Achievement.LEVEL_5.name])
        assertTrue(Achievements.unlock(p, pet, now = 43).second.isEmpty())
        assertEquals(1, Achievements.progressOf(Achievement.TASKS_10, p, pet))
    }

    // --- Sessions en cours ---

    @Test
    fun `etat d'une session au fil des evenements`() {
        val ask = Ask(id = "q1", kind = Ask.PERMISSION)
        var s = Sessions.onEvent(emptyList(), event("1", EventType.SESSION_START, time = 100))
        assertEquals(SessionState.IDLE, s.single().state)
        s = Sessions.onEvent(s, event("2", EventType.PROMPT, time = 110))
        assertEquals(SessionState.WORKING, s.single().state)
        assertEquals("Message 1", s.single().message) // le message envoyé n'est pas connu
        s = Sessions.onEvent(s, event("3", EventType.PERMISSION, time = 120, ask = ask))
        assertEquals(SessionState.WAITING, s.single().state)
        assertEquals("q1", s.single().askId)
        s = Sessions.onAnswer(s, "q1", AskAnswer(AskAnswer.ALLOW), nowSeconds = 130)
        assertEquals(SessionState.WORKING, s.single().state)
        assertEquals(130L, s.single().since)
        assertNull(s.single().askId)
        s = Sessions.onEvent(s, event("4", EventType.TASK_DONE, time = 140))
        assertEquals(SessionState.IDLE, s.single().state)
        s = Sessions.onEvent(s, event("5", EventType.SESSION_END, time = 150))
        assertTrue(s.isEmpty())
    }

    @Test
    fun `sessions distinctes, urgentes d'abord, oubliees au bout de 12 h`() {
        var s = Sessions.onEvent(emptyList(), event("1", EventType.TASK_DONE, session = "a", time = 100))
        s = Sessions.onEvent(s, event("2", EventType.INPUT_NEEDED, session = "b", time = 50))
        s = Sessions.onEvent(s, event("3", EventType.TASK_DONE, session = "c", time = 0))
        val visible = Sessions.visible(s, nowSeconds = Sessions.STALE_SECONDS + 10)
        assertEquals(listOf(SessionState.WAITING, SessionState.IDLE), visible.map { it.state })
    }

    @Test
    fun `message envoye sans id, ignore par les anciennes versions`() {
        val e = EventParser.fromPush(mapOf("time" to "1792500475", "payload" to """{"t":"prompt","m":"Message envoyé à Claude","s":"abcdef12"}"""))!!
        assertEquals(EventType.PROMPT, e.type)
        assertTrue(e.type.silent)
        assertEquals("prompt-1792500475-abcdef12", e.id)
        // Sans id, les autres pushs restent illisibles, comme avant.
        assertNull(EventParser.fromPush(mapOf("time" to "1", "payload" to """{"t":"done"}""")))
    }
}
