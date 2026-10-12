package io.opentrack.core.ntes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonReaderTest {

    @Test
    fun parsesObjectsArraysStringsNumbersBooleans() {
        val value = JsonReader.parse(
            """{"a":"x","b":12,"c":true,"d":null,"e":["p",{"f":"q"}]}"""
        )
        val obj = value as JsonValue.JsonObject
        assertEquals("x", obj.getString("a"))
        assertEquals("12", (obj.get("b") as JsonValue.JsonNumber).text)
        assertTrue(obj.getBoolean("c")!!)
        assertTrue(obj.get("d") is JsonValue.JsonNull)
        val array = obj.getArray("e")!!
        assertEquals(2, array.size)
        assertEquals("q", array.getObjectAt(1)!!.getString("f"))
    }

    @Test
    fun escapesAreDecoded() {
        val value = JsonReader.parse("{\"s\":\"a\\\"b\\\\c\\nd\\u0041\"}") as JsonValue.JsonObject
        val decoded = value.getString("s")
        assertEquals("a\"b\\c\ndA", decoded)
    }

    @Test
    fun malformedJsonIsNullNotACrash() {
        assertNull(JsonReader.parse("{"))
        assertNull(JsonReader.parse(""))
        assertNull(JsonReader.parse("[1,2"))
        assertNull(JsonReader.parse("not json"))
    }

    @Test
    fun trailingContentIsRejected() {
        assertNull(JsonReader.parse("""{"a":1} garbage"""))
    }
}
