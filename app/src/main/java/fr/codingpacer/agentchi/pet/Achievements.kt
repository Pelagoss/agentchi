package fr.codingpacer.agentchi.pet

import fr.codingpacer.agentchi.data.Ask
import fr.codingpacer.agentchi.data.AskAnswer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

/** Compteurs des séries et des succès (persistés, voir CompanionStore). */
@Serializable
data class Progress(
    /** Jours d'affilée avec au moins une tâche terminée, jusqu'à [lastTaskDay]. */
    val streak: Int = 0,
    val bestStreak: Int = 0,
    /** Dernier jour (LocalDate.toEpochDay) avec une tâche terminée, -1 = jamais. */
    val lastTaskDay: Long = -1,
    val nightTasks: Int = 0,
    /** Réponses données depuis la montre. */
    val allowed: Int = 0,
    val denied: Int = 0,
    val messages: Int = 0,
    val plans: Int = 0,
    val questions: Int = 0,
    val feeds: Int = 0,
    /** Succès débloqués : nom de [Achievement] → date (ms). */
    val unlocked: Map<String, Long> = emptyMap(),
)

/** Un succès : atteint quand [metric] vaut au moins [goal]. */
enum class Achievement(
    val emoji: String,
    val title: String,
    val description: String,
    val goal: Int,
    val metric: (Progress, PetState) -> Int,
) {
    FIRST_TASK("🎉", "Premier succès", "Une tâche terminée par un agent", 1, { _, pet -> pet.tasksCompleted }),
    TASKS_10("🛠️", "Bon début", "10 tâches terminées", 10, { _, pet -> pet.tasksCompleted }),
    TASKS_100("🏗️", "Centurion", "100 tâches terminées", 100, { _, pet -> pet.tasksCompleted }),
    TASKS_1000("🚀", "Usine à code", "1 000 tâches terminées", 1000, { _, pet -> pet.tasksCompleted }),
    STREAK_3("🔥", "Trois jours d'affilée", "Une tâche terminée 3 jours de suite", 3, { p, _ -> p.bestStreak }),
    STREAK_7("📅", "Une semaine d'affilée", "Une tâche terminée 7 jours de suite", 7, { p, _ -> p.bestStreak }),
    STREAK_30("🗓️", "Un mois d'affilée", "Une tâche terminée 30 jours de suite", 30, { p, _ -> p.bestStreak }),
    FIRST_ALLOW("✅", "Feu vert", "Première autorisation donnée depuis la montre", 1, { p, _ -> p.allowed }),
    ALLOW_100("🛂", "Douanier", "100 autorisations données depuis la montre", 100, { p, _ -> p.allowed }),
    FIRST_DENY("🛑", "Garde-fou", "Premier refus depuis la montre", 1, { p, _ -> p.denied }),
    FIRST_MESSAGE("🗣️", "Premiers mots", "Premier message à un agent, au clavier ou à la voix", 1, { p, _ -> p.messages }),
    MESSAGES_50("💬", "Bavard", "50 messages envoyés depuis la montre", 50, { p, _ -> p.messages }),
    FIRST_PLAN("🗺️", "Architecte", "Premier plan approuvé depuis la montre", 1, { p, _ -> p.plans }),
    FIRST_QUESTION("🧩", "Décideur", "Première question à choix répondue depuis la montre", 1, { p, _ -> p.questions }),
    NIGHT_OWL("🦉", "Oiseau de nuit", "Une tâche terminée entre minuit et 5 h", 1, { p, _ -> p.nightTasks }),
    CARETAKER("🍪", "Aux petits soins", "Nourrir la mascotte 50 fois", 50, { p, _ -> p.feeds }),
    LEVEL_5("👑", "Niveau 5", "La mascotte atteint le niveau 5", 5, { _, pet -> PetLogic.level(pet.xp) }),
    LEVEL_10("🌟", "Niveau 10", "La mascotte atteint le niveau 10", 10, { _, pet -> PetLogic.level(pet.xp) }),
}

/** Logique pure des séries et des succès (sans dépendance Android, testable en JVM). */
object Achievements {
    private const val NIGHT_END_HOUR = 5

    /** Une tâche terminée à [now] : prolonge ou recommence la série. */
    fun onTaskDone(p: Progress, now: Long, zone: ZoneId = ZoneId.systemDefault()): Progress {
        val time = Instant.ofEpochMilli(now).atZone(zone)
        val day = time.toLocalDate().toEpochDay()
        val streak = when (p.lastTaskDay) {
            day -> p.streak
            day - 1 -> p.streak + 1
            else -> 1
        }
        return p.copy(
            streak = streak,
            bestStreak = maxOf(p.bestStreak, streak),
            lastTaskDay = maxOf(p.lastTaskDay, day),
            nightTasks = p.nightTasks + if (time.hour < NIGHT_END_HOUR) 1 else 0,
        )
    }

    /** Réponse envoyée depuis la montre. Laisser l'ordinateur répondre ne compte pas. */
    fun onAnswer(p: Progress, ask: Ask, answer: AskAnswer): Progress {
        if (answer.decision == AskAnswer.PASS) return p
        val allow = answer.decision != AskAnswer.DENY
        val withMessage = !answer.message.isNullOrBlank()
        return p.copy(
            allowed = p.allowed + if (ask.kind == Ask.PERMISSION && allow) 1 else 0,
            denied = p.denied + if (ask.kind == Ask.PERMISSION && !allow) 1 else 0,
            plans = p.plans + if (ask.kind == Ask.PLAN && allow) 1 else 0,
            questions = p.questions + if (ask.kind == Ask.QUESTION && allow) 1 else 0,
            messages = p.messages + if (withMessage) 1 else 0,
        )
    }

    fun onFeed(p: Progress): Progress = p.copy(feeds = p.feeds + 1)

    /** Débloque les succès atteints : la progression mise à jour et les nouveaux succès. */
    fun unlock(p: Progress, pet: PetState, now: Long): Pair<Progress, List<Achievement>> {
        val new = Achievement.entries.filter { it.name !in p.unlocked && it.metric(p, pet) >= it.goal }
        if (new.isEmpty()) return p to emptyList()
        return p.copy(unlocked = p.unlocked + new.associate { it.name to now }) to new
    }

    fun isUnlocked(p: Progress, a: Achievement) = a.name in p.unlocked

    /** Avancement vers [a], borné à son objectif. */
    fun progressOf(a: Achievement, p: Progress, pet: PetState): Int = a.metric(p, pet).coerceIn(0, a.goal)

    /** Série en cours aujourd'hui : elle tient tant qu'une tâche a été terminée hier ou aujourd'hui. */
    fun currentStreak(p: Progress, now: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
        return if (p.lastTaskDay >= today - 1) p.streak else 0
    }
}
