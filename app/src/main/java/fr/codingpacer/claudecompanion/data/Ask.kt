package fr.codingpacer.claudecompanion.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Question de Claude à laquelle la montre peut répondre (champ `q` du payload, voir server/ask.go).
 * Le hook Claude Code attend la réponse jusqu'à [expires], puis pose la question sur l'ordinateur.
 */
@Serializable
data class Ask(
    val id: String,
    /** [PERMISSION], [QUESTION], [PLAN] ou [REPLY]. */
    @SerialName("k") val kind: String,
    /** Outil concerné (Bash, Edit…). */
    val tool: String? = null,
    /** Commande, fichier, plan ou fin du dernier message de Claude. */
    @SerialName("d") val detail: String = "",
    /** « Toujours autoriser » possible : Claude Code propose une règle de permission. */
    @SerialName("al") val always: Boolean = false,
    @SerialName("qs") val questions: List<AskQuestion> = emptyList(),
    /** Date limite de réponse (Unix, secondes). */
    @SerialName("x") val expires: Long = 0,
) {
    fun isExpired(nowSeconds: Long = System.currentTimeMillis() / 1000) = expires in 1..nowSeconds

    companion object {
        const val PERMISSION = "permission"
        const val QUESTION = "question"
        const val PLAN = "plan"
        const val REPLY = "reply"
    }
}

@Serializable
data class AskQuestion(
    @SerialName("q") val question: String,
    @SerialName("h") val header: String = "",
    @SerialName("mu") val multi: Boolean = false,
    @SerialName("o") val options: List<AskOption> = emptyList(),
)

@Serializable
data class AskOption(
    @SerialName("l") val label: String,
    @SerialName("d") val description: String = "",
)

/** Réponse envoyée au serveur (POST /v1/asks/{id}). */
@Serializable
data class AskAnswer(
    /** [ALLOW], [ALWAYS], [DENY] ou [PASS]. */
    val decision: String,
    val message: String? = null,
    /** Question → libellé choisi (choix multiples séparés par des virgules) ou texte libre. */
    val answers: Map<String, String>? = null,
) {
    /** La réponse en une ligne, pour l'historique. */
    fun summary(ask: Ask): String = when {
        decision == PASS -> if (ask.kind == Ask.REPLY) "🏁 Terminé" else "💻 Laissée à l'ordinateur"
        ask.kind == Ask.PERMISSION -> when (decision) {
            ALWAYS -> "♾️ Toujours autorisé"
            DENY -> "❌ Refusé" + message.suffix()
            else -> "✅ Autorisé"
        }
        ask.kind == Ask.PLAN -> if (decision == DENY) {
            if (message.isNullOrBlank()) "❌ Plan refusé" else "✍️ Changements demandés : $message"
        } else {
            "✅ Plan approuvé"
        }
        ask.kind == Ask.QUESTION -> if (decision == DENY) {
            "❌ Pas de réponse" + message.suffix()
        } else {
            "✅ " + ask.questions.mapNotNull { answers?.get(it.question) }.joinToString(" · ")
        }
        else -> "💬 " + message.orEmpty()
    }

    private fun String?.suffix() = if (isNullOrBlank()) "" else " : $this"

    companion object {
        const val ALLOW = "allow"
        const val ALWAYS = "always"
        const val DENY = "deny"

        /** Laisser l'ordinateur poser la question. */
        const val PASS = "pass"

        val pass = AskAnswer(PASS)

        /** Historique : question restée sans réponse de la montre. */
        const val UNANSWERED = "⏱ Sans réponse : posée sur l'ordinateur"
    }
}
