package `in`.senthilnathan_dev.sentrack.feature.work

import `in`.senthilnathan_dev.sentrack.core.data.work.WorkTaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskMathTest {
    private fun task(due: Long?, status: String = "OPEN") = WorkTaskEntity("t", "c", "x", null, status, 0, due, null, 0)

    @Test
    fun dueText_coversOverdueTodayTomorrowAndDone() {
        assertEquals("Overdue by 2 days", TaskMath.dueText(task(98), 100))
        assertEquals("Overdue by 1 day", TaskMath.dueText(task(99), 100))
        assertEquals("Due today", TaskMath.dueText(task(100), 100))
        assertEquals("Due tomorrow", TaskMath.dueText(task(101), 100))
        assertNull(TaskMath.dueText(task(98, "DONE"), 100))
        assertNull(TaskMath.dueText(task(null), 100))
    }

    @Test
    fun overdueAndDueToday_ignoreDoneAndUndated() {
        val list = listOf(task(99), task(100), task(null), task(99, "DONE"))
        assertEquals(1, TaskMath.overdue(list, 100).size)
        assertEquals(1, TaskMath.dueToday(list, 100).size)
    }
}
