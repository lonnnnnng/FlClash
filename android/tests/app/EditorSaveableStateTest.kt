package com.lonnnnnng.flclash.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.snapshots.SnapshotMutableState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class EditorSaveableStateTest {
    @Test fun frameworkDefaultLazyKeyKeepsTypeAndIndexAsMapKeyAcrossJson() {
        val constructor = Class.forName("androidx.compose.foundation.lazy.layout.DefaultLazyKey")
            .getDeclaredConstructor(Int::class.javaPrimitiveType).apply { isAccessible = true }
        val key = constructor.newInstance(7)
        val saved = EditorSavedValue.capture(mapOf(key to "private-row-draft"))
        val restored = roundTrip(saved).restore() as Map<*, *>
        assertEquals(key.javaClass, restored.keys.single()!!.javaClass)
        assertEquals("private-row-draft", restored[key])
        assertEquals(EditorSavedValue("lazy-key", "7"), EditorSavedValue.capture(restored.keys.single()))
    }

    @Test fun typedValuesAndStatePoliciesSurviveJsonWithoutNumberCoercion() {
        val values = listOf(null, "secret", true, 2.toByte(), 3.toShort(), 4, 5L, 6f, 7.0, '字', java.util.UUID.randomUUID(), listOf(null, 9), mapOf(2 to "two"))
        val restored = roundTrip(EditorSavedValue.capture(values)).restore() as List<*>
        assertEquals(values, restored)
        for (policy in listOf(structuralEqualityPolicy<Any?>(), referentialEqualityPolicy(), neverEqualPolicy())) {
            val state = roundTrip(EditorSavedValue.capture(mutableStateOf<Any?>(values, policy))).restore() as SnapshotMutableState<*>
            assertSame(policy, state.policy)
            assertEquals(values, state.value)
        }
        val arrays = listOf(byteArrayOf(1, 2), shortArrayOf(2), intArrayOf(3), longArrayOf(4), floatArrayOf(5f),
            doubleArrayOf(6.0), charArrayOf('字'), booleanArrayOf(true), arrayOf("a", null))
        for (array in arrays) {
            val value = roundTrip(EditorSavedValue.capture(array)).restore()
            assertEquals(array.javaClass, value!!.javaClass)
            assertEquals(EditorSavedValue.capture(array), EditorSavedValue.capture(value))
        }
    }

    @Test fun textFieldSaverPreservesSelectionAndSensitiveContentOnlyInExplicitTree() {
        val original = TextFieldValue("未确认的 YAML\nsecret", TextRange(2, 8))
        val saved = with(TextFieldValue.Saver) { SaverScope { true }.save(original) }
        val restored = TextFieldValue.Saver.restore(roundTrip(EditorSavedValue.capture(saved)).restore()!!)
        assertEquals(original, restored)
        assertFalse(EditorSavedValue.capture(saved).toString().contains("secret"))
    }

    @Test fun capturedStateIsImmutableAndUnknownObjectOrPolicyFailsClosed() {
        val state = mutableStateOf("before")
        val saved = EditorSavedValue.capture(state)
        state.value = "after"
        assertEquals("before", (saved.restore() as MutableState<*>).value)
        assertTrue(runCatching { EditorSavedValue.capture(Any()) }.isFailure)
        val custom = object : SnapshotMutationPolicy<String> { override fun equivalent(a: String, b: String) = a.length == b.length }
        assertTrue(runCatching { EditorSavedValue.capture(mutableStateOf("value", custom)) }.isFailure)
        assertTrue(runCatching { EditorSavedValue("unknown").restore() }.isFailure)
        assertTrue(runCatching { EditorSavedValue("state", "unknown", listOf(saved)).restore() }.isFailure)
        assertTrue(runCatching { EditorSavedValue("map", children = listOf(saved)).restore() }.isFailure)
    }

    private fun roundTrip(value: EditorSavedValue) = Gson().fromJson(Gson().toJson(value), EditorSavedValue::class.java)
}
