package `in`.senthilnathan_dev.sentrack.feature.personal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.data.personal.PersonalAttachmentEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.PlanItemEntity
import `in`.senthilnathan_dev.sentrack.core.data.personal.PlanStepEntity
import `in`.senthilnathan_dev.sentrack.core.ui.ActionBar
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.DateField
import `in`.senthilnathan_dev.sentrack.core.ui.IconAction
import `in`.senthilnathan_dev.sentrack.core.ui.Message
import `in`.senthilnathan_dev.sentrack.core.ui.MessageKind
import `in`.senthilnathan_dev.sentrack.core.ui.PrimaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.SecondaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.Section
import `in`.senthilnathan_dev.sentrack.core.ui.SwitchRow
import `in`.senthilnathan_dev.sentrack.core.ui.formatDate
import java.time.LocalDate
import java.util.UUID

/** The four kinds of plan item. */
internal enum class PlanKind(val label: String, val plural: String) {
    GOAL("Goal", "Goals"), PLAN("Plan", "Plans"), WISHLIST("Wishlist", "Wishlist"), BUCKET("Bucket list", "Bucket list");

    /** Wishlist and bucket-list items usually carry a price. */
    val hasCost: Boolean get() = this == WISHLIST || this == BUCKET
}

internal enum class PlanStatus(val label: String) { TODO("To do"), DOING("In progress"), DONE("Done") }

internal fun PlanItemEntity.kindOf(): PlanKind = runCatching { PlanKind.valueOf(kind) }.getOrDefault(PlanKind.GOAL)
internal fun PlanItemEntity.statusOf(): PlanStatus = runCatching { PlanStatus.valueOf(status) }.getOrDefault(PlanStatus.TODO)

private val PRIORITIES = listOf("None", "Low", "Medium", "High")

/** Plan items with their steps and attachments, joined for the screens. */
internal class PlansData(val items: List<PlanItemEntity>, steps: List<PlanStepEntity>, val attachments: List<PersonalAttachmentEntity>) {
    private val byItem = steps.groupBy { it.itemId }
    fun stepsOf(id: String): List<PlanStepEntity> = byItem[id] ?: emptyList()
    fun attachmentsOf(id: String): List<PersonalAttachmentEntity> = attachments.filter { it.ownerKind == "PLAN" && it.ownerId == id }

    /** 0 to 1: share of steps done, or the status when there are no steps. */
    fun progress(item: PlanItemEntity): Float {
        val steps = stepsOf(item.id)
        return if (steps.isEmpty()) (if (item.statusOf() == PlanStatus.DONE) 1f else 0f) else steps.count { it.done } / steps.size.toFloat()
    }
}

/** What the editor hands back: the item, its steps, and whether to remind on the target date. */
internal class PlanDraft(val item: PlanItemEntity, val steps: List<Pair<String, Boolean>>, val remind: Boolean)

// ---------------------------------------------------------------------------------------------------------------------
// List

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.PlansContent(
    data: PlansData,
    onOpen: (String) -> Unit,
    onToggleDone: (PlanItemEntity) -> Unit,
    onOpenArchived: () -> Unit,
) {
    var kindName by rememberSaveable { mutableStateOf("ALL") }
    val kind = PlanKind.entries.firstOrNull { it.name == kindName }
    val active = data.items.filterNot { it.archived }.filter { kind == null || it.kindOf() == kind }
    val archivedCount = data.items.count { it.archived }
    val today = LocalDate.now().toEpochDay()

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = kind == null, onClick = { kindName = "ALL" }, label = { Text("All") })
        PlanKind.entries.forEach { k -> FilterChip(selected = kind == k, onClick = { kindName = k.name }, label = { Text(k.plural) }) }
    }

    if (active.isEmpty()) {
        Section("Nothing here yet", "Tap “New plan” for a goal you are working toward, a plan with steps, something you want to buy, or something to do once in your life.") {}
    }
    active.forEach { item ->
        val done = item.statusOf() == PlanStatus.DONE
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.clickable { onOpen(item.id) }.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    val steps = data.stepsOf(item.id)
                    if (steps.isNotEmpty()) LinearProgressIndicator(progress = { data.progress(item) }, modifier = Modifier.fillMaxWidth())
                    val facts = buildList {
                        add(item.kindOf().label)
                        if (steps.isNotEmpty()) add("${steps.count { it.done }}/${steps.size} steps")
                        else add(item.statusOf().label)
                        item.costPaise?.let { add(rupees(it)) }
                        if (done) item.completedEpochDay?.let { add("on ${formatDate(it)}") }
                        else item.targetEpochDay?.let { add(DateMath.countdownFrom(it, today)) }
                    }
                    Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconAction(
                    if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    if (done) "Mark ${item.title} not done" else "Mark ${item.title} done",
                    { onToggleDone(item) },
                    tint = if (done) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified,
                )
            }
        }
    }
    if (archivedCount > 0) SecondaryButton("Archived plans ($archivedCount)", onClick = onOpenArchived, icon = Icons.Filled.Archive)
}

@Composable
internal fun ArchivedPlansScreen(data: PlansData, onOpen: (String) -> Unit, onRestore: (PlanItemEntity) -> Unit, onBack: () -> Unit) {
    val archived = data.items.filter { it.archived }
    AppScreen("Archived plans", onBack = onBack) {
        if (archived.isEmpty()) {
            Section("Nothing archived", "Archive an item from its page to hide it without losing it.") {}
        } else {
            Section("Archived", "Tap one to open it, or restore it.") {
                archived.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).clickable { onOpen(item.id) }.padding(vertical = 8.dp)) {
                            Text(item.title, style = MaterialTheme.typography.bodyLarge)
                            Text(item.kindOf().label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconAction(Icons.Filled.Unarchive, "Restore ${item.title}", { onRestore(item) })
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Editor

/** Create or edit a plan item: kind, status, priority, target date, cost, steps and (once saved) attachments. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlanEditorScreen(
    id: String,
    existing: PlanItemEntity?,
    steps: List<PlanStepEntity>,
    hasReminder: Boolean,
    attachments: List<PersonalAttachmentEntity>,
    importing: Boolean,
    notice: String?,
    problem: String?,
    onSave: (PlanDraft) -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onAddAttachment: () -> Unit,
    onOpenAttachment: (String) -> Unit,
    onExportAttachment: (PersonalAttachmentEntity) -> Unit,
    onDeleteAttachment: (PersonalAttachmentEntity) -> Unit,
    onBack: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(existing?.kindOf() ?: PlanKind.GOAL) }
    var status by rememberSaveable { mutableStateOf(existing?.statusOf() ?: PlanStatus.TODO) }
    var priority by rememberSaveable { mutableStateOf(existing?.priority ?: 0) }
    var hasTarget by rememberSaveable { mutableStateOf(existing?.targetEpochDay != null) }
    var target by rememberSaveable { mutableStateOf(existing?.targetEpochDay ?: LocalDate.now().plusMonths(1).toEpochDay()) }
    var cost by rememberSaveable { mutableStateOf(existing?.costPaise?.let { if (it % 100 == 0L) (it / 100).toString() else "%.2f".format(java.util.Locale.ENGLISH, it / 100.0) }.orEmpty()) }
    var remind by rememberSaveable { mutableStateOf(hasReminder) }
    val stepList = remember { mutableStateListOf<Pair<String, Boolean>>().also { l -> l.addAll(steps.map { it.title to it.done }) } }
    var newStep by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun submit() {
        val costPaise = cost.takeIf { it.isNotBlank() }?.let { parseRupees(it) }
        // A step typed but not yet added still counts.
        val allSteps = stepList.toList() + (if (newStep.isNotBlank()) listOf(newStep.trim() to false) else emptyList())
        when {
            title.isBlank() -> error = "Give it a title."
            cost.isNotBlank() && costPaise == null -> error = "Enter the cost as a number."
            else -> {
                error = null
                val now = System.currentTimeMillis()
                val finished = status == PlanStatus.DONE
                onSave(
                    PlanDraft(
                        item = PlanItemEntity(
                            id = id, kind = kind.name, title = title.trim(), note = note.trim().ifEmpty { null },
                            status = status.name, priority = priority,
                            targetEpochDay = if (hasTarget) target else null,
                            costPaise = costPaise,
                            completedEpochDay = if (finished) (existing?.completedEpochDay ?: LocalDate.now().toEpochDay()) else null,
                            archived = existing?.archived ?: false,
                            createdAtMillis = existing?.createdAtMillis ?: now, updatedAtMillis = now,
                        ),
                        steps = allSteps,
                        remind = remind && hasTarget && !finished,
                    ),
                )
            }
        }
    }

    AppScreen(
        if (existing == null) "New plan" else "Edit plan",
        onBack = onBack,
        actions = {
            if (existing != null) {
                IconAction(if (existing.archived) Icons.Filled.Unarchive else Icons.Filled.Archive, if (existing.archived) "Restore" else "Archive", onToggleArchive)
                IconAction(Icons.Filled.Delete, "Delete permanently", { confirmDelete = true })
            }
        },
        bottomBar = {
            ActionBar {
                Message(error, MessageKind.ERROR)
                PrimaryButton("Save", onClick = ::submit, icon = Icons.Filled.Check)
            }
        },
    ) {
        Section("What") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanKind.entries.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) }) }
            }
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
            if (kind.hasCost) {
                OutlinedTextField(
                    value = cost, onValueChange = { cost = it }, label = { Text("Cost in ₹ (optional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Section("Progress") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanStatus.entries.forEach { s -> FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s.label) }) }
            }
            Text("Priority", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRIORITIES.forEachIndexed { i, label -> FilterChip(selected = priority == i, onClick = { priority = i }, label = { Text(label) }) }
            }
            SwitchRow(title = "Target date", checked = hasTarget, onChange = { hasTarget = it })
            if (hasTarget) {
                DateField("By", target) { target = it }
                SwitchRow(title = "Remind me on that day", subtitle = "A notification at 9 am.", checked = remind, onChange = { remind = it })
            }
        }

        Section("Steps", "Break it into steps. Progress is the share of steps you have ticked.") {
            stepList.forEachIndexed { i, (text, done) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = done, onCheckedChange = { stepList[i] = text to it })
                    Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconAction(Icons.Filled.Close, "Remove step $text", { stepList.removeAt(i) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = newStep, onValueChange = { newStep = it }, label = { Text("Add a step") }, singleLine = true, modifier = Modifier.weight(1f))
                IconAction(Icons.Filled.Add, "Add step", { if (newStep.isNotBlank()) { stepList.add(newStep.trim() to false); newStep = "" } })
            }
        }

        if (existing != null) {
            Message(notice, MessageKind.INFO)
            Message(problem, MessageKind.ERROR)
            AttachmentsPanel(attachments, importing, onAddAttachment, onOpenAttachment, onExportAttachment, onDeleteAttachment)
        } else {
            Section("Attachments", "Save first, then open the item again to attach photos, PDFs or videos.") {}
        }
    }

    if (confirmDelete && existing != null) {
        ConfirmDeleteDialog(existing.title, onConfirm = { confirmDelete = false; onDelete() }, onDismiss = { confirmDelete = false })
    }
}

internal fun newId(): String = UUID.randomUUID().toString()
