package `in`.senthilnathan_dev.sentrack.feature.work

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.attachments.AttachmentService
import `in`.senthilnathan_dev.sentrack.core.attachments.AttachmentViewerScreen
import `in`.senthilnathan_dev.sentrack.core.security.vault.FileVault
import `in`.senthilnathan_dev.sentrack.core.data.work.AppreciationEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.WorkAttachmentEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.WorkPermissionEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.WorkTaskEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.AttendanceEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.WorkRepository
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.LocalExternalFlowStarter
import kotlinx.coroutines.launch
import java.time.LocalDate

private sealed interface WorkRoute {
    data object Home : WorkRoute
    data class CompanyEditor(val id: String) : WorkRoute
    data object ArchivedCompanies : WorkRoute
    data class LeaveEditor(val id: String) : WorkRoute
    data class TaskEditor(val id: String) : WorkRoute
    data class AppreciationEditor(val id: String) : WorkRoute
    data class PermissionEditor(val id: String) : WorkRoute
    data class Viewer(val attachmentId: String) : WorkRoute
}

private enum class WorkSection(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TODAY("Today", Icons.Filled.Schedule),
    TASKS("Tasks", Icons.Filled.TaskAlt),
    LEAVE("Leave", Icons.Filled.CalendarMonth),
    RECORDS("Records", Icons.Filled.Star),
    COMPANIES("Companies", Icons.Filled.Business),
}

/**
 * The Work tab: the company you work for, punching in and out, and leave. Tasks, appreciation and the daily summary
 * join it in later steps. Keeps its own back stack like the other tabs.
 */
@Composable
fun WorkTab(repo: WorkRepository, files: FileVault, topActions: @Composable RowScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    val beginExternalFlow = LocalExternalFlowStarter.current

    val companies by remember(repo) { repo.companies() }.collectAsState(initial = emptyList())
    val types by remember(repo) { repo.leaveTypes() }.collectAsState(initial = emptyList())
    val leaves by remember(repo) { repo.leaves() }.collectAsState(initial = emptyList())
    val attendance by remember(repo) { repo.attendance() }.collectAsState(initial = emptyList())
    val tasks by remember(repo) { repo.tasks() }.collectAsState(initial = emptyList())
    val appreciations by remember(repo) { repo.appreciations() }.collectAsState(initial = emptyList())
    val permissions by remember(repo) { repo.permissions() }.collectAsState(initial = emptyList())
    val workAttachments by remember(repo) { repo.attachments() }.collectAsState(initial = emptyList())
    val attachmentService = remember(files) { AttachmentService(context, files) }

    var section by rememberSaveable { mutableStateOf(WorkSection.TODAY) }
    var companyId by rememberSaveable { mutableStateOf("") }
    val active = companies.filterNot { it.archived }
    val company = active.firstOrNull { it.id == companyId } ?: active.firstOrNull()

    val stack = remember { mutableStateListOf<WorkRoute>(WorkRoute.Home) }
    fun open(r: WorkRoute) { stack.add(r) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1) { back() }

    var notice by remember { mutableStateOf<String?>(null) }
    var punching by remember { mutableStateOf(false) }

    /** Punch in, or punch out if a punch is open today. Location is read once, now, if allowed. */
    fun punch() {
        val c = company ?: return
        scope.launch {
            punching = true; notice = null
            val location = Locations.current(context)
            val now = System.currentTimeMillis()
            val today = LocalDate.now().toEpochDay()
            val open = attendance.firstOrNull { it.companyId == c.id && it.dayEpoch == today && it.outMillis == null }
            if (open != null) {
                repo.saveAttendance(open.copy(outMillis = now, outLat = location?.first, outLon = location?.second))
                notice = "Punched out."
            } else {
                repo.saveAttendance(
                    AttendanceEntity(newId(), c.id, today, now, null, location?.first, location?.second, null, null, null),
                )
                notice = "Punched in."
            }
            punching = false
        }
    }

    // ---- Appreciation attachments
    var importing by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var importOwner by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf<WorkAttachmentEntity?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val owner = importOwner
        if (owner != null && uris.isNotEmpty()) {
            scope.launch {
                importing = true; notice = null; problem = null
                var added = 0
                val failed = mutableListOf<String>()
                for (uri in uris) {
                    try {
                        val f = attachmentService.importFile(uri)
                        repo.saveAttachment(WorkAttachmentEntity(newId(), owner, f.name, f.mimeType, f.sizeBytes, f.vaultFileId, System.currentTimeMillis()))
                        added++
                    } catch (e: Exception) {
                        failed += (uri.lastPathSegment ?: "a file")
                    }
                }
                importing = false
                if (added > 0) notice = "Attached $added file(s), encrypted."
                if (failed.isNotEmpty()) problem = "Could not attach: " + failed.joinToString(", ")
            }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val a = exporting
        if (uri != null && a != null) {
            scope.launch {
                notice = null; problem = null
                if (attachmentService.export(a.vaultFileId, uri)) notice = "Saved a readable copy of ${a.name}." else problem = "Could not save the copy."
            }
        }
    }

    // Ask for location once; punch either way so a "no" never blocks attendance.
    var firstAsk by rememberSaveable { mutableStateOf(true) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { punch() }
    fun onPunchTapped() {
        if (Locations.hasPermission(context) || !firstAsk) { punch() } else {
            firstAsk = false
            beginExternalFlow()
            permissionLauncher.launch(Locations.PERMISSIONS)
        }
    }

    when (val current = stack.last()) {
        WorkRoute.Home -> AppScreen(
            "Work",
            actions = topActions,
            floatingActionButton = {
                if (section == WorkSection.TASKS && company != null) ExtendedFloatingActionButton(
                    onClick = { open(WorkRoute.TaskEditor(newId())) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New task") },
                )
            },
            bottomBar = {
                PrimaryTabRow(selectedTabIndex = section.ordinal, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    WorkSection.entries.forEach { s ->
                        Tab(selected = section == s, onClick = { section = s }, text = { Text(s.label) }, icon = { Icon(s.icon, contentDescription = null) })
                    }
                }
            },
        ) {
            if (section != WorkSection.COMPANIES) {   // (every other page is per company)
                if (company == null) NoCompanyHint { open(WorkRoute.CompanyEditor(newId())) }
                else CompanyPicker(active, company) { companyId = it }
            }
            when (section) {
                WorkSection.TODAY -> company?.let { c ->
                    TodayContent(
                        company = c,
                        records = attendance.filter { it.companyId == c.id },
                        notice = notice, busy = punching,
                        onPunch = ::onPunchTapped,
                        onDelete = { r -> scope.launch { repo.deleteAttendance(r.id) } },
                    )
                    DailySummary(
                        tasks = tasks.filter { it.companyId == c.id },
                        permissionsToday = permissions.filter { it.companyId == c.id && it.dayEpoch == LocalDate.now().toEpochDay() },
                        onOpenTasks = { section = WorkSection.TASKS },
                    )
                }
                WorkSection.TASKS -> company?.let { c ->
                    TasksContent(
                        tasks = tasks.filter { it.companyId == c.id },
                        onOpen = { open(WorkRoute.TaskEditor(it)) },
                        onToggle = { t ->
                            scope.launch {
                                val done = t.status != "DONE"
                                repo.saveTask(t.copy(status = if (done) "DONE" else "OPEN", completedEpochDay = if (done) LocalDate.now().toEpochDay() else null))
                            }
                        },
                    )
                    androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(72.dp))   // clear of the floating button
                }
                WorkSection.RECORDS -> company?.let { c ->
                    RecordsContent(
                        appreciations = appreciations.filter { it.companyId == c.id },
                        permissions = permissions.filter { it.companyId == c.id },
                        attachmentCount = { id -> workAttachments.count { it.ownerId == id } },
                        onOpenAppreciation = { notice = null; problem = null; open(WorkRoute.AppreciationEditor(it)) },
                        onNewAppreciation = { notice = null; problem = null; open(WorkRoute.AppreciationEditor(newId())) },
                        onOpenPermission = { open(WorkRoute.PermissionEditor(it)) },
                        onNewPermission = { open(WorkRoute.PermissionEditor(newId())) },
                    )
                }
                WorkSection.LEAVE -> company?.let { c ->
                    LeaveContent(
                        company = c,
                        types = types.filter { it.companyId == c.id },
                        leaves = leaves.filter { it.companyId == c.id },
                        onAdd = { open(WorkRoute.LeaveEditor(newId())) },
                        onOpen = { open(WorkRoute.LeaveEditor(it)) },
                    )
                }
                WorkSection.COMPANIES -> CompaniesContent(
                    companies = companies,
                    onOpen = { open(WorkRoute.CompanyEditor(it)) },
                    onAdd = { open(WorkRoute.CompanyEditor(newId())) },
                    onOpenArchived = { open(WorkRoute.ArchivedCompanies) },
                )
            }
        }

        is WorkRoute.CompanyEditor -> {
            val existing = companies.firstOrNull { it.id == current.id }
            CompanyEditorScreen(
                id = current.id,
                existing = existing,
                types = types.filter { it.companyId == current.id },
                onSave = { c, leaveTypes ->
                    scope.launch {
                        repo.saveCompany(c)
                        repo.saveLeaveTypes(c.id, leaveTypes)
                        companyId = c.id
                    }
                    back()
                },
                onToggleLeft = {
                    existing?.let { c ->
                        scope.launch {
                            val left = !c.archived
                            repo.saveCompany(c.copy(archived = left, endEpochDay = if (left) LocalDate.now().toEpochDay() else null))
                        }
                    }
                    back()
                },
                onDelete = { scope.launch { repo.deleteCompany(current.id).forEach { attachmentService.delete(it) } }; back() },
                onBack = ::back,
            )
        }

        is WorkRoute.TaskEditor -> {
            val c = company
            if (c == null) back() else TaskEditorScreen(
                id = current.id, company = c, existing = tasks.firstOrNull { it.id == current.id },
                onSave = { t -> scope.launch { repo.saveTask(t) }; back() },
                onDelete = { scope.launch { repo.deleteTask(current.id) }; back() },
                onBack = ::back,
            )
        }

        is WorkRoute.AppreciationEditor -> {
            val c = company
            if (c == null) back() else AppreciationEditorScreen(
                id = current.id, company = c,
                existing = appreciations.firstOrNull { it.id == current.id },
                attachments = workAttachments.filter { it.ownerId == current.id },
                importing = importing, notice = notice, problem = problem,
                onSave = { a -> scope.launch { repo.saveAppreciation(a) }; back() },
                onDelete = { scope.launch { repo.deleteAppreciation(current.id).forEach { attachmentService.delete(it) } }; back() },
                onAddAttachment = { importOwner = current.id; beginExternalFlow(); picker.launch(arrayOf("*/*")) },
                onOpenAttachment = { open(WorkRoute.Viewer(it)) },
                onExportAttachment = { a -> exporting = a; beginExternalFlow(); exporter.launch(a.name) },
                onDeleteAttachment = { a -> scope.launch { repo.deleteAttachment(a.id); attachmentService.delete(a.vaultFileId); notice = "Removed ${a.name}." } },
                onBack = ::back,
            )
        }

        is WorkRoute.PermissionEditor -> {
            val c = company
            if (c == null) back() else PermissionEditorScreen(
                id = current.id, company = c,
                existing = permissions.firstOrNull { it.id == current.id },
                usedCategories = permissions.map { it.category }.distinct(),
                onSave = { p -> scope.launch { repo.savePermission(p) }; back() },
                onDelete = { scope.launch { repo.deletePermission(current.id) }; back() },
                onBack = ::back,
            )
        }

        is WorkRoute.Viewer -> {
            val a = workAttachments.firstOrNull { it.id == current.attachmentId }
            if (a == null) back() else AttachmentViewerScreen(
                name = a.name, mimeType = a.mimeType, vaultFileId = a.vaultFileId,
                service = attachmentService, files = files,
                onExport = { exporting = a; beginExternalFlow(); exporter.launch(a.name) },
                onBack = ::back,
            )
        }

        WorkRoute.ArchivedCompanies -> ArchivedCompaniesScreen(companies, onOpen = { open(WorkRoute.CompanyEditor(it)) }, onBack = ::back)

        is WorkRoute.LeaveEditor -> {
            val c = company
            if (c == null) back() else LeaveEditorScreen(
                id = current.id,
                company = c,
                types = types.filter { it.companyId == c.id },
                existing = leaves.firstOrNull { it.id == current.id },
                onSave = { entry -> scope.launch { repo.saveLeave(entry) }; back() },
                onDelete = { scope.launch { repo.deleteLeave(current.id) }; back() },
                onBack = ::back,
            )
        }
    }
}
