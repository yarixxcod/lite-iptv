package player.vvvion.app.ui

enum class MessageKind { INFO, SUCCESS, ERROR }

data class UiMessage(
    val id: Long,
    val kind: MessageKind,
    val code: String? = null,
    val raw: String? = null,
    val dismissMs: Long,
)

data class MessageSnapshot(
    val current: UiMessage?,
    val pending: Int,
)

class MessageQueue {
    private var nextId = 1L
    private val pending = ArrayDeque<UiMessage>()
    var current: UiMessage? = null
        private set

    fun post(
        kind: MessageKind,
        code: String? = null,
        raw: String? = null,
        dismissMs: Long = defaultDismiss(kind),
    ): MessageSnapshot {
        if (kind == MessageKind.SUCCESS) dropErrors()
        val message = UiMessage(nextId++, kind, code, raw, dismissMs)
        if (current == null) current = message else pending.addLast(message)
        return snapshot()
    }

    fun dismiss(id: Long? = current?.id): MessageSnapshot {
        if (id != null && current?.id == id) current = null
        if (current == null) current = pending.removeFirstOrNull()
        return snapshot()
    }

    fun clear(): MessageSnapshot {
        pending.clear()
        current = null
        return snapshot()
    }

    private fun dropErrors() {
        if (current?.kind == MessageKind.ERROR) current = null
        pending.removeAll { it.kind == MessageKind.ERROR }
        if (current == null) current = pending.removeFirstOrNull()
    }

    private fun snapshot() = MessageSnapshot(current, pending.size)

    companion object {
        fun defaultDismiss(kind: MessageKind): Long = when (kind) {
            MessageKind.SUCCESS -> 3_500L
            MessageKind.INFO -> 4_500L
            MessageKind.ERROR -> 7_000L
        }
    }
}
