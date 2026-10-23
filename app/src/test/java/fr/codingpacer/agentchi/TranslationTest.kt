package fr.codingpacer.agentchi

import fr.codingpacer.agentchi.data.AgentEvent
import fr.codingpacer.agentchi.data.Ask
import fr.codingpacer.agentchi.data.AskAnswer
import fr.codingpacer.agentchi.data.CompanionSettings
import fr.codingpacer.agentchi.data.EventType
import fr.codingpacer.agentchi.data.SessionState
import fr.codingpacer.agentchi.notify.Digest
import fr.codingpacer.agentchi.pet.Achievement
import fr.codingpacer.agentchi.pet.MascotSprite
import fr.codingpacer.agentchi.pet.PetLogic
import fr.codingpacer.agentchi.pet.PetState
import fr.codingpacer.agentchi.pet.Reaction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** L'interface est en anglais, sauf sur une montre en français. */
class TranslationTest {
    private fun task(id: String, project: String) =
        AgentEvent(id = id, type = EventType.TASK_DONE, message = "Message $id", project = project, time = 0)

    @Test
    fun `anglais par defaut, francais sur une montre en francais`() {
        for ((locale, label) in listOf(Locale.US to "Task done", Locale.GERMANY to "Task done", Locale.FRANCE to "Tâche terminée", Locale.CANADA_FRENCH to "Tâche terminée")) {
            assertEquals(locale.toString(), label, inLocale(locale) { EventType.TASK_DONE.label })
        }
    }

    @Test
    fun `resume groupe en anglais`() = inLocale(Locale.US) {
        val s = Digest.summary(listOf(task("3", "api"), task("2", "demo"), task("1", "demo")))
        assertEquals("✅ 3 tasks done", s.title)
        assertEquals("across 2 projects: api, demo", s.text)
        assertEquals("1 session ended", Digest.count(EventType.SESSION_END, 1))
    }

    @Test
    fun `reponses, succes et mascotte en anglais`() = inLocale(Locale.US) {
        val permission = Ask(id = "q1", kind = Ask.PERMISSION)
        assertEquals("❌ Denied: not on prod", AskAnswer(AskAnswer.DENY, "not on prod").summary(permission))
        assertEquals("✍️ Changes requested: no CI", AskAnswer(AskAnswer.DENY, "no CI").summary(Ask(id = "q2", kind = Ask.PLAN)))
        assertEquals("Night owl", Achievement.NIGHT_OWL.title)
        assertEquals("Waiting for you", SessionState.WAITING.label)
        assertEquals("Ocean", MascotSprite.Palette.OCEAN.label)
        assertEquals("Task done, nice work!", PetLogic.moodLine(PetState(), Reaction.CELEBRATE))
        assertEquals(listOf("Yes", "No", "Continue", "Do your best"), CompanionSettings().quickReplies)
    }
}
