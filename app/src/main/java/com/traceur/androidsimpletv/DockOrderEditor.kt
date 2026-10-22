package com.traceur.androidsimpletv

/** A move is a preview until placed. Back restores the last committed order. */
internal class DockOrderEditor(initial: List<String>) {
    private var committed = initial.distinct()
    var order: List<String> = committed
        private set
    var picked: String? = null
        private set
    val pickedIndex: Int get() = order.indexOf(picked)

    fun pick(index: Int) { if (picked == null) picked = order.getOrNull(index) }
    fun move(delta: Int) {
        val from = pickedIndex
        if (from < 0) return
        val to = (from + delta).coerceIn(0, order.lastIndex)
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }
    fun place(): List<String> {
        committed = order.toList()
        picked = null
        return committed
    }
    fun cancel() { order = committed; picked = null }
}
