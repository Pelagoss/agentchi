package com.claudecompanion.pet

import com.claudecompanion.data.EventType
import kotlinx.serialization.Serializable

enum class Reaction { NONE, CELEBRATE, ALERT, WAVE, THINK, LOVE, EAT, PLAY }

enum class Mood { HAPPY, NEUTRAL, SAD, HUNGRY, TIRED, SLEEPING }

@Serializable
data class PetState(
    val name: String = "Claude",
    val satiety: Float = 80f,
    val energy: Float = 80f,
    val happiness: Float = 80f,
    val xp: Int = 0,
    val tasksCompleted: Int = 0,
    val sleeping: Boolean = false,
    /** Dernière mise à jour des jauges (ms). 0 = jamais. */
    val lastUpdate: Long = 0L,
    val reaction: Reaction = Reaction.NONE,
    val reactionUntil: Long = 0L,
)

/** Logique pure du tamagotchi (sans dépendance Android, testable en JVM). */
object PetLogic {
    private const val HOUR = 3_600_000f

    // Variation des jauges par heure
    private const val SATIETY_DECAY = 6f
    private const val HAPPINESS_DECAY = 4f
    private const val ENERGY_DECAY = 5f
    private const val ENERGY_SLEEP_GAIN = 15f

    private const val REACTION_MS = 4_000L
    private const val ALERT_MS = 30 * 60_000L

    /** Applique l'écoulement du temps depuis [PetState.lastUpdate]. */
    fun tick(s: PetState, now: Long): PetState {
        if (s.lastUpdate == 0L || now <= s.lastUpdate) return s.copy(lastUpdate = maxOf(now, s.lastUpdate))
        val hours = (now - s.lastUpdate) / HOUR
        val energy = if (s.sleeping) s.energy + ENERGY_SLEEP_GAIN * hours else s.energy - ENERGY_DECAY * hours
        val satietyDecay = if (s.sleeping) SATIETY_DECAY / 2 else SATIETY_DECAY
        val next = s.copy(
            satiety = clamp(s.satiety - satietyDecay * hours),
            happiness = clamp(s.happiness - HAPPINESS_DECAY * hours),
            energy = clamp(energy),
            lastUpdate = now,
        )
        // Se réveille tout seul une fois reposé
        return if (next.sleeping && next.energy >= 100f) next.copy(sleeping = false, reaction = Reaction.WAVE, reactionUntil = now + REACTION_MS) else next
    }

    fun feed(s: PetState, now: Long): PetState = tick(s, now).let {
        if (it.sleeping) it else it.copy(satiety = clamp(it.satiety + 25f), happiness = clamp(it.happiness + 3f))
            .react(Reaction.EAT, now)
    }

    fun play(s: PetState, now: Long): PetState = tick(s, now).let {
        if (it.sleeping || it.energy < 10f) it
        else it.copy(
            happiness = clamp(it.happiness + 20f),
            energy = clamp(it.energy - 10f),
            satiety = clamp(it.satiety - 5f),
            xp = it.xp + 2,
        ).react(Reaction.PLAY, now)
    }

    fun toggleSleep(s: PetState, now: Long): PetState = tick(s, now).let {
        it.copy(sleeping = !it.sleeping, reaction = Reaction.NONE, reactionUntil = 0L)
    }

    /** Caresse (tap sur la mascotte). Acquitte aussi une alerte en cours. */
    fun pet(s: PetState, now: Long): PetState = tick(s, now).let {
        if (it.sleeping) it else it.copy(happiness = clamp(it.happiness + 3f)).react(Reaction.LOVE, now)
    }

    /** Réaction de la mascotte aux événements des sessions Claude. */
    fun onClaudeEvent(s: PetState, type: EventType, now: Long): PetState {
        val t = tick(s, now)
        return when (type) {
            EventType.TASK_DONE -> t.copy(
                happiness = clamp(t.happiness + 10f),
                xp = t.xp + 10,
                tasksCompleted = t.tasksCompleted + 1,
                sleeping = false,
            ).react(Reaction.CELEBRATE, now, 10_000L)
            EventType.SUBAGENT_DONE -> t.copy(xp = t.xp + 3)
            EventType.PERMISSION, EventType.INPUT_NEEDED, EventType.ERROR ->
                t.copy(sleeping = false).react(Reaction.ALERT, now, ALERT_MS)
            EventType.SESSION_START -> t.copy(sleeping = false).react(Reaction.WAVE, now, 10_000L)
            EventType.COMPACT -> t.react(Reaction.THINK, now, 10_000L)
            EventType.SESSION_END, EventType.OTHER -> t
        }
    }

    fun currentReaction(s: PetState, now: Long): Reaction =
        if (s.reaction != Reaction.NONE && now < s.reactionUntil) s.reaction else Reaction.NONE

    fun mood(s: PetState): Mood = when {
        s.sleeping -> Mood.SLEEPING
        s.satiety < 25f -> Mood.HUNGRY
        s.energy < 20f -> Mood.TIRED
        s.happiness < 30f -> Mood.SAD
        (s.satiety + s.energy + s.happiness) / 3f > 70f -> Mood.HAPPY
        else -> Mood.NEUTRAL
    }

    fun level(xp: Int): Int = 1 + xp / 100

    fun moodLine(s: PetState, reaction: Reaction): String = when (reaction) {
        Reaction.ALERT -> "Claude a besoin de toi !"
        Reaction.CELEBRATE -> "Tâche terminée, bravo !"
        Reaction.WAVE -> "Coucou !"
        Reaction.THINK -> "Je range mes idées…"
        Reaction.EAT -> "Miam !"
        Reaction.PLAY -> "Youpi !"
        Reaction.LOVE -> "♥"
        Reaction.NONE -> when (mood(s)) {
            Mood.SLEEPING -> "Zzz…"
            Mood.HUNGRY -> "J'ai faim…"
            Mood.TIRED -> "Je suis épuisé…"
            Mood.SAD -> "On joue ?"
            Mood.HAPPY -> "Tout va bien !"
            Mood.NEUTRAL -> "Prêt à coder."
        }
    }

    private fun PetState.react(r: Reaction, now: Long, duration: Long = REACTION_MS) =
        copy(reaction = r, reactionUntil = now + duration)

    private fun clamp(v: Float) = v.coerceIn(0f, 100f)
}
