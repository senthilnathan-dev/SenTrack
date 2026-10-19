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
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.EmojiEvents
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
import `in`.senthilnathan_dev.sentrack.core.data.personal.AchievementEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.ImportantDateEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.LifeRepository
import `in`.senthilnathan_dev.sentrack.core.data.personal.PersonalAttachmentEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.PlanItemEntity
import `in`.senthilnathan_dev.sentrack.core.data.reminders.ReminderEntity
import `in`.senthilnathan_dev.sentrack.core.sync.ScheduleSpec
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteAttachmentEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.NoteRepository
import `in`.senthilnathan_dev.sentrack.core.security.vault.FileVault
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.LocalExternalFlowStarter
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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
    data class PlanEditor(val itemId: String) : PersonalRoute
    data object ArchivedPlans : PersonalRoute
    data class AchievementEditor(val id: String) : PersonalRoute
    data class DateEditor(val id: String) : PersonalRoute
    data object ArchivedMoments : PersonalRoute
    data class LifeViewer(val attachmentId: String) : PersonalRoute
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
    lifeRepo: LifeRepository,
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
    var section by rememberSaveable { mutableStateOf(PersonalSection.TIMELINE) }

    // ---- Plans, achievements, important dates
    val planItems by remember(lifeRepo) { lifeRepo.planItems() }.collectAsState(initial = emptyList())
    val planSteps by remember(lifeRepo) { lifeRepo.planSteps() }.collectAsState(initial = emptyList())
    val achievements by remember(lifeRepo) { lifeRepo.achievements() }.collectAsState(initial = emptyList())
    val importantDates by remember(lifeRepo) { lifeRepo.importantDates() }.collectAsState(initial = emptyList())
    val lifeAttachments by remember(lifeRepo) { lifeRepo.attachments() }.collectAsState(initial = emptyList())
    val plansData = remember(planItems, planSteps, lifeAttachments) { PlansData(planItems, planSteps, lifeAttachments) }
    val momentsData = remember(achievements, importantDates, lifeAttachments) { MomentsData(achievements, importantDates, lifeAttachments) }

    fun reminderEntity(id: String, title: String, owner: String, ownerId: String, at: Long?, repeat: ScheduleSpec?) = ReminderEntity(
        id = id, title = title, note = null, ownerKind = owner, ownerId = ownerId, style = "NOTIFICATION",
        oneOffAtMillis = at, repeatEncoded = repeat?.encode(), enabled = true, completed = false, createdAtMillis = System.currentTimeMillis(),
    )

    fun millisAt(day: LocalDate, time: LocalTime) = day.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Saves a plan item and keeps its one-off "target date" reminder (id item-<id>) in step. */
    fun savePlan(draft: PlanDraft) {
        scope.launch {
            lifeRepo.savePlanItem(draft.item, draft.steps)
            val reminderId = "item-${draft.item.id}"
            val target = draft.item.targetEpochDay
            val at = target?.let { millisAt(LocalDate.ofEpochDay(it), LocalTime.of(9, 0)) }
            if (draft.remind && at != null && at > System.currentTimeMillis() && !draft.item.archived) {
                reminderRepo.save(reminderEntity(reminderId, draft.item.title, "personal:plan", draft.item.id, at, null))
            } else {
                reminderRepo.delete(reminderId)
            }
        }
    }

    fun togglePlanArchive(item: PlanItemEntity) {
        scope.launch {
            val archived = !item.archived
            lifeRepo.savePlanItem(item.copy(archived = archived, updatedAtMillis = System.currentTimeMillis()), plansData.stepsOf(item.id).map { it.title to it.done })
            reminderRepo.setEnabled("item-${item.id}", !archived)
        }
    }

    /** The tick on a plan card: done now, or back to to-do. */
    fun togglePlanDone(item: PlanItemEntity) {
        scope.launch {
            val done = item.statusOf() != PlanStatus.DONE
            lifeRepo.savePlanItem(
                item.copy(
                    status = if (done) PlanStatus.DONE.name else PlanStatus.TODO.name,
                    completedEpochDay = if (done) LocalDate.now().toEpochDay() else null,
                    updatedAtMillis = System.currentTimeMillis(),
                ),
                plansData.stepsOf(item.id).map { it.title to it.done },
            )
            reminderRepo.setEnabled("item-${item.id}", !done)
        }
    }

    /**
     * Saves an important date and keeps its reminder (id date-<id>) in step: yearly dates repeat every year,
     * one-offs ring once. The reminder day is the date minus the chosen lead time.
     */
    fun saveDate(d: ImportantDateEntity) {
        scope.launch {
            lifeRepo.saveImportantDate(d)
            val reminderId = "date-${d.id}"
            val time = LocalTime.ofSecondOfDay(d.remindMinuteOfDay * 60L)
            val origin = LocalDate.ofEpochDay(d.dateEpochDay)
            val next = DateMath.nextOccurrence(origin, d.yearly, LocalDate.now())
            if (d.remindDaysBefore < 0 || d.archived || next == null) {
                reminderRepo.delete(reminderId)
            } else if (d.yearly) {
                val rd = DateMath.reminderDay(next, d.remindDaysBefore)
                val day = if (rd.monthValue == 2 && rd.dayOfMonth == 29) 28 else rd.dayOfMonth   // keep it ringing in non-leap years
                reminderRepo.save(reminderEntity(reminderId, d.title, "personal:date", d.id, null, ScheduleSpec.Yearly(rd.monthValue, day, time)))
            } else {
                val at = millisAt(DateMath.reminderDay(next, d.remindDaysBefore), time)
                if (at > System.currentTimeMillis()) reminderRepo.save(reminderEntity(reminderId, d.title, "personal:date", d.id, at, null))
                else reminderRepo.delete(reminderId)
            }
        }
    }

    fun toggleDateArchive(d: ImportantDateEntity) = saveDate(d.copy(archived = !d.archived))


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

    var importing by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    // ---- Timeline: everything with a date, newest first
    val timeline = run {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now().toEpochDay()
        buildList {
            notes.filterNot { it.archived }.forEach { n ->
                add(TimelineEntry(java.time.Instant.ofEpochMilli(n.updatedAtMillis).atZone(zone).toLocalDate().toEpochDay(), TimelineIcons.note, n.title.ifBlank { "Untitled note" }, NoteFormat.snippet(n.body)) { open(PersonalRoute.Editor(n.id)) })
            }
            achievements.filterNot { it.archived }.forEach { a ->
                add(TimelineEntry(a.dateEpochDay, TimelineIcons.achievement, a.title, "Achievement · ${a.category}") { open(PersonalRoute.AchievementEditor(a.id)) })
            }
            importantDates.filterNot { it.archived }.filter { it.dateEpochDay <= today }.forEach { d ->
                add(TimelineEntry(d.dateEpochDay, TimelineIcons.date, d.title, d.kindOf().label) { open(PersonalRoute.DateEditor(d.id)) })
            }
            planItems.filterNot { it.archived }.forEach { p ->
                p.completedEpochDay?.let { add(TimelineEntry(it, TimelineIcons.plan, p.title, "Finished · ${p.kindOf().label}") { open(PersonalRoute.PlanEditor(p.id)) }) }
            }
        }
    }

    // ---- Attachments on plans and achievements
    var lifeOwner by remember { mutableStateOf<Pair<String, String>?>(null) }
    var lifeExporting by remember { mutableStateOf<PersonalAttachmentEntity?>(null) }
    val lifePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val owner = lifeOwner
        if (owner != null && uris.isNotEmpty()) {
            scope.launch {
                importing = true; notice = null; problem = null
                var added = 0
                val failed = mutableListOf<String>()
                for (uri in uris) {
                    try {
                        val f = attachmentService.importFile(uri)
                        lifeRepo.saveAttachment(
                            PersonalAttachmentEntity(
                                id = UUID.randomUUID().toString(), ownerKind = owner.first, ownerId = owner.second, name = f.name,
                                mimeType = f.mimeType, sizeBytes = f.sizeBytes, vaultFileId = f.vaultFileId, addedAtMillis = System.currentTimeMillis(),
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
    val lifeExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val attachment = lifeExporting
        if (uri != null && attachment != null) {
            scope.launch {
                notice = null; problem = null
                if (attachmentService.export(attachment.vaultFileId, uri)) notice = "Saved a readable copy of ${attachment.name}." else problem = "Could not save the copy."
            }
        }
    }

    // ---- Attachments: import from the phone, save a readable copy
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
                // Timeline and Moments have no floating button: the timeline only shows, Moments has its own add buttons.
                val label = when (section) {
                    PersonalSection.NOTES -> "New note"
                    PersonalSection.HABITS -> "New habit"
                    PersonalSection.PLANS -> "New plan"
                    else -> null
                }
                if (label != null) ExtendedFloatingActionButton(
                    onClick = {
                        notice = null; problem = null
                        when (section) {
                            PersonalSection.NOTES -> open(PersonalRoute.Editor(UUID.randomUUID().toString()))
                            PersonalSection.HABITS -> open(PersonalRoute.HabitEditor(null))
                            PersonalSection.PLANS -> open(PersonalRoute.PlanEditor(newId()))
                            else -> {}
                        }
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(label) },
                )
            },
        ) {
            when (section) {
                PersonalSection.TIMELINE -> TimelineContent(timeline)
                PersonalSection.PLANS -> PlansContent(
                    data = plansData,
                    onOpen = { notice = null; problem = null; open(PersonalRoute.PlanEditor(it)) },
                    onToggleDone = ::togglePlanDone,
                    onOpenArchived = { open(PersonalRoute.ArchivedPlans) },
                )
                PersonalSection.MOMENTS -> MomentsContent(
                    data = momentsData,
                    onOpenAchievement = { notice = null; problem = null; open(PersonalRoute.AchievementEditor(it)) },
                    onOpenDate = { open(PersonalRoute.DateEditor(it)) },
                    onNewAchievement = { notice = null; problem = null; open(PersonalRoute.AchievementEditor(newId())) },
                    onNewDate = { open(PersonalRoute.DateEditor(newId())) },
                    onOpenArchived = { open(PersonalRoute.ArchivedMoments) },
                )
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

        is PersonalRoute.PlanEditor -> {
            val item = planItems.firstOrNull { it.id == current.itemId }
            PlanEditorScreen(
                id = current.itemId,
                existing = item,
                steps = plansData.stepsOf(current.itemId),
                hasReminder = reminders.any { it.id == "item-${current.itemId}" },
                attachments = plansData.attachmentsOf(current.itemId),
                importing = importing, notice = notice, problem = problem,
                onSave = { draft -> savePlan(draft); back() },
                onToggleArchive = { item?.let { togglePlanArchive(it) }; back() },
                onDelete = {
                    scope.launch {
                        lifeRepo.deletePlanItem(current.itemId).forEach { attachmentService.delete(it) }
                        reminderRepo.delete("item-${current.itemId}")
                        back()
                    }
                },
                onAddAttachment = { lifeOwner = "PLAN" to current.itemId; beginExternalFlow(); lifePicker.launch(arrayOf("*/*")) },
                onOpenAttachment = { open(PersonalRoute.LifeViewer(it)) },
                onExportAttachment = { a -> lifeExporting = a; beginExternalFlow(); lifeExporter.launch(a.name) },
                onDeleteAttachment = { a -> scope.launch { lifeRepo.deleteAttachment(a.id); attachmentService.delete(a.vaultFileId); notice = "Removed ${a.name}." } },
                onBack = ::back,
            )
        }

        PersonalRoute.ArchivedPlans -> ArchivedPlansScreen(
            data = plansData,
            onOpen = { open(PersonalRoute.PlanEditor(it)) },
            onRestore = { togglePlanArchive(it) },
            onBack = ::back,
        )

        is PersonalRoute.AchievementEditor -> {
            val existing = achievements.firstOrNull { it.id == current.id }
            AchievementEditorScreen(
                id = current.id,
                existing = existing,
                attachments = momentsData.attachmentsOf("ACHIEVEMENT", current.id),
                importing = importing, notice = notice, problem = problem,
                onSave = { a -> scope.launch { lifeRepo.saveAchievement(a) }; back() },
                onToggleArchive = { existing?.let { a -> scope.launch { lifeRepo.saveAchievement(a.copy(archived = !a.archived)) } }; back() },
                onDelete = {
                    scope.launch {
                        lifeRepo.deleteAchievement(current.id).forEach { attachmentService.delete(it) }
                        back()
                    }
                },
                onAddAttachment = { lifeOwner = "ACHIEVEMENT" to current.id; beginExternalFlow(); lifePicker.launch(arrayOf("*/*")) },
                onOpenAttachment = { open(PersonalRoute.LifeViewer(it)) },
                onExportAttachment = { a -> lifeExporting = a; beginExternalFlow(); lifeExporter.launch(a.name) },
                onDeleteAttachment = { a -> scope.launch { lifeRepo.deleteAttachment(a.id); attachmentService.delete(a.vaultFileId); notice = "Removed ${a.name}." } },
                onBack = ::back,
            )
        }

        is PersonalRoute.DateEditor -> {
            val existing = importantDates.firstOrNull { it.id == current.id }
            DateEditorScreen(
                id = current.id,
                existing = existing,
                onSave = { d -> saveDate(d); back() },
                onToggleArchive = { existing?.let { toggleDateArchive(it) }; back() },
                onDelete = {
                    scope.launch {
                        lifeRepo.deleteImportantDate(current.id)
                        reminderRepo.delete("date-${current.id}")
                        back()
                    }
                },
                onBack = ::back,
            )
        }

        PersonalRoute.ArchivedMoments -> ArchivedMomentsScreen(
            data = momentsData,
            onOpenAchievement = { open(PersonalRoute.AchievementEditor(it)) },
            onOpenDate = { open(PersonalRoute.DateEditor(it)) },
            onRestoreAchievement = { a -> scope.launch { lifeRepo.saveAchievement(a.copy(archived = false)) } },
            onRestoreDate = { toggleDateArchive(it) },
            onBack = ::back,
        )

        is PersonalRoute.LifeViewer -> {
            val a = lifeAttachments.firstOrNull { it.id == current.attachmentId }
            if (a == null) back() else AttachmentViewerScreen(
                name = a.name,
                mimeType = a.mimeType,
                vaultFileId = a.vaultFileId,
                service = attachmentService,
                files = files,
                onExport = { lifeExporting = a; beginExternalFlow(); lifeExporter.launch(a.name) },
                onBack = ::back,
            )
        }
    }
}

/** The sections of the Personal tab. */
private enum class PersonalSection(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TIMELINE("Timeline", Icons.Filled.Timeline),
    NOTES("Notes", Icons.Filled.EditNote),
    HABITS("Habits", Icons.Filled.Loop),
    PLANS("Plans", Icons.Filled.Flag),
    MOMENTS("Moments", Icons.Filled.EmojiEvents),
}
