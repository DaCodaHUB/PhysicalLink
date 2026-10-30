package com.dacodahub.physicallink.data.ble

import com.dacodahub.physicallink.domain.model.*
import java.util.UUID

object PhysicalLinkProtocol {
    val SERVICE: UUID = UUID.fromString("b89e2000-7c42-4f9d-9d25-8a639eab5120")
    val METADATA: UUID = UUID.fromString("b89e2001-7c42-4f9d-9d25-8a639eab5120")
    val STATE: UUID = UUID.fromString("b89e2003-7c42-4f9d-9d25-8a639eab5120")
    val CONTROL: UUID = UUID.fromString("b89e2002-7c42-4f9d-9d25-8a639eab5120")
    fun supports(services: List<UUID>): Boolean = SERVICE in services
}

/** One immutable manifest; old selected pages may be polled, never appended. */
class MetadataTransfer {
    var nextPage = 0
        private set
    private var total = 0
    private var count = 0
    private var epoch = ""
    private val bytes = java.io.ByteArrayOutputStream()
    fun command(): ByteArray { require(nextPage in 1 until count); return byteArrayOf(1, nextPage.toByte()) }
    fun accept(value: ByteArray): Boolean {
        fun u(i: Int) = value[i].toInt() and 255
        require(value.size in 17..512) { "Truncated metadata page" }
        require(u(0) == 80 && u(1) == 76 && u(2) == 2 && u(5) == 0) { "Unsupported metadata page header" }
        val index = u(3); val pages = u(4); val length = u(6) or (u(7) shl 8)
        val run = value.sliceArray(8..15).reversed().joinToString("") { "%02x".format(it.toInt() and 255) }
        require(length in 1..8192 && pages == (length + 495) / 496 && index < pages && run != "0000000000000000") { "Invalid metadata page bounds" }
        require(value.size == 16 + minOf(496, length - index * 496)) { "Invalid metadata slice length" }
        if (nextPage == 0) { require(index == 0) { "Expected metadata page zero" }; total = length; count = pages; epoch = run }
        require(total == length && count == pages && epoch == run) { "Metadata changed during retrieval; reconnect" }
        if (index != nextPage) { require(index == nextPage - 1) { "Unexpected metadata page index" }; return false }
        bytes.write(value, 16, value.size - 16); nextPage++
        return true
    }
    val complete: Boolean get() = count > 0 && nextPage == count
    fun result(): SensorManifest {
        check(complete && bytes.size() == total) { "Incomplete metadata" }
        return MetadataParser.parse(bytes.toByteArray()).also { require(it.epoch == epoch) { "Manifest epoch differs from page header" } }
    }
}

object MetadataParser {
    fun parse(bytes: ByteArray): SensorManifest {
        require(bytes.size in 1..8192) { "Metadata exceeds 8192 bytes" }
        val root = StrictJson(bytes.decodeToString(throwOnInvalidSequence = true)).parse() as? Map<*, *> ?: error("Expected manifest object")
        require(root.keys == setOf("version", "deviceId", "device", "epoch", "sensors")) { "Invalid manifest fields" }
        require(root["version"] == 2) { "Unsupported metadata protocol version" }
        fun text(map: Map<*, *>, key: String, min: Int, max: Int): String {
            val s = map[key] as? String ?: error("$key must be a string")
            require(s.encodeToByteArray().size in min..max && '\u0000' !in s) { "Invalid $key length or NUL" }
            s.forEachIndexed { i, c ->
                require(!c.isHighSurrogate() || (i + 1 < s.length && s[i + 1].isLowSurrogate())) { "Invalid Unicode scalar" }
                require(!c.isLowSurrogate() || (i > 0 && s[i - 1].isHighSurrogate())) { "Invalid Unicode scalar" }
            }
            return s
        }
        val id = text(root, "deviceId", 36, 36)
        require(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}").matches(id)) { "Invalid persistent UUID" }
        val epoch = text(root, "epoch", 16, 16)
        require(Regex("[0-9a-f]{16}").matches(epoch) && epoch != "0000000000000000") { "Invalid epoch" }
        val items = root["sensors"] as? List<*> ?: error("Expected sensors array")
        require(items.size in 1..16) { "Expected 1–16 sensors" }
        val sensors = items.map {
            val item = it as? Map<*, *> ?: error("Expected sensor object")
            require(item.keys == setOf("id", "name", "unit")) { "Invalid sensor fields" }
            val sensorId = text(item, "id", 1, 32)
            require(Regex("[a-z][a-z0-9_.-]{0,31}").matches(sensorId)) { "Invalid sensor ID" }
            ManifestSensor(sensorId, text(item, "name", 1, 64), text(item, "unit", 0, 16))
        }
        require(sensors.distinctBy { it.id }.size == sensors.size) { "Duplicate sensor IDs" }
        return SensorManifest(id, text(root, "device", 1, 64), epoch, sensors)
    }
}

/** Restricted strict JSON grammar: manifest values are objects, arrays, strings or integers. */
private class StrictJson(private val s: String) {
    private var p = 0
    private fun ws() { while (p < s.length && s[p] in " \r\n\t") p++ }
    private fun take(c: Char): Boolean { ws(); return if (p < s.length && s[p] == c) { p++; true } else false }
    private fun expect(c: Char) { require(take(c)) { "Invalid JSON at $p" } }
    fun parse(): Any { val v = value(0); ws(); require(p == s.length) { "Trailing JSON data" }; return v }
    private fun value(depth: Int): Any {
        ws(); require(depth <= 4 && p < s.length) { "Truncated or deeply nested JSON" }
        return when (s[p]) {
            '"' -> string()
            '{' -> { p++; val m = linkedMapOf<String, Any>(); if (!take('}')) { do { ws(); val k = string(); expect(':'); require(k !in m) { "Duplicate JSON key" }; m[k] = value(depth + 1) } while (take(',')); expect('}') }; m }
            '[' -> { p++; val a = mutableListOf<Any>(); if (!take(']')) { do { a.add(value(depth + 1)) } while (take(',')); expect(']') }; a }
            else -> { val start = p; if (s[p] == '-') p++; require(p < s.length && s[p] in '0'..'9'); if (s[p] == '0') p++ else while (p < s.length && s[p] in '0'..'9') p++; s.substring(start, p).toInt() }
        }
    }
    private fun string(): String {
        require(p < s.length && s[p++] == '"') { "Expected JSON string" }
        val out = StringBuilder()
        while (p < s.length) {
            val c = s[p++]
            if (c == '"') return out.toString()
            require(c >= ' ') { "Unescaped JSON control" }
            if (c != '\\') out.append(c) else {
                require(p < s.length)
                out.append(when (val e = s[p++]) {
                    '"', '\\', '/' -> e; 'b' -> '\b'; 'f' -> '\u000c'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
                    'u' -> { require(p + 4 <= s.length); val h = s.substring(p, p + 4); require(h.all { it in "0123456789abcdefABCDEF" }); p += 4; h.toInt(16).toChar() }
                    else -> error("Invalid JSON escape")
                })
            }
        }
        error("Unterminated JSON string")
    }
}
