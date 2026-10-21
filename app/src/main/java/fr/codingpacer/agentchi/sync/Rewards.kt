package fr.codingpacer.agentchi.sync

import fr.codingpacer.agentchi.data.CompanionStore
import fr.codingpacer.agentchi.notify.EventNotifier
import fr.codingpacer.agentchi.pet.PetLogic
import fr.codingpacer.agentchi.pet.Progress

/** Séries et succès : compte ce qui se passe et fête les succès débloqués. */
class Rewards(
    private val store: CompanionStore,
    private val notifier: EventNotifier,
    /** Mascotte changée : tuile et complication à redessiner. */
    private val onChange: () -> Unit = {},
) {
    suspend fun record(transform: (Progress) -> Progress) {
        val now = System.currentTimeMillis()
        val unlocked = store.updateProgress(now, transform)
        if (unlocked.isEmpty()) return
        store.updatePet { PetLogic.celebrate(it, now) }
        notifier.notifyAchievements(unlocked)
        onChange()
    }
}
