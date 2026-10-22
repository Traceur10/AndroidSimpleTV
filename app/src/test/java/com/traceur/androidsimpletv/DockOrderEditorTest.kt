package com.traceur.androidsimpletv

import org.junit.Assert.*
import org.junit.Test

class DockOrderEditorTest {
    @Test fun `picked app follows repeated moves and stays at boundaries`() {
        val editor = DockOrderEditor(listOf("a", "b", "c", "d"))
        editor.pick(0)
        editor.move(1)
        editor.move(1)
        assertEquals(listOf("b", "c", "a", "d"), editor.order)
        assertEquals("a", editor.picked)
        editor.move(100)
        assertEquals(3, editor.pickedIndex)
        editor.move(1)
        assertEquals(3, editor.pickedIndex)
        editor.move(-100)
        assertEquals(0, editor.pickedIndex)
    }

    @Test fun `cancel restores last placed order not initial order`() {
        val editor = DockOrderEditor(listOf("a", "b", "c"))
        editor.pick(0)
        editor.move(2)
        assertEquals(listOf("b", "c", "a"), editor.place())
        assertNull(editor.picked)
        editor.pick(1)
        editor.move(-1)
        editor.cancel()
        assertEquals(listOf("b", "c", "a"), editor.order)
        assertNull(editor.picked)
    }
}
