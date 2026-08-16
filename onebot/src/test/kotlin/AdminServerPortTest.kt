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
    fun usesConfiguredPortAndIncrementsPastConflicts() {
        val occupied = setOf(8080, 8081, 8082)
        assertEquals(8083, AdminServerPort.resolve(emptyArray(), 8080) { it !in occupied })
    }

    @Test
    fun acceptsSupportedArgumentFormats() {
        assertEquals(6100, AdminServerPort.resolve(arrayOf("6100"), 8080) { true })
        assertEquals(6200, AdminServerPort.resolve(arrayOf("--port", "6200"), 8080) { true })
        assertEquals(6300, AdminServerPort.resolve(arrayOf("--port=6300"), 8080) { true })
    }

    @Test
    fun rejectsInvalidPorts() {
        assertFailsWith<IllegalStateException> { AdminServerPort.parse(arrayOf("--port"), 8080) }
        assertFailsWith<IllegalStateException> { AdminServerPort.parse(arrayOf("abc"), 8080) }
        assertFailsWith<IllegalArgumentException> { AdminServerPort.parse(arrayOf("70000"), 8080) }
        assertFailsWith<IllegalArgumentException> { AdminServerPort.parse(emptyArray(), 0) }
    }
}
