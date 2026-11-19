package app.liteiptv.domain

object FavoritesLogic {
    fun toggle(order: List<String>, url: String): List<String> {
        val index = order.indexOf(url)
        if (index >= 0) return order.filterNot { it == url }
        return order + url
    }

    fun move(order: List<String>, from: Int, to: Int): List<String> {
        if (order.isEmpty() || from !in order.indices) return order
        val dest = to.coerceIn(0, order.lastIndex)
        if (from == dest) return order
        val copy = order.toMutableList()
        val item = copy.removeAt(from)
        copy.add(dest, item)
        return copy
    }
}
