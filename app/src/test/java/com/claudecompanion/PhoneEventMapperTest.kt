package com.claudecompanion

import com.claudecompanion.data.CompanionSettings
import com.claudecompanion.data.EventType
import com.claudecompanion.relay.PhoneEventMapper
import com.claudecompanion.relay.RelayedNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneEventMapperTest {

    @Test
    fun `deduit le type depuis le texte de la notification`() {
        assertEquals(EventType.PERMISSION, PhoneEventMapper.classify("Fix login bug", "Claude needs your permission to run a command"))
        assertEquals(EventType.INPUT_NEEDED, PhoneEventMapper.classify("Refacto", "Claude is waiting for your input"))
        assertEquals(EventType.TASK_DONE, PhoneEventMapper.classify("Refacto", "Task complete"))
        assertEquals(EventType.TASK_DONE, PhoneEventMapper.classify("Refacto", "Claude a terminé"))
        assertEquals(EventType.ERROR, PhoneEventMapper.classify("Refacto", "Session failed to complete"))
        assertEquals(EventType.OTHER, PhoneEventMapper.classify("Claude", "Voici une idée de recette"))
    }

    @Test
    fun `convertit une notification relayee en evenement`() {
        val n = RelayedNotification(key = "0|com.anthropic.claude|42|null|10", title = "Fix login bug", text = "Task complete", time = 1_760_000_000_000)
        val event = PhoneEventMapper.toEvent(n)
        assertEquals(EventType.TASK_DONE, event.type)
        assertEquals("Fix login bug", event.project)
        assertEquals("Task complete", event.message)
        assertEquals(1_760_000_000L, event.time)
        assertTrue(event.fromPhone)
        assertTrue(event.openableOnPhone)
        assertEquals("📱 Fix login bug", event.origin)
    }

    @Test
    fun `une conversation sans type reconnu garde son titre`() {
        val n = RelayedNotification(key = "k", title = "Claude", text = "Voici une idée", conversation = "Recettes", time = 0)
        assertEquals("💬 Recettes", PhoneEventMapper.toEvent(n).title)
    }

    @Test
    fun `filtre des notifications du telephone`() {
        val event = PhoneEventMapper.toEvent(RelayedNotification(key = "k", title = "t", text = "Task complete", time = 0))
        assertFalse(CompanionSettings().isFiltered(event))
        assertTrue(CompanionSettings(phoneNotifications = false).isFiltered(event))
        // Les réglages du hook ne concernent pas le téléphone
        assertFalse(CompanionSettings(localSessions = false, cloudSessions = false).isFiltered(event))
    }
}
