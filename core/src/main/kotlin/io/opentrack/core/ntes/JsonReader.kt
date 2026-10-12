package io.opentrack.core.ntes

/**
 * A tiny JSON reader for core (pure JVM: org.json is Android-only, and java.time-style stdlib
 * JSON does not exist). Supports objects, arrays, strings, numbers, booleans and null; it is only
 * a reader, and it never invents values the text does not contain (3.4).
 */
sealed interface JsonValue {
    data class JsonObject(val entries: Map<String, JsonValue>) : JsonValue {
        fun getEntries(): Map<String, JsonValue> = entries
        fun get(key: String): JsonValue? = entries[key]
        fun getString(key: String): String? = (entries[key] as? JsonString)?.value
        fun optString(key: String): String = getString(key) ?: ""
        fun getBoolean(key: String): Boolean? = (entries[key] as? JsonBoolean)?.value
        fun optBoolean(key: String): Boolean = getBoolean(key) ?: false
        fun getArray(key: String): JsonArray? = entries[key] as? JsonArray
        fun getObject(key: String): JsonObject? = entries[key] as? JsonObject
    }

    data class JsonArray(val items: List<JsonValue>) : JsonValue {
        val size: Int get() = items.size
        fun getObjectAt(index: Int): JsonObject? = items.getOrNull(index) as? JsonObject
    }

    data class JsonString(val value: String) : JsonValue
    data class JsonNumber(val text: String) : JsonValue
    data class JsonBoolean(val value: Boolean) : JsonValue
    data object JsonNull : JsonValue
}

object JsonReader {
    fun parse(text: String): JsonValue? = try {
        val state = State(text)
        state.skipWhitespace()
        val value = state.readValue() ?: return null
        state.skipWhitespace()
        if (!state.atEnd()) null else value
    } catch (e: Exception) {
        null
    }

    private class State(val text: String) {
        var pos = 0

        fun atEnd(): Boolean = pos >= text.length

        fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        fun readValue(): JsonValue? {
            if (atEnd()) return null
            return when (text[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> JsonValue.JsonString(readString())
                't' -> literal("true", JsonValue.JsonBoolean(true))
                'f' -> literal("false", JsonValue.JsonBoolean(false))
                'n' -> literal("null", JsonValue.JsonNull)
                else -> readNumber()
            }
        }

        private fun readObject(): JsonValue? {
            expect('{')
            val entries = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (peek() == '}') { pos++; return JsonValue.JsonObject(entries) }
            while (true) {
                skipWhitespace()
                if (peek() != '"') return null
                val key = readString()
                skipWhitespace()
                if (peek() != ':') return null
                expect(':')
                skipWhitespace()
                val value = readValue() ?: return null
                entries[key] = value
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> { pos++; return JsonValue.JsonObject(entries) }
                    else -> return null
                }
            }
        }

        private fun readArray(): JsonValue? {
            expect('[')
            val items = mutableListOf<JsonValue>()
            skipWhitespace()
            if (peek() == ']') { pos++; return JsonValue.JsonArray(items) }
            while (true) {
                skipWhitespace()
                val value = readValue() ?: return null
                items.add(value)
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> { pos++; return JsonValue.JsonArray(items) }
                    else -> return null
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonReadFailure()
                when (val c = text[pos]) {
                    '"' -> { pos++; return sb.toString() }
                    '\\' -> {
                        pos++
                        if (atEnd()) throw JsonReadFailure()
                        when (val esc = text[pos]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                val hex = text.substring(pos + 1, pos + 5)
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw JsonReadFailure()
                        }
                        pos++
                    }
                    else -> { sb.append(c); pos++ }
                }
            }
        }

        private fun readNumber(): JsonValue? {
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] in "-+.eE")) pos++
            if (pos == start) return null
            return JsonValue.JsonNumber(text.substring(start, pos))
        }

        private fun literal(word: String, value: JsonValue): JsonValue? {
            if (!text.startsWith(word, pos)) return null
            pos += word.length
            return value
        }

        private fun peek(): Char = if (atEnd()) '\u0000' else text[pos]

        private fun expect(c: Char) {
            if (peek() != c) throw JsonReadFailure()
            pos++
        }
    }

    private class JsonReadFailure : Exception()
}
