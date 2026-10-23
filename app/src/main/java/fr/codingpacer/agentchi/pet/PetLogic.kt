package fr.codingpacer.agentchi.pet

import fr.codingpacer.agentchi.data.EventType
import fr.codingpacer.agentchi.text.tr
import kotlinx.serialization.Serializable

enum class Reaction { NONE, CELEBRATE, ALERT, WAVE, THINK, LOVE, EAT, PLAY, CLEAN }

enum class Mood { HAPPY, NEUTRAL, SAD, HUNGRY, TIRED, SLEEPING, DIRTY }

@Serializable
data class PetState(
    val name: String = "Chi",
    val satiety: Float = 80f,
    val energy: Float = 80f,
    val happiness: Float = 80f,
    val xp: Int = 0,
    val tasksCompleted: Int = 0,
    val sleeping: Boolean = false,
    /** Cacas à nettoyer autour de la mascotte (0 à [PetLogic.MAX_POOPS]). */
    val poops: Int = 0,
    /** Digestion en cours, en heures : un caca tous les [PetLogic.POOP_EVERY_HOURS]. */
    val digestion: Float = 0f,
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
    /** Bonheur perdu par heure et par caca laissé traîner. */
    private const val DIRTY_DECAY = 2f

    const val MAX_POOPS = 3
    const val POOP_EVERY_HOURS = 3f
    /** Un repas accélère la digestion d'autant d'heures. */
    private const val MEAL_DIGESTION = 1f

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
            happiness = clamp(s.happiness - (HAPPINESS_DECAY + DIRTY_DECAY * s.poops) * hours),
            energy = clamp(energy),
            lastUpdate = now,
        ).digest(if (s.sleeping) hours / 2 else hours)
        // Se réveille tout seul une fois reposé
        return if (next.sleeping && next.energy >= 100f) next.copy(sleeping = false, reaction = Reaction.WAVE, reactionUntil = now + REACTION_MS) else next
    }

    fun feed(s: PetState, now: Long): PetState = tick(s, now).let {
        if (it.sleeping) it else it.copy(satiety = clamp(it.satiety + 25f), happiness = clamp(it.happiness + 3f))
            .digest(MEAL_DIGESTION)
            .react(Reaction.EAT, now)
    }

    /** Nettoie les cacas : la mascotte est soulagée (+5 de bonheur et +1 XP par caca), même endormie. */
    fun clean(s: PetState, now: Long): PetState = tick(s, now).let {
        if (it.poops == 0) it
        else it.copy(poops = 0, happiness = clamp(it.happiness + 5f * it.poops), xp = it.xp + it.poops)
            .let { c -> if (c.sleeping) c else c.react(Reaction.CLEAN, now) }
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

    /** Réaction de la mascotte aux événements des sessions des agents. */
    fun onAgentEvent(s: PetState, type: EventType, now: Long): PetState {
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
            EventType.SESSION_END, EventType.OTHER, EventType.PROMPT -> t
        }
    }

    /** Succès débloqué : la mascotte fait la fête. */
    fun celebrate(s: PetState, now: Long): PetState = tick(s, now).copy(sleeping = false).react(Reaction.CELEBRATE, now, 10_000L)

    fun currentReaction(s: PetState, now: Long): Reaction =
        if (s.reaction != Reaction.NONE && now < s.reactionUntil) s.reaction else Reaction.NONE

    fun mood(s: PetState): Mood = when {
        s.sleeping -> Mood.SLEEPING
        s.satiety < 25f -> Mood.HUNGRY
        s.poops >= 2 -> Mood.DIRTY
        s.energy < 20f -> Mood.TIRED
        s.happiness < 30f -> Mood.SAD
        (s.satiety + s.energy + s.happiness) / 3f > 70f -> Mood.HAPPY
        else -> Mood.NEUTRAL
    }

    fun level(xp: Int): Int = 1 + xp / 100

    /** Forme moyenne de la mascotte (0-100), pour la complication. */
    fun wellbeing(s: PetState): Int = ((s.satiety + s.energy + s.happiness) / 3f).toInt().coerceIn(0, 100)

    /** L'humeur en un emoji, pour la tuile et la complication. */
    fun emoji(mood: Mood): String = when (mood) {
        Mood.HAPPY -> "😊"
        Mood.NEUTRAL -> "🙂"
        Mood.SAD -> "😢"
        Mood.HUNGRY -> "🍪"
        Mood.TIRED -> "🥱"
        Mood.SLEEPING -> "😴"
        Mood.DIRTY -> "💩"
    }

    fun moodLine(s: PetState, reaction: Reaction): String = when (reaction) {
        Reaction.ALERT -> tr("On a besoin de toi !", "You're needed!")
        Reaction.CELEBRATE -> tr("Tâche terminée, bravo !", "Task done, nice work!")
        Reaction.WAVE -> tr("Coucou !", "Hi there!")
        Reaction.THINK -> tr("Je range mes idées…", "Tidying up my thoughts…")
        Reaction.EAT -> tr("Miam !", "Yum!")
        Reaction.PLAY -> tr("Youpi !", "Yay!")
        Reaction.LOVE -> "♥"
        Reaction.CLEAN -> tr("Tout propre, merci !", "All clean, thanks!")
        Reaction.NONE -> when (mood(s)) {
            Mood.SLEEPING -> "Zzz…"
            Mood.HUNGRY -> tr("J'ai faim…", "I'm hungry…")
            Mood.DIRTY -> tr("Ça sent pas bon ici…", "Something stinks in here…")
            Mood.TIRED -> tr("Je suis épuisé…", "I'm worn out…")
            Mood.SAD -> tr("On joue ?", "Wanna play?")
            Mood.HAPPY -> tr("Tout va bien !", "All good!")
            Mood.NEUTRAL -> tr("Prêt à coder.", "Ready to code.")
        }
    }

    /** Avance la digestion de [hours] heures : un caca à chaque [POOP_EVERY_HOURS], au plus [MAX_POOPS]. */
    private fun PetState.digest(hours: Float): PetState {
        var d = digestion + hours
        var p = poops
        while (d >= POOP_EVERY_HOURS && p < MAX_POOPS) {
            d -= POOP_EVERY_HOURS
            p++
        }
        // Plus de place : la digestion attend le prochain nettoyage.
        return copy(poops = p, digestion = if (p >= MAX_POOPS) minOf(d, POOP_EVERY_HOURS) else d)
    }

    private fun PetState.react(r: Reaction, now: Long, duration: Long = REACTION_MS) =
        copy(reaction = r, reactionUntil = now + duration)

    private fun clamp(v: Float) = v.coerceIn(0f, 100f)
}
