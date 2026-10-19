package `in`.senthilnathan_dev.sentrack.feature.personal

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.attachments.AttachmentService
import `in`.senthilnathan_dev.sentrack.core.attachments.AttachmentViewerScreen
import `in`.senthilnathan_dev.sentrack.core.alerts.ReminderRepository
import `in`.senthilnathan_dev.sentrack.core.data.personal.HabitEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.HabitRepository
import `in`.senthilnathan_dev.sentrack.core.data.reminders.ReminderEntity
import `in`.senthilnathan_dev.sentrack.core.sync.ScheduleSpec
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteAttachmentEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteRepository
import `in`.senthilnathan_dev.sentrack.core.security.vault.FileVault
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.LocalExternalFlowStarter
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.util.UUID

/** Where the user is inside the Personal tab. */
private sealed interface PersonalRoute {
    data object Home : PersonalRoute
    data class Editor(val noteId: String) : PersonalRoute
    data class Viewer(val attachmentId: String) : PersonalRoute
    data object Archived : PersonalRoute
    data object Folders : PersonalRoute
    data class HabitEditor(val habitId: String?) : PersonalRoute
    data class HabitDetail(val habitId: String) : PersonalRoute
    data object ArchivedHabits : PersonalRoute
}

/**
 * The Personal tab. Notes for now; habits, plans, achievements, important dates and the timeline join it in later
 * steps. Keeps its own back stack, like the Finance tab.
 *
 * @param topActions buttons the app shell wants in the top bar (Reminders, Settings, Lock).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalTab(
    repo: NoteRepository,
    habitRepo: HabitRepository,
    reminderRepo: ReminderRepository,
    files: FileVault,
    topActions: @Composable RowScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    val beginExternalFlow = LocalExternalFlowStarter.current
    val attachmentService = remember(files) { AttachmentService(context, files) }

    val notes by remember(repo) { repo.notes() }.collectAsState(initial = emptyList())
    val folders by remember(repo) { repo.folders() }.collectAsState(initial = emptyList())
    val tags by remember(repo) { repo.tags() }.collectAsState(initial = emptyList())
    val links by remember(repo) { repo.tagLinks() }.collectAsState(initial = emptyList())
    val attachments by remember(repo) { repo.attachments() }.collectAsState(initial = emptyList())
    val data = remember(notes, folders, tags, links, attachments) { NotesData(notes, folders, tags, links, attachments) }
    // Words from all your notes, for the typing suggestion strip. Computed on the phone only; never leaves it.
    val vocabulary = remember(notes) { EditAssist.vocabulary(notes.map { it.title + " " + NoteFormat.plainText(it.body) }) }

    // ---- Habits and their reminders
    val habits by remember(habitRepo) { habitRepo.habits() }.collectAsState(initial = emptyList())
    val habitLogs by remember(habitRepo) { habitRepo.logs() }.collectAsState(initial = emptyList())
    val habitsData = remember(habits, habitLogs) { HabitsData(habits, habitLogs) }
    val reminders by remember(reminderRepo) { reminderRepo.reminders() }.collectAsState(initial = emptyList())
    var section by rememberSaveable { mutableStateOf(PersonalSection.NOTES) }

    /** Saves a habit and creates, updates or removes its reminder to match what the editor asked for. */
    fun saveHabit(draft: HabitDraft) {
        scope.launch {
            habitRepo.save(draft.habit)
            val reminderId = "habit-${draft.habit.id}"
            val time = draft.reminderTime
            if (time != null && !draft.habit.archived) {
                val spec: ScheduleSpec = if (draft.habit.period() == HabitPeriod.WEEK) {
                    ScheduleSpec.Weekly(setOf(draft.reminderDay ?: DayOfWeek.SUNDAY), time)
                } else {
                    val days = draft.habit.days()
                    if (days.isEmpty() || days.size == 7) ScheduleSpec.Daily(time) else ScheduleSpec.Weekly(days, time)
                }
                reminderRepo.save(
                    ReminderEntity(
                        id = reminderId, title = draft.habit.name, note = null, ownerKind = "personal:habit", ownerId = draft.habit.id,
                        style = "NOTIFICATION", oneOffAtMillis = null, repeatEncoded = spec.encode(), enabled = true,
                        completed = false, createdAtMillis = System.currentTimeMillis(),
                    ),
                )
            } else {
                reminderRepo.delete(reminderId)
            }
        }
    }

    /** Archiving a habit pauses its reminder; restoring turns it back on. */
    fun toggleHabitArchive(habit: HabitEntity) {
        scope.launch {
            val archived = !habit.archived
            habitRepo.save(habit.copy(archived = archived))
            reminderRepo.setEnabled("habit-${habit.id}", !archived)
        }
    }

    val stack = remember { mutableStateListOf<PersonalRoute>(PersonalRoute.Home) }
    val route = stack.last()
    fun open(r: PersonalRoute) { stack.add(r) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1) { back() }

    // ---- Attachments: import from the phone, save a readable copy
    var importing by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var importNoteId by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf<NoteAttachmentEntity?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val noteId = importNoteId
        if (noteId != null && uris.isNotEmpty()) {
            scope.launch {
                importing = true; notice = null; problem = null
                var added = 0
                val failed = mutableListOf<String>()
                for (uri in uris) {
                    try {
                        val f = attachmentService.importFile(uri)
                        repo.saveAttachment(
                            NoteAttachmentEntity(
                                id = UUID.randomUUID().toString(), noteId = noteId, name = f.name, mimeType = f.mimeType,
                                sizeBytes = f.sizeBytes, vaultFileId = f.vaultFileId, addedAtMillis = System.currentTimeMillis(),
                            ),
                        )
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
        val attachment = exporting
        if (uri != null && attachment != null) {
            scope.launch {
                notice = null; problem = null
                if (attachmentService.export(attachment.vaultFileId, uri)) notice = "Saved a readable copy of ${attachment.name}." else problem = "Could not save the copy."
            }
        }
    }

    when (val current = route) {
        PersonalRoute.Home -> AppScreen(
            "Personal",
            actions = topActions,
            // The section switcher sits at the bottom, where the thumb rests (just above the app's module bar).
            bottomBar = {
                PrimaryTabRow(selectedTabIndex = section.ordinal, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    PersonalSection.entries.forEach { s ->
                        Tab(
                            selected = section == s,
                            onClick = { section = s },
                            text = { Text(s.label) },
                            icon = { Icon(s.icon, contentDescription = null) },
                        )
                    }
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        when (section) {
                            PersonalSection.NOTES -> { notice = null; problem = null; open(PersonalRoute.Editor(UUID.randomUUID().toString())) }
                            PersonalSection.HABITS -> open(PersonalRoute.HabitEditor(null))
                        }
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(if (section == PersonalSection.NOTES) "New note" else "New habit") },
                )
            },
        ) {
            when (section) {
                PersonalSection.NOTES -> NotesContent(
                    data = data,
                    onOpen = { notice = null; problem = null; open(PersonalRoute.Editor(it)) },
                    onOpenArchived = { open(PersonalRoute.Archived) },
                    onOpenFolders = { open(PersonalRoute.Folders) },
                )
                PersonalSection.HABITS -> HabitsContent(
                    data = habitsData,
                    onLog = { id, day, amount -> scope.launch { habitRepo.setLog(id, day.toEpochDay(), amount) } },
                    onOpen = { open(PersonalRoute.HabitDetail(it)) },
                    onOpenArchived = { open(PersonalRoute.ArchivedHabits) },
                )
            }
            Spacer(Modifier.height(72.dp))   // keeps the last card clear of the floating button
        }

        is PersonalRoute.Editor -> NoteEditorScreen(
            noteId = current.noteId,
            existing = notes.firstOrNull { it.id == current.noteId },
            vocabulary = vocabulary,
            data = data,
            attachments = attachments.filter { it.noteId == current.noteId },
            notice = notice,
            problem = problem,
            importing = importing,
            onSave = { note, tagNames -> scope.launch { repo.saveNote(note, tagNames) } },
            onAddFolder = { name -> scope.launch { repo.addFolder(name) } },
            onPickAttachments = {
                importNoteId = current.noteId
                beginExternalFlow()
                picker.launch(arrayOf("*/*"))
            },
            onOpenAttachment = { open(PersonalRoute.Viewer(it)) },
            onExportAttachment = { a -> exporting = a; beginExternalFlow(); exporter.launch(a.name) },
            onDeleteAttachment = { a ->
                scope.launch {
                    repo.deleteAttachment(a.id)
                    attachmentService.delete(a.vaultFileId)
                    notice = "Removed ${a.name}."
                }
            },
            onDeleteNote = {
                scope.launch {
                    repo.deleteNote(current.noteId).forEach { attachmentService.delete(it) }
                    back()
                }
            },
            onBack = ::back,
        )

        is PersonalRoute.Viewer -> {
            val a = attachments.firstOrNull { it.id == current.attachmentId }
            if (a == null) back() else AttachmentViewerScreen(
                name = a.name,
                mimeType = a.mimeType,
                vaultFileId = a.vaultFileId,
                service = attachmentService,
                files = files,
                onExport = { exporting = a; beginExternalFlow(); exporter.launch(a.name) },
                onBack = ::back,
            )
        }

        PersonalRoute.Archived -> ArchivedNotesScreen(
            data = data,
            onOpen = { open(PersonalRoute.Editor(it)) },
            onRestore = { n -> scope.launch { repo.saveNote(n.copy(archived = false, updatedAtMillis = System.currentTimeMillis()), data.tagNames(n.id).toSet()) } },
            onBack = ::back,
        )

        PersonalRoute.Folders -> FoldersScreen(
            data = data,
            onAdd = { name -> scope.launch { repo.addFolder(name) } },
            onSave = { f -> scope.launch { repo.saveFolder(f) } },
            onBack = ::back,
        )

        is PersonalRoute.HabitEditor -> {
            val habit = current.habitId?.let { id -> habits.firstOrNull { it.id == id } }
            val spec = habit?.let { h -> reminders.firstOrNull { it.id == "habit-${h.id}" }?.repeatEncoded?.let { ScheduleSpec.decode(it) } }
            val time = when (spec) {
                is ScheduleSpec.Daily -> spec.time
                is ScheduleSpec.Weekly -> spec.time
                else -> null
            }
            val day = (spec as? ScheduleSpec.Weekly)?.days?.firstOrNull()
            HabitEditorScreen(
                existing = habit,
                initialReminderTime = time,
                initialReminderDay = day,
                onSave = { draft -> saveHabit(draft); back() },
                onBack = ::back,
            )
        }

        is PersonalRoute.HabitDetail -> {
            val habit = habits.firstOrNull { it.id == current.habitId }
            if (habit == null) back() else HabitDetailScreen(
                habit = habit,
                logs = habitsData.logMap(habit.id),
                onLog = { date, amount -> scope.launch { habitRepo.setLog(habit.id, date.toEpochDay(), amount) } },
                onEdit = { open(PersonalRoute.HabitEditor(habit.id)) },
                onToggleArchive = { toggleHabitArchive(habit); back() },
                onBack = ::back,
            )
        }

        PersonalRoute.ArchivedHabits -> ArchivedHabitsScreen(
            data = habitsData,
            onOpen = { open(PersonalRoute.HabitDetail(it)) },
            onRestore = { toggleHabitArchive(it) },
            onBack = ::back,
        )
    }
}

/** The sections of the Personal tab. */
private enum class PersonalSection(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    NOTES("Notes", Icons.Filled.EditNote),
    HABITS("Habits", Icons.Filled.Loop),
}
