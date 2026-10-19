package `in`.senthilnathan_dev.sentrack.feature.work

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
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
}

private enum class WorkSection(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TODAY("Today", Icons.Filled.Schedule),
    LEAVE("Leave", Icons.Filled.CalendarMonth),
    COMPANIES("Companies", Icons.Filled.Business),
}

/**
 * The Work tab: the company you work for, punching in and out, and leave. Tasks, appreciation and the daily summary
 * join it in later steps. Keeps its own back stack like the other tabs.
 */
@Composable
fun WorkTab(repo: WorkRepository, topActions: @Composable RowScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    val beginExternalFlow = LocalExternalFlowStarter.current

    val companies by remember(repo) { repo.companies() }.collectAsState(initial = emptyList())
    val types by remember(repo) { repo.leaveTypes() }.collectAsState(initial = emptyList())
    val leaves by remember(repo) { repo.leaves() }.collectAsState(initial = emptyList())
    val attendance by remember(repo) { repo.attendance() }.collectAsState(initial = emptyList())

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
            bottomBar = {
                PrimaryTabRow(selectedTabIndex = section.ordinal, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    WorkSection.entries.forEach { s ->
                        Tab(selected = section == s, onClick = { section = s }, text = { Text(s.label) }, icon = { Icon(s.icon, contentDescription = null) })
                    }
                }
            },
        ) {
            if (section != WorkSection.COMPANIES) {
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
                onDelete = { scope.launch { repo.deleteCompany(current.id) }; back() },
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
