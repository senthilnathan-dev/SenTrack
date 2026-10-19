package `in`.senthilnathan_dev.sentrack.feature.career

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.alerts.ReminderRepository
import `in`.senthilnathan_dev.sentrack.core.attachments.AttachmentService
import `in`.senthilnathan_dev.sentrack.core.attachments.AttachmentViewerScreen
import `in`.senthilnathan_dev.sentrack.core.data.career.CareerAttachmentEntity
import `in`.senthilnathan_dev.sentrack.core.data.career.CareerRepository
import `in`.senthilnathan_dev.sentrack.core.data.career.CertificationEntity
import `in`.senthilnathan_dev.sentrack.core.data.career.InterviewEntity
import `in`.senthilnathan_dev.sentrack.core.data.career.JobApplicationEntity
import `in`.senthilnathan_dev.sentrack.core.data.career.ResumeEntity
import `in`.senthilnathan_dev.sentrack.core.data.career.ChecklistItemEntity
import `in`.senthilnathan_dev.sentrack.core.data.career.WorkspaceItemEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.WorkRepository
import `in`.senthilnathan_dev.sentrack.core.data.reminders.ReminderEntity
import `in`.senthilnathan_dev.sentrack.core.security.vault.FileVault
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.LocalExternalFlowStarter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private sealed interface CareerRoute {
    data object Home : CareerRoute
    data class LearningEditor(val id: String) : CareerRoute
    data class CertEditor(val id: String) : CareerRoute
    data class JobEditor(val id: String) : CareerRoute
    data class ResumeEditor(val id: String) : CareerRoute
    data class ResumeFile(val id: String) : CareerRoute
    data class Viewer(val attachmentId: String) : CareerRoute
    data class WorkspaceEditor(val id: String, val companyId: String?) : CareerRoute
    data class Checklist(val companyId: String) : CareerRoute
}

private enum class CareerSection(val label: String, val icon: ImageVector) {
    LEARNING("Learning", Icons.Filled.School),
    CERTS("Certificates", Icons.Filled.Verified),
    JOBS("Job hunt", Icons.Filled.WorkOutline),
    RESUMES("Resumes", Icons.Filled.Description),
    WORKSPACE("Workspace", Icons.Filled.Apps),
}

/**
 * The Career tab: what you are learning, certifications (with renewal reminders), job applications with interviews
 * (reminded an hour ahead), and your resume versions. The workspace of apps and sites joins it in a later step.
 */
@Composable
fun CareerTab(repo: CareerRepository, workRepo: WorkRepository, reminderRepo: ReminderRepository, files: FileVault, topActions: @Composable RowScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    val beginExternalFlow = LocalExternalFlowStarter.current
    val attachmentService = remember(files) { AttachmentService(context, files) }

    val learning by remember(repo) { repo.learning() }.collectAsState(initial = emptyList())
    val certs by remember(repo) { repo.certifications() }.collectAsState(initial = emptyList())
    val applications by remember(repo) { repo.applications() }.collectAsState(initial = emptyList())
    val interviews by remember(repo) { repo.interviews() }.collectAsState(initial = emptyList())
    val resumes by remember(repo) { repo.resumes() }.collectAsState(initial = emptyList())
    val attachments by remember(repo) { repo.attachments() }.collectAsState(initial = emptyList())
    val workspace by remember(repo) { repo.workspace() }.collectAsState(initial = emptyList())
    val checklist by remember(repo) { repo.checklist() }.collectAsState(initial = emptyList())
    val companies by remember(workRepo) { workRepo.companies() }.collectAsState(initial = emptyList())
    val reminders by remember(reminderRepo) { reminderRepo.reminders() }.collectAsState(initial = emptyList())

    var section by rememberSaveable { mutableStateOf(CareerSection.LEARNING) }
    val stack = remember { mutableStateListOf<CareerRoute>(CareerRoute.Home) }
    fun open(r: CareerRoute) { stack.add(r) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1) { back() }

    var importing by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var importOwner by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf<Pair<String, String>?>(null) }   // vault id to suggested file name

    // ---- Reminders
    fun reminder(id: String, title: String, kind: String, ownerId: String, at: Long) = ReminderEntity(
        id = id, title = title, note = null, ownerKind = kind, ownerId = ownerId, style = "NOTIFICATION",
        oneOffAtMillis = at, repeatEncoded = null, enabled = true, completed = false, createdAtMillis = System.currentTimeMillis(),
    )

    fun saveCert(c: CertificationEntity) {
        scope.launch {
            repo.saveCertification(c)
            val id = "cert-${c.id}"
            val at = c.expiryEpochDay?.let { LocalDate.ofEpochDay(CareerMath.certReminderDay(it)).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            if (at != null && at > System.currentTimeMillis() && !c.archived) reminderRepo.save(reminder(id, "${c.name} expires soon", "career:cert", c.id, at))
            else reminderRepo.delete(id)
        }
    }

    fun saveInterview(iv: InterviewEntity) {
        scope.launch {
            repo.saveInterview(iv)
            val id = "interview-${iv.id}"
            val at = CareerMath.interviewReminderAt(iv.atMillis)
            val company = applications.firstOrNull { it.id == iv.applicationId }?.company
            if (!iv.done && at > System.currentTimeMillis()) reminderRepo.save(reminder(id, "Interview in an hour: ${iv.round}${company?.let { " at $it" } ?: ""}", "career:interview", iv.id, at))
            else reminderRepo.delete(id)
        }
    }

    // ---- Files: certificate attachments and resume versions
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
                        repo.saveAttachment(CareerAttachmentEntity(newId(), owner, f.name, f.mimeType, f.sizeBytes, f.vaultFileId, System.currentTimeMillis()))
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
    val resumePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                importing = true; problem = null
                try {
                    val f = attachmentService.importFile(uri)
                    val r = ResumeEntity(newId(), f.name.substringBeforeLast('.'), null, f.name, f.mimeType, f.sizeBytes, f.vaultFileId, false, System.currentTimeMillis())
                    repo.saveResume(r)
                    open(CareerRoute.ResumeEditor(r.id))
                } catch (e: Exception) {
                    problem = "Could not add that file."
                }
                importing = false
            }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val target = exporting
        if (uri != null && target != null) {
            scope.launch {
                notice = null; problem = null
                if (attachmentService.export(target.first, uri)) notice = "Saved a readable copy of ${target.second}." else problem = "Could not save the copy."
            }
        }
    }
    fun export(vaultId: String, name: String) { exporting = vaultId to name; beginExternalFlow(); exporter.launch(name) }

    when (val current = stack.last()) {
        CareerRoute.Home -> AppScreen(
            "Career",
            actions = topActions,
            bottomBar = {
                PrimaryScrollableTabRow(selectedTabIndex = section.ordinal, containerColor = MaterialTheme.colorScheme.surfaceContainer, edgePadding = 0.dp) {
                    CareerSection.entries.forEach { s ->
                        Tab(selected = section == s, onClick = { section = s }, text = { Text(s.label, maxLines = 1) }, icon = { Icon(s.icon, contentDescription = null) })
                    }
                }
            },
            floatingActionButton = {
                val label = when (section) {
                    CareerSection.LEARNING -> "New learning"
                    CareerSection.CERTS -> "New certificate"
                    CareerSection.JOBS -> "New application"
                    CareerSection.RESUMES -> null
                    CareerSection.WORKSPACE -> null
                }
                if (label != null) ExtendedFloatingActionButton(
                    onClick = {
                        notice = null; problem = null
                        open(
                            when (section) {
                                CareerSection.LEARNING -> CareerRoute.LearningEditor(newId())
                                CareerSection.CERTS -> CareerRoute.CertEditor(newId())
                                else -> CareerRoute.JobEditor(newId())
                            },
                        )
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(label) },
                )
            },
        ) {
            when (section) {
                CareerSection.LEARNING -> LearningContent(learning) { open(CareerRoute.LearningEditor(it)) }
                CareerSection.CERTS -> CertificationsContent(certs, attachmentCount = { id -> attachments.count { it.ownerId == id } }) { notice = null; problem = null; open(CareerRoute.CertEditor(it)) }
                CareerSection.JOBS -> JobsContent(applications, interviews, resumes) { open(CareerRoute.JobEditor(it)) }
                CareerSection.WORKSPACE -> WorkspaceContent(
                    items = workspace, companies = companies, notice = problem,
                    onLaunch = { item ->
                        problem = null
                        beginExternalFlow()   // leaving SenTrack on purpose must not lock it
                        if (AppLauncher.launch(context, item)) scope.launch { repo.recordLaunch(item.id) }
                        else problem = if (item.kind == "APP") "${item.name} is not installed on this phone." else "Could not open ${item.name}."
                    },
                    onEdit = { open(CareerRoute.WorkspaceEditor(it, null)) },
                    onAdd = { open(CareerRoute.WorkspaceEditor(newId(), it)) },
                    onOpenChecklists = { open(CareerRoute.Checklist(it)) },
                )
                CareerSection.RESUMES -> {
                    `in`.senthilnathan_dev.sentrack.core.ui.Message(problem, `in`.senthilnathan_dev.sentrack.core.ui.MessageKind.ERROR)
                    ResumesContent(
                        resumes = resumes, applications = applications, importing = importing,
                        onAdd = { problem = null; beginExternalFlow(); resumePicker.launch(arrayOf("*/*")) },
                        onOpen = { notice = null; problem = null; open(CareerRoute.ResumeEditor(it)) },
                    )
                }
            }
            Spacer(Modifier.height(72.dp))   // keeps the last card clear of the floating button
        }

        is CareerRoute.LearningEditor -> {
            val item = learning.firstOrNull { it.id == current.id }
            LearningEditorScreen(
                id = current.id, existing = item,
                onSave = { l -> scope.launch { repo.saveLearning(l) }; back() },
                onToggleArchive = { item?.let { l -> scope.launch { repo.saveLearning(l.copy(archived = !l.archived)) } }; back() },
                onDelete = { scope.launch { repo.deleteLearning(current.id) }; back() },
                onBack = ::back,
            )
        }

        is CareerRoute.CertEditor -> {
            val item = certs.firstOrNull { it.id == current.id }
            CertificationEditorScreen(
                id = current.id, existing = item,
                attachments = attachments.filter { it.ownerId == current.id },
                hasReminder = reminders.any { it.id == "cert-${current.id}" },
                importing = importing, notice = notice, problem = problem,
                onSave = { c -> saveCert(c); back() },
                onToggleArchive = { item?.let { c -> saveCert(c.copy(archived = !c.archived)) }; back() },
                onDelete = {
                    scope.launch {
                        repo.deleteCertification(current.id).forEach { attachmentService.delete(it) }
                        reminderRepo.delete("cert-${current.id}")
                    }
                    back()
                },
                onAddAttachment = { importOwner = current.id; beginExternalFlow(); picker.launch(arrayOf("*/*")) },
                onOpenAttachment = { open(CareerRoute.Viewer(it)) },
                onExportAttachment = { a -> export(a.vaultFileId, a.name) },
                onDeleteAttachment = { a -> scope.launch { repo.deleteAttachment(a.id); attachmentService.delete(a.vaultFileId); notice = "Removed ${a.name}." } },
                onBack = ::back,
            )
        }

        is CareerRoute.JobEditor -> {
            val item = applications.firstOrNull { it.id == current.id }
            JobEditorScreen(
                id = current.id, existing = item,
                interviews = interviews.filter { it.applicationId == current.id },
                resumes = resumes,
                onSave = { a -> scope.launch { repo.saveApplication(a) }; back() },
                onToggleArchive = { item?.let { a -> scope.launch { repo.saveApplication(a.copy(archived = !a.archived)) } }; back() },
                onDelete = {
                    scope.launch { repo.deleteApplication(current.id).forEach { reminderRepo.delete("interview-$it") } }
                    back()
                },
                onSaveInterview = ::saveInterview,
                onDeleteInterview = { iv -> scope.launch { repo.deleteInterview(iv.id); reminderRepo.delete("interview-${iv.id}") } },
                onBack = ::back,
            )
        }

        is CareerRoute.ResumeEditor -> {
            val r = resumes.firstOrNull { it.id == current.id }
            if (r == null) back() else ResumeEditorScreen(
                resume = r,
                usedBy = applications.filter { it.resumeId == r.id },
                onSave = { updated -> scope.launch { repo.saveResume(updated) }; back() },
                onOpenFile = { open(CareerRoute.ResumeFile(r.id)) },
                onExport = { export(r.vaultFileId, r.fileName) },
                onToggleArchive = { scope.launch { repo.saveResume(r.copy(archived = !r.archived)) }; back() },
                onDelete = { scope.launch { attachmentService.delete(repo.deleteResume(r)) }; back() },
                onBack = ::back,
            )
        }

        is CareerRoute.ResumeFile -> {
            val r = resumes.firstOrNull { it.id == current.id }
            if (r == null) back() else AttachmentViewerScreen(
                name = r.fileName, mimeType = r.mimeType, vaultFileId = r.vaultFileId, service = attachmentService, files = files,
                onExport = { export(r.vaultFileId, r.fileName) }, onBack = ::back,
            )
        }

        is CareerRoute.WorkspaceEditor -> {
            val item = workspace.firstOrNull { it.id == current.id }
            WorkspaceEditorScreen(
                id = current.id, existing = item, companies = companies, defaultCompanyId = current.companyId,
                onSave = { w -> scope.launch { repo.saveWorkspaceItem(w) }; back() },
                onToggleArchive = { item?.let { w -> scope.launch { repo.saveWorkspaceItem(w.copy(archived = !w.archived)) } }; back() },
                onDelete = { scope.launch { repo.deleteWorkspaceItem(current.id) }; back() },
                onBack = ::back,
            )
        }

        is CareerRoute.Checklist -> {
            val c = companies.firstOrNull { it.id == current.companyId }
            if (c == null) back() else ChecklistScreen(
                company = c, items = checklist, workspace = workspace,
                onSaveItems = { list -> scope.launch { repo.saveChecklistItems(list) } },
                onDeleteItem = { scope.launch { repo.deleteChecklistItem(it.id) } },
                onArchiveWorkspaceItem = { w -> scope.launch { repo.saveWorkspaceItem(w.copy(archived = true)) } },
                onBack = ::back,
            )
        }

        is CareerRoute.Viewer -> {
            val a = attachments.firstOrNull { it.id == current.attachmentId }
            if (a == null) back() else AttachmentViewerScreen(
                name = a.name, mimeType = a.mimeType, vaultFileId = a.vaultFileId, service = attachmentService, files = files,
                onExport = { export(a.vaultFileId, a.name) }, onBack = ::back,
            )
        }
    }
}
