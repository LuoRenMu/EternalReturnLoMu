package cn.luorenmu.onebot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class AdminServerPortTest {
    @Test
    fun defaultsTo5752AndIncrementsPastConflicts() {
        val occupied = setOf(5752, 5753, 5754)
        assertEquals(5755, AdminServerPort.resolve(emptyArray()) { it !in occupied })
    }

    @Test
    fun acceptsSupportedArgumentFormats() {
        assertEquals(6100, AdminServerPort.resolve(arrayOf("6100")) { true })
        assertEquals(6200, AdminServerPort.resolve(arrayOf("--port", "6200")) { true })
        assertEquals(6300, AdminServerPort.resolve(arrayOf("--port=6300")) { true })
    }

    @Test
    fun rejectsInvalidPorts() {
        assertFailsWith<IllegalStateException> { AdminServerPort.parse(arrayOf("--port")) }
        assertFailsWith<IllegalStateException> { AdminServerPort.parse(arrayOf("abc")) }
        assertFailsWith<IllegalArgumentException> { AdminServerPort.parse(arrayOf("70000")) }
    }
}
