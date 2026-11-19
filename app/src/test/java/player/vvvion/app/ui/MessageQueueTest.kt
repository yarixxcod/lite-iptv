package player.vvvion.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageQueueTest {
    @Test
    fun showsOneAtATimeAndAdvances() {
        val q = MessageQueue()
        q.post(MessageKind.INFO, code = "a")
        val queued = q.post(MessageKind.SUCCESS, code = "b")
        assertEquals("a", queued.current?.code)
        assertEquals(1, queued.pending)
        val after = q.dismiss()
        assertEquals("b", after.current?.code)
        assertEquals(0, after.pending)
        q.dismiss()
        assertNull(q.current)
    }

    @Test
    fun successDropsStaleErrors() {
        val q = MessageQueue()
        q.post(MessageKind.ERROR, code = "old")
        q.post(MessageKind.ERROR, raw = "also")
        val snap = q.post(MessageKind.SUCCESS, code = "ok")
        assertEquals("ok", snap.current?.code)
        assertEquals(0, snap.pending)
        assertEquals(MessageKind.SUCCESS, q.current?.kind)
    }

    @Test
    fun dismissUnknownIdLeavesCurrent() {
        val q = MessageQueue()
        q.post(MessageKind.INFO, code = "stay")
        val id = q.current!!.id
        q.dismiss(id + 10)
        assertEquals("stay", q.current?.code)
        q.dismiss(id)
        assertNull(q.current)
    }
}
