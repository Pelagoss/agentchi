package fr.codingpacer.claudecompanion

import fr.codingpacer.claudecompanion.data.EventType
import fr.codingpacer.claudecompanion.pet.Mood
import fr.codingpacer.claudecompanion.pet.PetLogic
import fr.codingpacer.claudecompanion.pet.PetState
import fr.codingpacer.claudecompanion.pet.Reaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetLogicTest {
    private val hour = 3_600_000L
    private val t0 = 1_000_000_000L

    @Test
    fun `les jauges baissent avec le temps`() {
        val s = PetLogic.tick(PetState(lastUpdate = t0), t0 + 2 * hour)
        assertEquals(68f, s.satiety, 0.01f)
        assertEquals(72f, s.happiness, 0.01f)
        assertEquals(70f, s.energy, 0.01f)
    }

    @Test
    fun `les jauges restent entre 0 et 100`() {
        val s = PetLogic.tick(PetState(lastUpdate = t0), t0 + 100 * hour)
        assertEquals(0f, s.satiety, 0f)
        assertEquals(0f, s.energy, 0f)
    }

    @Test
    fun `dormir recharge l'energie et reveille une fois repose`() {
        val s = PetLogic.tick(PetState(energy = 10f, sleeping = true, lastUpdate = t0), t0 + 10 * hour)
        assertEquals(100f, s.energy, 0f)
        assertFalse(s.sleeping)
    }

    @Test
    fun `une tache terminee rend heureux et donne de l'xp`() {
        val s = PetLogic.onClaudeEvent(PetState(lastUpdate = t0), EventType.TASK_DONE, t0)
        assertEquals(10, s.xp)
        assertEquals(1, s.tasksCompleted)
        assertEquals(Reaction.CELEBRATE, PetLogic.currentReaction(s, t0 + 1_000))
        assertEquals(Reaction.NONE, PetLogic.currentReaction(s, t0 + 60_000))
    }

    @Test
    fun `une demande d'autorisation declenche une alerte et reveille`() {
        val s = PetLogic.onClaudeEvent(PetState(sleeping = true, lastUpdate = t0), EventType.PERMISSION, t0)
        assertFalse(s.sleeping)
        assertEquals(Reaction.ALERT, PetLogic.currentReaction(s, t0 + 60_000))
    }

    @Test
    fun `on ne peut pas nourrir une mascotte endormie`() {
        val s = PetLogic.feed(PetState(satiety = 10f, sleeping = true, lastUpdate = t0), t0)
        assertEquals(10f, s.satiety, 0f)
    }

    @Test
    fun humeur() {
        assertEquals(Mood.HUNGRY, PetLogic.mood(PetState(satiety = 10f)))
        assertEquals(Mood.SLEEPING, PetLogic.mood(PetState(sleeping = true)))
        assertEquals(Mood.HAPPY, PetLogic.mood(PetState(satiety = 90f, energy = 90f, happiness = 90f)))
        assertTrue(PetLogic.level(250) == 3)
    }

    @Test
    fun `forme et emoji pour la tuile et la complication`() {
        assertEquals(80, PetLogic.wellbeing(PetState()))
        assertEquals(0, PetLogic.wellbeing(PetState(satiety = 0f, energy = 0f, happiness = 0f)))
        assertEquals("😊", PetLogic.emoji(PetLogic.mood(PetState())))
        assertEquals("😴", PetLogic.emoji(PetLogic.mood(PetState(sleeping = true))))
        Mood.entries.forEach { assertTrue(PetLogic.emoji(it).isNotEmpty()) }
    }
}
