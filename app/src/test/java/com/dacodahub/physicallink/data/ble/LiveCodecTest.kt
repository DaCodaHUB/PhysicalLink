package com.dacodahub.physicallink.data.ble

import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LiveCodecTest {
    private val fixtures = JSONObject(requireNotNull(javaClass.getResource("/protocol-v2/numeric-v2.json")).readText())
    private fun wire(key: String) = fixtures.getJSONObject("wire").getString(key).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun metadata(key: String = "one") = MetadataParser.parse(wire("manifest.$key"))
    private fun rejects(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} }
    private fun record(sequence: Int, unavailable: Boolean = false): ByteArray = wire(if (unavailable) "record.unavailable" else "record.valid").also {
        it[1] = 0; ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(4, sequence)
    }
    @Test fun goldenRecordsAndInvalidRecords() {
        val valid = LiveCodec.record(wire("record.valid"), 16, 100)
        assertEquals(23.5, valid.value!!, 0.0); assertEquals(42L, valid.sequence)
        assertEquals(110L, valid.acquisitionAgeMs(200))
        assertEquals("Stale", valid.availability(30_100))
        val unavailable = LiveCodec.record(wire("record.unavailable"), 16, 100)
        assertNull(unavailable.value); assertNull(unavailable.acquisitionAgeMs(200))
        assertEquals("Unavailable", unavailable.availability(1_000_000))
        assertEquals(Long.MIN_VALUE, LiveCodec.record(wire("record.negative_zero"), 16, 0).value!!.toRawBits())
        val invalid = fixtures.getJSONObject("invalidRecords")
        invalid.keys().forEach { key -> rejects { LiveCodec.record(invalid.getString(key).chunked(2).map { it.toInt(16).toByte() }.toByteArray(), 16, 0) } }
        rejects { LiveCodec.record(record(0), 1, 0) }
        rejects { LiveCodec.record(record(1, true).also { it[12] = 1 }, 1, 0) }
    }
    @Test fun longImageAndHeaderValidation() {
        val bytes = wire("image.16"); val meta = metadata("sixteen")
        assertEquals(336, bytes.size); assertEquals(16, LiveCodec.image(bytes, meta, 0).records.size)
        for (i in listOf(0, 1, 4, 12, 17)) rejects { LiveCodec.image(bytes.copyOf().also { it[i] = 99 }, meta, 0) }
        rejects { LiveCodec.image(bytes.dropLast(1).toByteArray(), meta, 0) }
        rejects { LiveCodec.image(bytes + byteArrayOf(0), meta, 0) }
    }
    @Test fun initialCaptureBuffersAndMergesOnlyNewerNotifications() {
        val s = LiveSession(metadata()); assertArrayEquals(byteArrayOf(2, 1, 0), s.beginCapture())
        s.notification(record(2), 100); s.notification(record(1), 200)
        assertTrue(s.state.readings.isEmpty())
        assertFalse(s.acceptImage(wire("image.initial"), 300))
        assertTrue(s.acceptImage(wire("image.1"), 400))
        val id = metadata().sensors.single().id
        assertEquals(2L, s.state.readings[id]!!.sequence)
        s.notification(record(2), 500); assertEquals(100L, s.state.readings[id]!!.receivedAtMs)
        s.notification(record(1), 600); assertEquals(2L, s.state.readings[id]!!.sequence)
        s.notification(record(3, true), 700); assertNull(s.state.readings[id]!!.value)
        s.notification(record(5), 800); assertNotNull(s.state.readings[id]!!.value); assertTrue(s.gap)
        assertArrayEquals(byteArrayOf(2, 2, 0), s.beginCapture())
        assertFalse(s.acceptImage(wire("image.1"), 900))
        val image = wire("image.1").also { it[2] = 2 }
        assertTrue(s.acceptImage(image, 1000)); assertEquals(5L, s.state.readings[id]!!.sequence)
    }
    @Test fun invalidImageCannotPublishOrEndSynchronization() {
        val s = LiveSession(metadata()); s.beginCapture()
        rejects { s.acceptImage(wire("image.1").also { it[4] = 0 }, 0) }
        rejects { s.acceptImage(wire("image.1").also { it[2] = 3 }, 0) }
        assertFalse(s.state.synchronized); assertTrue(s.state.readings.isEmpty())
    }
    @Test fun missingNotificationRecoveredByLaterCapture() {
        val s = LiveSession(metadata()); s.beginCapture(); s.acceptImage(wire("image.1"), 0)
        s.beginCapture()
        val bytes = wire("image.1").also { it[2] = 2; ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(20, 9) }
        s.acceptImage(bytes, 30_000)
        assertEquals(9L, s.state.readings.values.single().sequence)
    }
}
