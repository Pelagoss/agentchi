package com.claudecompanion

import com.claudecompanion.data.CompanionSettings
import com.claudecompanion.data.EventType
import com.claudecompanion.net.EventParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventParserTest {

    @Test
    fun `parse un message publie par le hook`() {
        val line = """{"id":"abc123","time":1760000000,"expires":1760043200,"event":"message","topic":"t",""" +
            """"title":"mon-projet","message":"Claude veut exécuter : rm -rf build","tags":["cc-permission","ccs-1a2b3c4d"],"priority":5}"""
        val msg = EventParser.parseLine(line)!!
        val event = EventParser.toEvent(msg)
        assertEquals(EventType.PERMISSION, event.type)
        assertEquals("mon-projet", event.project)
        assertEquals("1a2b3c4d", event.session)
        assertEquals("Claude veut exécuter : rm -rf build", event.message)
    }

    @Test
    fun `ignore les evenements keepalive et open`() {
        assertNull(EventParser.parseLine("""{"id":"x","time":1,"event":"keepalive","topic":"t"}"""))
        assertNull(EventParser.parseLine("""{"id":"x","time":1,"event":"open","topic":"t"}"""))
        assertNull(EventParser.parseLine("pas du json"))
    }

    @Test
    fun `un message sans tag devient OTHER`() {
        val msg = EventParser.parseLine("""{"id":"x","time":1,"event":"message","topic":"t","message":"hello"}""")!!
        val event = EventParser.toEvent(msg)
        assertEquals(EventType.OTHER, event.type)
        assertEquals("hello", event.message)
    }

    @Test
    fun `heures calmes qui traversent minuit`() {
        assertTrue(CompanionSettings.isInQuietHours(23, 22, 7))
        assertTrue(CompanionSettings.isInQuietHours(3, 22, 7))
        assertFalse(CompanionSettings.isInQuietHours(12, 22, 7))
        assertTrue(CompanionSettings.isInQuietHours(13, 12, 14))
        assertFalse(CompanionSettings.isInQuietHours(14, 12, 14))
    }

    @Test
    fun `les urgences passent pendant les heures calmes si autorise`() {
        val s = CompanionSettings(quietHoursEnabled = true, quietStart = 22, quietEnd = 7, quietAllowsUrgent = true)
        assertFalse(s.isMuted(EventType.PERMISSION, 23))
        assertTrue(s.isMuted(EventType.TASK_DONE, 23))
        assertFalse(s.isMuted(EventType.TASK_DONE, 12))
    }

    @Test
    fun `validation du sujet`() {
        assertTrue(CompanionSettings.TOPIC_REGEX.matches("claude-companion-ab12_x"))
        assertFalse(CompanionSettings.TOPIC_REGEX.matches("avec espace"))
        assertFalse(CompanionSettings.TOPIC_REGEX.matches("a/b"))
    }
}
