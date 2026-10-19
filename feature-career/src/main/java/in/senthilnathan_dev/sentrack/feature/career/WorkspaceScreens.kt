package `in`.senthilnathan_dev.sentrack.feature.career

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.data.career.ChecklistItemEntity
import `in`.senthilnathan_dev.sentrack.core.data.career.WorkspaceItemEntity
import `in`.senthilnathan_dev.sentrack.core.data.work.CompanyEntity
import `in`.senthilnathan_dev.sentrack.core.ui.ActionBar
import `in`.senthilnathan_dev.sentrack.core.ui.AppScreen
import `in`.senthilnathan_dev.sentrack.core.ui.IconAction
import `in`.senthilnathan_dev.sentrack.core.ui.Message
import `in`.senthilnathan_dev.sentrack.core.ui.MessageKind
import `in`.senthilnathan_dev.sentrack.core.ui.PickerField
import `in`.senthilnathan_dev.sentrack.core.ui.PrimaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.SecondaryButton
import `in`.senthilnathan_dev.sentrack.core.ui.Section

private fun companyName(companies: List<CompanyEntity>, id: String?): String = if (id == null) "Personal" else companies.firstOrNull { it.id == id }?.name ?: "Former company"

// ---------------------------------------------------------------------------------------------------------------------
// Launcher

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.WorkspaceContent(
    items: List<WorkspaceItemEntity>,
    companies: List<CompanyEntity>,
    notice: String?,
    onLaunch: (WorkspaceItemEntity) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: (companyId: String?) -> Unit,
    onOpenChecklists: (String) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("ALL") }   // ALL, PERSONAL or a company id
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val activeCompanies = companies.filterNot { it.archived }
    val inFilter = items.filter { filter == "ALL" || (filter == "PERSONAL" && it.companyId == null) || it.companyId == filter }
    val active = WorkspaceLogic.byUsage(inFilter.filterNot { it.archived })
    val archived = inFilter.filter { it.archived }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = filter == "ALL", onClick = { filter = "ALL" }, label = { Text("All") })
        FilterChip(selected = filter == "PERSONAL", onClick = { filter = "PERSONAL" }, label = { Text("Personal") })
        activeCompanies.forEach { c -> FilterChip(selected = filter == c.id, onClick = { filter = c.id }, label = { Text(c.name) }) }
    }
    Message(notice, MessageKind.ERROR)
    if (active.isEmpty()) Section("Your workspace is empty", "Add the apps and sites you use for work. Open them from here and SenTrack counts how often you use each.") {}
    active.forEach { item -> WorkspaceCard(item, companies, onLaunch, onEdit) }
    SecondaryButton("Add an app or site", onClick = { onAdd(filter.takeIf { it != "ALL" && it != "PERSONAL" }) }, icon = Icons.Filled.Add)
    if (filter != "ALL" && filter != "PERSONAL") SecondaryButton("Onboarding and offboarding checklist", onClick = { onOpenChecklists(filter) }, icon = Icons.Filled.Checklist)
    if (archived.isNotEmpty()) {
        SecondaryButton(if (showArchived) "Hide archived (${archived.size})" else "Archived (${archived.size})", onClick = { showArchived = !showArchived }, icon = Icons.Filled.Archive)
        if (showArchived) archived.forEach { WorkspaceCard(it, companies, onLaunch, onEdit) }
    }
}

@Composable
private fun WorkspaceCard(item: WorkspaceItemEntity, companies: List<CompanyEntity>, onLaunch: (WorkspaceItemEntity) -> Unit, onEdit: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.clickable { onLaunch(item) }.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(if (item.kind == "APP") Icons.Filled.Apps else Icons.Filled.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOf(companyName(companies, item.companyId), WorkspaceLogic.usageText(item)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconAction(Icons.Filled.Edit, "Edit ${item.name}", { onEdit(item.id) })
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Editor

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkspaceEditorScreen(
    id: String,
    existing: WorkspaceItemEntity?,
    companies: List<CompanyEntity>,
    defaultCompanyId: String?,
    onSave: (WorkspaceItemEntity) -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: "SITE") }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var target by rememberSaveable { mutableStateOf(existing?.target.orEmpty()) }
    var companyId by rememberSaveable { mutableStateOf(existing?.companyId ?: defaultCompanyId.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    AppScreen(
        if (existing == null) "Add to workspace" else "Edit",
        onBack = onBack,
        actions = { ArchiveDeleteActions(existing?.archived, onToggleArchive) { confirmDelete = true } },
        bottomBar = {
            ActionBar {
                Message(error, MessageKind.ERROR)
                PrimaryButton("Save", icon = Icons.Filled.Check, onClick = {
                    val resolved = if (kind == "SITE") WorkspaceLogic.normalizeUrl(target) else target.takeIf { it.isNotBlank() }
                    when {
                        resolved == null -> error = if (kind == "SITE") "Enter a web address, for example mail.company.com." else "Choose an app."
                        name.isBlank() -> error = "Give it a name."
                        else -> onSave(
                            WorkspaceItemEntity(
                                id, companyId.ifEmpty { null }, name.trim(), kind, resolved, note.trim().ifEmpty { null },
                                existing?.launchCount ?: 0, existing?.lastLaunchedMillis, existing?.archived ?: false,
                                existing?.createdAtMillis ?: System.currentTimeMillis(),
                            ),
                        )
                    }
                })
            }
        },
    ) {
        Section("What") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = kind == "SITE", onClick = { kind = "SITE"; target = "" }, label = { Text("Website") })
                FilterChip(selected = kind == "APP", onClick = { kind = "APP"; target = "" }, label = { Text("App on this phone") })
            }
            if (kind == "SITE") {
                OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text("Web address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            } else {
                Text(
                    if (target.isBlank()) "No app chosen" else (AppLauncher.appLabel(context, target) ?: target),
                    style = MaterialTheme.typography.bodyLarge,
                )
                SecondaryButton("Choose an app", onClick = { picking = true })
            }
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            PickerField(
                "Belongs to", companyName(companies, companyId.ifEmpty { null }),
                listOf("Personal" to "") + companies.filterNot { it.archived }.map { it.name to it.id },
            ) { companyId = it }
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notes (login hint, never the password)") }, modifier = Modifier.fillMaxWidth())
        }
    }

    if (picking) {
        val apps = remember { AppLauncher.installedApps(context) }
        var query by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Choose an app") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }) { app ->
                            Text(
                                app.label, style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    target = app.packageName
                                    if (name.isBlank()) name = app.label
                                    picking = false
                                }.padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete && existing != null) ConfirmDelete(existing.name, { confirmDelete = false; onDelete() }, { confirmDelete = false })
}

// ---------------------------------------------------------------------------------------------------------------------
// Onboarding and offboarding checklist

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChecklistScreen(
    company: CompanyEntity,
    items: List<ChecklistItemEntity>,
    workspace: List<WorkspaceItemEntity>,
    onSaveItems: (List<ChecklistItemEntity>) -> Unit,
    onDeleteItem: (ChecklistItemEntity) -> Unit,
    onArchiveWorkspaceItem: (WorkspaceItemEntity) -> Unit,
    onBack: () -> Unit,
) {
    var phase by rememberSaveable { mutableStateOf(if (company.archived) "OFFBOARD" else "ONBOARD") }
    var newText by rememberSaveable { mutableStateOf("") }
    val mine = items.filter { it.companyId == company.id && it.phase == phase }.sortedBy { it.sortOrder }
    val done = mine.count { it.done }
    val defaults = if (phase == "ONBOARD") WorkspaceLogic.ONBOARDING else WorkspaceLogic.OFFBOARDING

    fun add(titles: List<String>) {
        val start = (mine.maxOfOrNull { it.sortOrder } ?: -1) + 1
        onSaveItems(titles.mapIndexed { i, t -> ChecklistItemEntity(newId(), company.id, phase, t, false, start + i) })
    }

    AppScreen(company.name, onBack = onBack) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = phase == "ONBOARD", onClick = { phase = "ONBOARD" }, label = { Text("Joining") })
            FilterChip(selected = phase == "OFFBOARD", onClick = { phase = "OFFBOARD" }, label = { Text("Leaving") })
        }
        Section(if (phase == "ONBOARD") "Joining checklist" else "Leaving checklist", if (mine.isEmpty()) null else "$done of ${mine.size} done") {
            if (mine.isEmpty()) Text("Nothing yet. Start from a suggested list, then change it to fit.", style = MaterialTheme.typography.bodyMedium)
            mine.forEachIndexed { i, it ->
                if (i > 0) HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = it.done, onCheckedChange = { checked -> onSaveItems(listOf(it.copy(done = checked))) })
                    Text(it.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconAction(Icons.Filled.Close, "Remove ${it.title}", { onDeleteItem(it) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = newText, onValueChange = { newText = it }, label = { Text("Add an item") }, singleLine = true, modifier = Modifier.weight(1f))
                IconAction(Icons.Filled.Add, "Add item", { if (newText.isNotBlank()) { add(listOf(newText.trim())); newText = "" } })
            }
            if (mine.isEmpty()) SecondaryButton("Use the suggested list", onClick = { add(defaults) })
        }
        if (phase == "OFFBOARD") {
            val live = workspace.filter { it.companyId == company.id && !it.archived }
            Section("Remove access", "Archive the company's apps and sites so they leave your launcher. Their history is kept.") {
                if (live.isEmpty()) Text("Nothing left in the workspace for this company.", style = MaterialTheme.typography.bodyMedium)
                live.forEach { w ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(w.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconAction(Icons.Filled.Archive, "Archive ${w.name}", { onArchiveWorkspaceItem(w) })
                    }
                }
            }
        }
    }
}
