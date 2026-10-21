package fr.codingpacer.claudecompanion.sync

import android.content.Context
import android.widget.Toast
import androidx.core.content.ContextCompat
import fr.codingpacer.claudecompanion.data.Ask
import fr.codingpacer.claudecompanion.data.AskAnswer
import fr.codingpacer.claudecompanion.data.ClaudeEvent
import fr.codingpacer.claudecompanion.data.CompanionStore
import fr.codingpacer.claudecompanion.net.CompanionApi
import fr.codingpacer.claudecompanion.net.ServerException
import fr.codingpacer.claudecompanion.notify.EventNotifier
import fr.codingpacer.claudecompanion.widget.Widgets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/** Envoie au serveur les réponses données sur la montre et retire les questions traitées. */
class AskResponder(
    private val context: Context,
    private val store: CompanionStore,
    private val api: () -> CompanionApi,
    private val notifier: EventNotifier,
) {
    enum class Result(val message: String?) {
        SENT(null),
        EXPIRED("Trop tard : Claude n'attend plus cette réponse"),
        FAILED("Réponse non envoyée : serveur injoignable"),
    }

    private val _ended = MutableStateFlow<Set<String>>(emptySet())

    /** Questions qui n'attendent plus de réponse (traitées, expirées ou retirées par le serveur). */
    val ended: StateFlow<Set<String>> = _ended.asStateFlow()

    suspend fun answer(ask: Ask, answer: AskAnswer): Result {
        val settings = store.settings.first()
        val result = try {
            if (!settings.isConnected) Result.FAILED else {
                api().answer(settings, ask.id, answer)
                Result.SENT
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ServerException) {
            if (e.status == 410) Result.EXPIRED else Result.FAILED
        } catch (e: Exception) {
            Result.FAILED
        }
        when (result) {
            Result.SENT -> store.setAnswer(ask.id, answer.summary(ask))
            Result.EXPIRED -> store.setAnswer(ask.id, AskAnswer.UNANSWERED)
            Result.FAILED -> Unit
        }
        if (result != Result.FAILED) end(ask.id)
        return result
    }

    /** Le serveur a retiré la question restée sans réponse. */
    suspend fun expired(askId: String) {
        store.setAnswer(askId, AskAnswer.UNANSWERED)
        end(askId)
    }

    /** Dernière question qui attend encore une réponse de la montre. */
    suspend fun pending(): ClaudeEvent? =
        store.history.first().firstOrNull { it.isPendingAsk && it.ask!!.id !in ended.value }

    /** Laisse l'ordinateur poser la question (notification balayée, événement filtré…). */
    suspend fun pass(ask: Ask) {
        answer(ask, AskAnswer.pass)
    }

    /** Le serveur n'attend plus de réponse : la question disparaît de la montre. */
    fun end(askId: String) {
        _ended.update { (it + askId).toList().takeLast(50).toSet() }
        notifier.cancel(askId.hashCode())
        Widgets.refresh(context)
    }

    /** Affiche le résultat d'une réponse, depuis n'importe quel thread. */
    fun toast(result: Result) {
        val text = result.message ?: return
        ContextCompat.getMainExecutor(context).execute { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }
}
