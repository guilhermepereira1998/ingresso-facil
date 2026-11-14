package com.wildrift.aicoach

import com.wildrift.aicoach.brain.CooldownManager
import com.wildrift.aicoach.brain.LocalRuleEngine
import com.wildrift.aicoach.brain.SanityFilter
import com.wildrift.aicoach.model.GameStateSnapshot
import com.wildrift.aicoach.model.MinimapDetection
import com.wildrift.aicoach.model.MinimapEntityClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.wildrift.aicoach.vision.VisionPipeline

class TacticalBrainTest {
    @Test
    fun `ocr parsers reject malformed values and understand compact gold`() {
        assertEquals(265, VisionPipeline.parseClockToSeconds("04:25"))
        assertEquals(-1, VisionPipeline.parseClockToSeconds("04:75"))
        assertEquals(-1, VisionPipeline.parseClockToSeconds("menu"))
        assertEquals(1300, VisionPipeline.parseGoldAmount("1.3k"))
        assertEquals(1300, VisionPipeline.parseGoldAmount("1.300"))
        assertEquals(9, VisionPipeline.parseGoldAmount("9"))
        assertEquals(-1, VisionPipeline.parseGoldAmount("abc"))
    }


    @Test
    fun `sanityFilter rejects impossible OCR clock jumps`() {
        val filter = SanityFilter()
        val s1 = GameStateSnapshot(1000L, 252, "04:12", 1100, "1/0/0", "Mid")
        val sGlitch = GameStateSnapshot(2000L, 5052, "84:12", 1105, "1/0/0", "Mid")
        val s2 = GameStateSnapshot(3000L, 254, "04:14", 1110, "1/0/0", "Mid")

        assertNotNull(filter.validate(s1))
        assertNull("Leitura absurda 84:12 deve ser descartada", filter.validate(sGlitch))
        assertNotNull("Leitura válida subsequente 04:14 deve passar", filter.validate(s2))
    }

    @Test
    fun `localRuleEngine triggers dragon warning 35s before 5 minutes`() {
        val engine = LocalRuleEngine(CooldownManager())
        val snapshot = GameStateSnapshot(10_000L, 265, "04:25", 900, "0/0/0", "Mid")

        val calls = engine.evaluate(snapshot, nowMs = 10_000L)
        assertTrue(calls.any { it.ruleId == "OBJ_DRAGON_5M" })
    }

    @Test
    fun `localRuleEngine triggers gank alert when 2 enemies near player and 0 allies`() {
        val engine = LocalRuleEngine(CooldownManager())
        val detections = listOf(
            MinimapDetection(MinimapEntityClass.INIMIGO, 0.90f, 0.52f, 0.48f),
            MinimapDetection(MinimapEntityClass.INIMIGO, 0.88f, 0.45f, 0.53f)
        )
        val snapshot = GameStateSnapshot(20_000L, 190, "03:10", 800, "0/0/0", "Mid", detections, playerXNormalized = 0.5f, playerYNormalized = 0.5f)

        val calls = engine.evaluate(snapshot, nowMs = 20_000L)
        assertEquals(1, calls.count { it.ruleId == "MINIMAP_GANK" })

        val repeatedCalls = engine.evaluate(snapshot, nowMs = 22_000L)
        assertTrue(repeatedCalls.isEmpty())
    }

    @Test
    fun `first call does not require a previous cooldown`() {
        val cooldown = CooldownManager()
        assertTrue(cooldown.canTrigger("first", 90_000L, 1000L))
        assertTrue(!cooldown.canTrigger("first", 90_000L, 1001L))
        assertTrue(cooldown.canTrigger("first", 90_000L, 91_000L))
    }

    @Test
    fun `filter recovers after a capture pause`() {
        val filter = SanityFilter()
        assertNotNull(filter.validate(GameStateSnapshot(1000L, 200, "03:20", 800, "", "Mid")))
        assertNotNull(filter.validate(GameStateSnapshot(31_000L, 230, "03:50", 900, "", "Mid")))
    }

    @Test
    fun `unknown player position never creates a proximity alert`() {
        val snapshot = GameStateSnapshot(30_000L, 200, "03:20", 800, "", "Mid", listOf(
            MinimapDetection(MinimapEntityClass.INIMIGO, .9f, .5f, .5f),
            MinimapDetection(MinimapEntityClass.INIMIGO, .9f, .51f, .5f)
        ))
        assertTrue(LocalRuleEngine().evaluate(snapshot).none { it.ruleId == "MINIMAP_GANK" })
    }

    @Test
    fun `baron planned timer warns once and obeys cooldown`() {
        val engine = LocalRuleEngine()
        val s = GameStateSnapshot(30_000L, 685, "11:25", 800, "", "Mid")
        assertTrue(engine.evaluate(s).any { it.ruleId == "OBJ_BARON_12M" })
        assertTrue(engine.evaluate(s.copy(timestampMs = 31_000L)).isEmpty())
    }

    @Test
    fun `invalid clock never enters tactical state`() {
        assertNull(SanityFilter().validate(GameStateSnapshot(1000L, -1, "--:--", -1, "", "Mid")))
    }
}
