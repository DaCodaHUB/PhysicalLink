package com.dacodahub.physicallink.data.ble

import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject

class PhysicalLinkProtocolTest {
    private val fixture = JSONObject(requireNotNull(javaClass.getResource("/protocol-v2/numeric-v2.json")).readText())
    private fun page(name: String): ByteArray {
        val hex = fixture.getJSONObject("wire").getString(name)
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    private fun rejects(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {} catch (_: java.nio.charset.CharacterCodingException) {} }
    @Test fun discovery() {
        assertTrue(PhysicalLinkProtocol.supports(listOf(PhysicalLinkProtocol.SERVICE)))
        assertFalse(PhysicalLinkProtocol.supports(emptyList()))
        assertFalse(PhysicalLinkProtocol.supports(listOf(java.util.UUID.fromString("00001234-0000-1000-8000-00805f9b34fb"))))
    }
    @Test fun singlePage() {
        val t = MetadataTransfer(); assertTrue(t.accept(page("page.one.0")))
        assertTrue(t.complete); assertEquals("temperature.indoor", t.result().sensors.single().id)
    }
    @Test fun multiplePagesAndDelayedSelection() {
        val t = MetadataTransfer(); t.accept(page("page.sixteen.0"))
        assertArrayEquals(byteArrayOf(1, 1), t.command())
        assertFalse(t.accept(page("page.sixteen.0")))
        rejects { t.result() }
        t.accept(page("page.sixteen.1")); assertEquals(16, t.result().sensors.size)
    }
    @Test fun pageBoundariesAndSplitUtf8() {
        for (name in listOf("b495", "b496", "b497", "utf8")) {
            val t = MetadataTransfer(); t.accept(page("page.$name.0"))
            if (!t.complete) t.accept(page("page.$name.1"))
            assertTrue(t.complete); assertTrue(t.result().sensors.isNotEmpty())
        }
    }
    @Test fun maximumManifest() {
        val t = MetadataTransfer()
        for (i in 0..16) t.accept(page("page.max.$i"))
        assertEquals(16, t.result().sensors.size)
    }
    @Test fun corruptHeaders() {
        for (offset in listOf(0, 2, 3, 4, 5, 6)) {
            val p = page("page.one.0"); p[offset] = 99; rejects { MetadataTransfer().accept(p) }
        }
        rejects { MetadataTransfer().accept(page("page.one.0").dropLast(1).toByteArray()) }
        val t = MetadataTransfer(); t.accept(page("page.sixteen.0"))
        val p = page("page.sixteen.1"); p[8] = 0; rejects { t.accept(p) }
    }
    @Test fun strictManifest() {
        val valid = page("page.one.0").drop(16).toByteArray().decodeToString()
        for (text in listOf(valid.dropLast(1), valid + "x", valid.replace("\"version\":2", "\"version\":3"), valid.replace("\"version\":2", "\"version\":2,\"version\":2"), valid.replace("Garden", "\\uD800"))) rejects { MetadataParser.parse(text.toByteArray()) }
        rejects { MetadataParser.parse(byteArrayOf(0xc0.toByte(), 0x80.toByte())) }
    }
}
