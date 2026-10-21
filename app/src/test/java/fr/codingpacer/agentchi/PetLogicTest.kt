package fr.codingpacer.agentchi

import fr.codingpacer.agentchi.data.EventType
import fr.codingpacer.agentchi.pet.MascotSprite
import fr.codingpacer.agentchi.pet.Mood
import fr.codingpacer.agentchi.pet.PetLogic
import fr.codingpacer.agentchi.pet.PetState
import fr.codingpacer.agentchi.pet.PixelIcon
import fr.codingpacer.agentchi.pet.Reaction
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
        val s = PetLogic.onAgentEvent(PetState(lastUpdate = t0), EventType.TASK_DONE, t0)
        assertEquals(10, s.xp)
        assertEquals(1, s.tasksCompleted)
        assertEquals(Reaction.CELEBRATE, PetLogic.currentReaction(s, t0 + 1_000))
        assertEquals(Reaction.NONE, PetLogic.currentReaction(s, t0 + 60_000))
    }

    @Test
    fun `une demande d'autorisation declenche une alerte et reveille`() {
        val s = PetLogic.onAgentEvent(PetState(sleeping = true, lastUpdate = t0), EventType.PERMISSION, t0)
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

    @Test
    fun `sprite de la mascotte dans la grille`() {
        MascotSprite.Species.entries.forEach { species ->
            MascotSprite.Accessory.entries.forEach { accessory ->
                MascotSprite.Face.entries.forEach { face ->
                    val pixels = MascotSprite.pixels(MascotSprite.Look(species, accessory = accessory), face, altLegs = true)
                    assertTrue("$species $accessory $face", pixels.all { it.x in 0 until MascotSprite.WIDTH && it.y in 0 until MascotSprite.HEIGHT })
                }
            }
            // Accessoires au-dessus du corps : les rangées du haut sont libres sans accessoire.
            assertTrue(MascotSprite.pixels(MascotSprite.Look(species), MascotSprite.Face.OPEN).none { it.y < MascotSprite.TOP })
            assertEquals(species.round, MascotSprite.pixels(MascotSprite.Look(species), MascotSprite.Face.OPEN).first().round)
        }
        val coral = MascotSprite.Look(MascotSprite.Species.CRAB)
        assertEquals(MascotSprite.Palette.CORAL, coral.palette)
        assertTrue(MascotSprite.pixels(coral.copy(palette = MascotSprite.Palette.OCEAN), MascotSprite.Face.OPEN).any { it.argb == MascotSprite.Palette.OCEAN.body })
        assertEquals(MascotSprite.Face.CLOSED, MascotSprite.face(Mood.SLEEPING, Reaction.NONE))
        assertEquals(MascotSprite.Face.WIDE, MascotSprite.face(Mood.HAPPY, Reaction.ALERT))
        assertEquals("💤", MascotSprite.bubble(Mood.SLEEPING, Reaction.NONE))
    }

    @Test
    fun `icones pixel 8 x 8`() {
        PixelIcon.entries.forEach { icon ->
            assertEquals(icon.name, PixelIcon.SIZE, icon.grid.size)
            assertTrue(icon.name, icon.grid.all { it.length == PixelIcon.SIZE })
            assertTrue(icon.name, icon.pixels().isNotEmpty())
        }
    }
}
