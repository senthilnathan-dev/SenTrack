package `in`.senthilnathan_dev.sentrack.feature.career

import `in`.senthilnathan_dev.sentrack.core.data.career.WorkspaceItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceLogicTest {
    private fun item(name: String, count: Int) = WorkspaceItemEntity(name, null, name, "SITE", "x", null, count, null, false, 0)

    @Test
    fun normalizeUrl_addsSchemeAndRejectsJunk() {
        assertEquals("https://mail.company.com", WorkspaceLogic.normalizeUrl(" mail.company.com "))
        assertEquals("http://intranet/home", WorkspaceLogic.normalizeUrl("http://intranet/home"))
        assertNull(WorkspaceLogic.normalizeUrl("   "))
        assertNull(WorkspaceLogic.normalizeUrl("not a url"))
    }

    @Test
    fun host_stripsSchemePathAndQuery() {
        assertEquals("mail.company.com", WorkspaceLogic.host("https://mail.company.com/inbox?x=1"))
        assertEquals("intranet", WorkspaceLogic.host("http://intranet"))
    }

    @Test
    fun usage_textAndOrdering() {
        assertEquals("Not opened yet", WorkspaceLogic.usageText(item("a", 0)))
        assertEquals("Opened once", WorkspaceLogic.usageText(item("a", 1)))
        assertEquals("Opened 7 times", WorkspaceLogic.usageText(item("a", 7)))
        val ordered = WorkspaceLogic.byUsage(listOf(item("b", 2), item("a", 2), item("c", 9)))
        assertEquals(listOf("c", "a", "b"), ordered.map { it.name })
    }
}
