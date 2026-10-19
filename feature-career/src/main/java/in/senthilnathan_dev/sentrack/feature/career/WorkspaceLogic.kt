package `in`.senthilnathan_dev.sentrack.feature.career

import `in`.senthilnathan_dev.sentrack.core.data.career.WorkspaceItemEntity

/** Rules for the workspace launcher and the onboarding and offboarding checklists. */
internal object WorkspaceLogic {
    /** Makes what the user typed into a web address: adds https:// when no scheme is given. Blank in, null out. */
    fun normalizeUrl(input: String): String? {
        val t = input.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() }) return null
        return if (t.contains("://")) t else "https://$t"
    }

    /** "mail.company.com" from "https://mail.company.com/inbox?x=1". */
    fun host(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')

    /** "Opened 12 times" or "Not opened yet". */
    fun usageText(item: WorkspaceItemEntity): String = when (item.launchCount) {
        0 -> "Not opened yet"
        1 -> "Opened once"
        else -> "Opened ${item.launchCount} times"
    }

    /** Most used first; ties by name. Archived items are the caller's concern. */
    fun byUsage(items: List<WorkspaceItemEntity>): List<WorkspaceItemEntity> =
        items.sortedWith(compareByDescending<WorkspaceItemEntity> { it.launchCount }.thenBy { it.name.lowercase() })

    val ONBOARDING = listOf(
        "Collect the offer letter and joining documents",
        "Set up the work email and sign in to chat",
        "Install the apps and bookmark the sites you need (add them in Workspace)",
        "Note your manager, buddy and HR contacts",
        "Enrol in payroll, PF and insurance",
        "Add your leave policy in Work",
    )

    val OFFBOARDING = listOf(
        "Hand over tasks and documents",
        "Save the appreciation mails and screenshots you want to keep",
        "Download payslips, tax forms and experience letter",
        "Return the laptop, ID card and other assets",
        "Remove work accounts from your personal phone",
        "Archive the company's apps and sites in Workspace",
        "Confirm final settlement and PF transfer",
    )
}
