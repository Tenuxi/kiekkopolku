package fi.kiekkopolku.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fi.kiekkopolku.app.BuildConfig
import fi.kiekkopolku.app.R
import fi.kiekkopolku.app.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val playerColors = listOf(Color(0xFFE2E2E2), Color(0xFFBDBDBD), Color(0xFF969696), Color(0xFFCBCBCB), Color(0xFFAAAAAA))
private val pagePadding = PaddingValues(20.dp)
private fun date(value: String) = runCatching { LocalDate.parse(value).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())) }.getOrDefault(value)
private fun updated(value: Long) = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KiekkopolkuApp(vm: HistoryViewModel, mapScreen: @Composable (History, (String) -> Unit) -> Unit = { h, open -> CourseMap(h, open) }) {
    val colors = darkColorScheme(
        primary = Color(0xFFE2E2E2), onPrimary = Color(0xFF202020),
        primaryContainer = Color(0xFF393939), onPrimaryContainer = Color(0xFFF1F1F1),
        secondary = Color(0xFFCACACA), onSecondary = Color(0xFF202020),
        secondaryContainer = Color(0xFF363636), onSecondaryContainer = Color(0xFFF0F0F0),
        tertiary = Color(0xFFBDBDBD), onTertiary = Color(0xFF202020),
        background = Color(0xFF151515), onBackground = Color(0xFFF0F0F0),
        surface = Color(0xFF151515), onSurface = Color(0xFFF0F0F0),
        surfaceVariant = Color(0xFF333333), onSurfaceVariant = Color(0xFFBEBEBE),
        surfaceContainer = Color(0xFF202020), surfaceContainerHigh = Color(0xFF2A2A2A),
        surfaceContainerHighest = Color(0xFF303030), surfaceContainerLow = Color(0xFF1C1C1C),
        surfaceContainerLowest = Color(0xFF101010), surfaceTint = Color.Transparent,
        outline = Color(0xFF8C8C8C), outlineVariant = Color(0xFF484848),
        inverseSurface = Color(0xFFE0E0E0), inverseOnSurface = Color(0xFF252525), inversePrimary = Color(0xFF555555),
    )
    MaterialTheme(colorScheme = colors) {
        val state by vm.state.collectAsStateWithLifecycle()
        val busy by vm.busy.collectAsStateWithLifecycle()
        val progress by vm.progress.collectAsStateWithLifecycle()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle, vm) {
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) vm.onOpen() }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        val message by vm.message.collectAsStateWithLifecycle()
        val history = state.history
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var profiles by rememberSaveable { mutableStateOf(false) }
        var courseId by rememberSaveable { mutableStateOf<String?>(null) }
        var roundId by rememberSaveable { mutableStateOf<String?>(null) }
        var roundPlayerId by rememberSaveable { mutableStateOf<String?>(null) }
        val nested = profiles || courseId != null || roundId != null
        val mapVisible = tab == 4 && !nested
        val statisticsVisible = tab == 3 && !nested
        var playerPicker by rememberSaveable { mutableStateOf(false) }
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(mapVisible) { if (mapVisible) { focus.clearFocus(force = true); keyboard?.hide() } }
        fun back() { when { roundId != null -> { roundId = null; roundPlayerId = null }; courseId != null -> courseId = null; else -> profiles = false } }
        BackHandler(nested) { back() }
        val snackbar = remember { SnackbarHostState() }
        val context = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(message) { message?.let { text ->
            val details = text in listOf(R.string.sync_partial, R.string.sync_failed, R.string.sync_limited)
            val result = snackbar.showSnackbar(context.getString(text),
                actionLabel = if (details) context.getString(R.string.sync_details) else null,
                withDismissAction = details, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) { profiles = true; courseId = null; roundId = null }
            vm.message.compareAndSet(text, null)
        } }
        if (playerPicker) AlertDialog(onDismissRequest = { playerPicker = false },
            title = { Text(stringResource(R.string.map_players)) },
            text = { LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(history.players, key = { it.id }) { player ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = player.active, onCheckedChange = { vm.select(player.id, it) })
                        Text(player.name, Modifier.weight(1f))
                    }
                }
            } }, confirmButton = { TextButton(onClick = { playerPicker = false }) { Text(stringResource(R.string.close)) } })
        val labels = listOf(R.string.courses, R.string.rounds, R.string.aces, R.string.stats, R.string.map)
        val icons = listOf(Icons.Default.Place, Icons.AutoMirrored.Filled.List, Icons.Default.StarOutline, Icons.Default.BarChart, Icons.Default.Map)
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = { TopAppBar(title = { Text(stringResource(when { profiles -> R.string.profiles; statisticsVisible -> R.string.stats; else -> R.string.app_name }), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { if (nested) IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (mapVisible && history.players.isNotEmpty()) IconButton(onClick = { playerPicker = true }) {
                        Icon(Icons.Default.FilterList, stringResource(R.string.map_players))
                    }
                    if (busy) {
                        val description = stringResource(R.string.sync_loading)
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp).semantics { contentDescription = description }, strokeWidth = 2.dp)
                        }
                    } else IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
                    if (!profiles) IconButton(onClick = { profiles = true; courseId = null; roundId = null }) { Icon(Icons.Default.PeopleOutline, stringResource(R.string.profiles)) }
                }) },
            bottomBar = { if (!profiles) NavigationBar { labels.forEachIndexed { index, label ->
                NavigationBarItem(selected = tab == index, onClick = { tab = index; courseId = null; roundId = null },
                    icon = { Icon(icons[index], null) }, label = { Text(stringResource(label)) })
            } } },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().testTag("screen-content")) {
                if (!mapVisible) {
                    if (!statisticsVisible) {
                        progress?.let { Text(if (it.phase == "COURSES") stringResource(R.string.sync_courses, it.playerName) else stringResource(R.string.sync_progress, it.playerName, it.processed, it.total),
                            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
                        Text(stringResource(R.string.local_mode), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                        if (history.players.any { it.sample && it.active }) Text(stringResource(R.string.sample_banner),
                            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 20.dp))
                    }
                    if (!profiles && history.players.isNotEmpty()) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(history.players, key = { it.id }) { p -> FilterChip(selected = p.active,
                            onClick = { vm.select(p.id, !p.active) }, label = { Text(p.name) },
                            leadingIcon = { if (p.active) Icon(Icons.Default.Check, null, Modifier.size(18.dp)) else PlayerDot(p.color) }) }
                    }
                }
                val entries = history.selectedEntries()
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.readFailed -> Notice(R.string.read_failed, action = vm::retry, actionLabel = R.string.retry)
                    profiles -> Profiles(history, busy, vm)
                    history.players.isEmpty() -> Welcome(busy, { profiles = true }, vm::sample)
                    history.activePlayers.isEmpty() && !mapVisible -> Notice(R.string.select_players)
                    roundId != null -> RoundDetail(history, entries.find { it.roundId == roundId && it.playerId == roundPlayerId })
                    courseId != null -> CourseDetail(history, courseId!!) { roundId = it.roundId; roundPlayerId = it.playerId }
                    tab == 4 -> mapScreen(history) { courseId = it }
                    tab == 3 -> Statistics(history)
                    entries.isEmpty() -> Notice(R.string.no_rounds, R.string.no_rounds_body, action = { profiles = true }, actionLabel = R.string.profiles)
                    tab == 0 -> Courses(history) { courseId = it }
                    tab == 1 -> RoundList(history, entries) { roundId = it.roundId; roundPlayerId = it.playerId }
                    tab == 2 -> Aces(history) { roundId = it.roundId; roundPlayerId = it.playerId }
                    else -> Statistics(history)
                }
            }
        }
    }
}

@Composable private fun PlayerDot(index: Int) { Box(Modifier.size(12.dp).background(playerColors[Math.floorMod(index, playerColors.size)], CircleShape)) }
@Composable private fun Title(text: String) { Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
@Composable private fun Muted(text: String) { Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable private fun Notice(title: Int, body: Int? = null, action: (() -> Unit)? = null, actionLabel: Int = R.string.retry) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title(stringResource(title)); body?.let { Muted(stringResource(it)) }
        action?.let { Button(onClick = it) { Text(stringResource(actionLabel)) } }
    }
}
@Composable private fun Welcome(busy: Boolean, profiles: () -> Unit, sample: () -> Unit) {
    LazyColumn(contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Icon(Icons.Default.Explore, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary) }
        item { Title(stringResource(R.string.welcome)) }
        item { Text(stringResource(R.string.tagline), style = MaterialTheme.typography.titleMedium) }
        item { Muted(stringResource(R.string.welcome_body)) }
        item { Button(onClick = profiles, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_player)) } }
        item { OutlinedButton(onClick = sample, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.load_sample)) } }
    }
}
@Composable private fun Courses(history: History, open: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var orderIndex by rememberSaveable { mutableIntStateOf(CourseOrder.LAST_PLAYED.ordinal) }
    var expanded by remember { mutableStateOf(false) }
    val orderNames = listOf(R.string.order_name, R.string.order_rounds, R.string.order_last, R.string.order_first)
    val visits = history.courseVisits(query, CourseOrder.entries[orderIndex])
    LazyColumn(contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Title(stringResource(R.string.courses)) }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.search_courses)) }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }) }
        item { Box {
            OutlinedButton(onClick = { expanded = true }) { Icon(Icons.AutoMirrored.Filled.Sort, null); Spacer(Modifier.width(8.dp)); Text(stringResource(orderNames[orderIndex])) }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) { orderNames.forEachIndexed { i, label ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { orderIndex = i; expanded = false })
            } }
        } }
        if (visits.isEmpty()) item { Muted(stringResource(R.string.no_matches)) }
        items(visits, key = { it.course.id }) { visit -> Card(onClick = { open(visit.course.id) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(visit.course.name, style = MaterialTheme.typography.titleLarge)
                Muted(location(visit.course))
                Text(stringResource(R.string.round_count, visit.entries.size), color = MaterialTheme.colorScheme.primary)
                Muted(stringResource(R.string.last_played, date(visit.last)))
                visit.entries.groupBy { it.playerId }.forEach { (id, rounds) ->
                    Text("${history.players.first { it.id == id }.name} · ${rounds.size}", style = MaterialTheme.typography.bodySmall)
                }
            }
        } }
    }
}
@Composable private fun location(course: Course): String = listOfNotNull(course.city, course.country).joinToString(" · ").ifEmpty { stringResource(R.string.unknown_location) }
@Composable private fun RoundCard(history: History, entry: RoundEntry, open: () -> Unit) {
    Card(onClick = open, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(history.courses.find { it.id == entry.courseId }?.name ?: stringResource(R.string.unknown_course), style = MaterialTheme.typography.titleMedium)
            Text(history.players.first { it.id == entry.playerId }.name)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Muted(date(entry.date)); Text(result(entry), fontWeight = FontWeight.Bold) }
            entry.layout?.let { Muted(it) }
            if (entry.status == "METADATA_ONLY") Muted(stringResource(R.string.historical_scorecard_missing))
            if (entry.status == "DNF") Muted(stringResource(R.string.dnf))
            if (entry.status == "IN_PROGRESS") Muted(stringResource(R.string.round_in_progress))
        }
    }
}
@Composable private fun result(entry: RoundEntry): String {
    val dash = stringResource(R.string.missing_value)
    val relative = entry.relative?.let { if (it > 0) "+$it" else "$it" } ?: dash
    return stringResource(R.string.result, entry.total?.toString() ?: dash, relative)
}
@Composable private fun RoundList(history: History, entries: List<RoundEntry>, open: (RoundEntry) -> Unit) {
    LazyColumn(contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Title(stringResource(R.string.rounds)) }
        items(entries, key = { "${it.roundId}/${it.playerId}" }) { RoundCard(history, it) { open(it) } }
    }
}
@Composable private fun CourseDetail(history: History, id: String, open: (RoundEntry) -> Unit) {
    val visit = history.courseVisits().find { it.course.id == id }
    if (visit == null) { Notice(R.string.not_available); return }
    LazyColumn(contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Title(visit.course.name) }
        item { Muted(location(visit.course)) }
        item { Text(stringResource(R.string.round_count, visit.entries.size)) }
        item { Muted(stringResource(R.string.first_played, date(visit.first))); Muted(stringResource(R.string.last_played, date(visit.last))) }
        item { val c = visit.course; Muted(if (c.latitude != null && c.longitude != null) stringResource(R.string.coordinates, c.latitude, c.longitude) else stringResource(R.string.no_coordinates)) }
        history.activePlayers.forEach { player ->
            val entries = visit.entries.filter { it.playerId == player.id }
            if (entries.isNotEmpty()) {
                item(key = player.id) { Text(player.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)); Muted(stringResource(R.string.round_count, entries.size)) }
                items(entries, key = { "${it.roundId}/${it.playerId}" }) { RoundCard(history, it) { open(it) } }
            }
        }
    }
}
@Composable private fun RoundDetail(history: History, entry: RoundEntry?) {
    if (entry == null) { Notice(R.string.not_available); return }
    LazyColumn(contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Title(history.courses.find { it.id == entry.courseId }?.name ?: stringResource(R.string.unknown_course)) }
        item { Text(history.players.first { it.id == entry.playerId }.name, style = MaterialTheme.typography.titleLarge); Muted(date(entry.date)) }
        item { Text(result(entry), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary) }
        item { entry.layout?.let { Muted(stringResource(R.string.layout, it)) }; entry.tee?.let { Muted(stringResource(R.string.tee, it)) } }
        item { Muted(stringResource(if (entry.source == "sample") R.string.sample_round_id else R.string.round_id, entry.externalId)) }
        if (entry.status == "METADATA_ONLY") item { Muted(stringResource(R.string.historical_scorecard_missing)) }
        if (!entry.completeHoles) item { Muted(pluralStringResource(R.plurals.missing_holes, 1, 1)) }
        if (entry.status == "DNF") item { Muted(stringResource(R.string.dnf)) }
        if (entry.holes.isEmpty()) item { Muted(stringResource(R.string.no_holes)) }
        else {
            item { Row(Modifier.fillMaxWidth()) { Text(stringResource(R.string.hole), Modifier.weight(1f)); Text(stringResource(R.string.par), Modifier.weight(1f)); Text(stringResource(R.string.score), Modifier.weight(1f)) } }
            items(entry.holes, key = { it.ordinal }) { hole ->
                val ace = hole.score == 1 && entry.individual
                Row(Modifier.fillMaxWidth().background(if (ace) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent).padding(vertical = 12.dp, horizontal = 4.dp)) {
                    Text(hole.label, Modifier.weight(1f)); Text(hole.par?.toString() ?: stringResource(R.string.missing_value), Modifier.weight(1f))
                    Text(if (ace) "1 · ${stringResource(R.string.ace)}" else hole.score?.toString() ?: stringResource(R.string.missing_value), Modifier.weight(1f), fontWeight = if (ace) FontWeight.Bold else FontWeight.Normal)
                }
                HorizontalDivider()
            }
        }
    }
}
@Composable private fun Aces(history: History, open: (RoundEntry) -> Unit) {
    val entries = history.selectedEntries(); val aces = aces(entries)
    LazyColumn(contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Title(stringResource(R.string.ace_count, aces.size)) }
        item { Muted(stringResource(R.string.coverage_help)) }
        history.activePlayers.forEach { p -> item { Text("${p.name} · ${aces.count { it.entry.playerId == p.id }}") } }
        if (entries.any { !it.completeHoles }) item { val count = entries.count { !it.completeHoles }; Muted(pluralStringResource(R.plurals.missing_holes, count, count)) }
        if (aces.isEmpty()) item { Muted(stringResource(R.string.no_aces)) }
        items(aces, key = { "${it.entry.roundId}/${it.entry.playerId}/${it.hole.ordinal}" }) { ace ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.ace_hole, ace.hole.label, ace.hole.par?.toString() ?: stringResource(R.string.missing_value)), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                RoundCard(history, ace.entry) { open(ace.entry) }
            }
        }
    }
}
@Composable private fun Statistics(history: History) {
    val entries = history.selectedEntries()
    val coverage = history.coverage()
    val lastYear = history.entriesLastYear()
    val today = LocalDate.now()
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "last-year") { StatsCard(stringResource(R.string.last_year), history.stats(lastYear), history.physicalCourseCount(lastYear),
            period = "${date(today.minusYears(1).toString())} – ${date(today.toString())}", tag = "stats-year") }
        item(key = "all-history") { StatsCard(stringResource(R.string.all_history), history.stats(), history.physicalCourseCount(), tag = "stats-all") }
        if (history.activePlayers.size > 1) item(key = "players") {
            StatsDisclosure(stringResource(R.string.stats_by_player)) {
                history.activePlayers.forEach { player -> key(player.id) {
                    val own = entries.filter { it.playerId == player.id }
                    StatsCard(player.name, history.stats(own), history.physicalCourseCount(own)) {
                        Muted(stringResource(R.string.metrix_list_count, history.coverage(setOf(player.id)).listedEvents))
                    }
                } }
            }
        }
        item(key = "coverage") { StatsDisclosure(stringResource(R.string.metrix_coverage)) {
            Text(stringResource(R.string.metrix_list_count, coverage.listedEvents))
            Text(stringResource(R.string.metrix_blocked_count, coverage.blockedCards))
            Text(stringResource(R.string.map_history_limit, entries.count { it.status == "METADATA_ONLY" }, history.unresolvedHistoricalEvents()))
            if (coverage.otherUnavailable > 0) Text(stringResource(R.string.metrix_other_count, coverage.otherUnavailable))
            if (coverage.pending > 0) Text(stringResource(R.string.metrix_pending_count, coverage.pending))
            Muted(stringResource(R.string.event_list_help))
            if (coverage.blockedCards > 0 || history.activePlayers.any { it.syncError == "HISTORY_LIMIT" })
                Muted(stringResource(R.string.stats_history_limit))
        } }
        item(key = "explanation") { StatsDisclosure(stringResource(R.string.stats_calculation)) {
            Muted(stringResource(R.string.round_count_help))
            Muted(stringResource(R.string.coverage_help))
            Muted(stringResource(R.string.physical_course_help))
            if (history.players.any { it.sample && it.active }) Muted(stringResource(R.string.sample_banner))
        } }
    }
}

@Composable private fun StatsDisclosure(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { StatsDetails(title, title, content) }
}

@Composable private fun StatsDetails(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(if (expanded) R.string.stats_expanded else R.string.stats_collapsed)
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().semantics {
        contentDescription = description; stateDescription = state
    }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
    }
    if (expanded) Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable private fun StatsCard(title: String, stats: HistoryStats, physicalCourses: Int,
    period: String? = null, tag: String = "stats-player", extraDetails: @Composable ColumnScope.() -> Unit = {}) {
    Card(Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatNumber(stats.rounds, stringResource(R.string.stat_rounds), Modifier.weight(1f))
                StatNumber(physicalCourses, stringResource(R.string.stat_courses), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatNumber(stats.courses, stringResource(R.string.stat_layouts), Modifier.weight(1f))
                StatNumber(stats.aces, stringResource(R.string.stat_aces), Modifier.weight(1f), ace = true)
            }
            val range = period ?: stats.first?.let { first -> "${date(first)} – ${date(stats.last ?: first)}" }
            range?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        StatsDetails(stringResource(R.string.stats_details), stringResource(R.string.stats_details_for, title)) {
            Muted(stringResource(R.string.event_count, stats.events))
            stats.first?.let { Muted(stringResource(R.string.first_played, date(it))) }
            stats.last?.let { Muted(stringResource(R.string.last_played, date(it))) }
            if (stats.missingHoles > 0) Muted(pluralStringResource(R.plurals.missing_holes, stats.missingHoles, stats.missingHoles))
            if (stats.missingCourses > 0) Muted(pluralStringResource(R.plurals.missing_courses, stats.missingCourses, stats.missingCourses))
            extraDetails()
        }
    }
}

@Composable private fun StatNumber(value: Int, label: String, modifier: Modifier, ace: Boolean = false) {
    Column(modifier.semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (ace) Icon(Icons.Default.StarOutline, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun Profiles(history: History, busy: Boolean, vm: HistoryViewModel) {
    var add by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Player?>(null) }
    var removeSample by remember { mutableStateOf(false) }
    var initialProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var initialId by rememberSaveable { mutableStateOf("") }
    var initialName by rememberSaveable { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    if (add) {
        BackHandler { add = false }
        AddPlayerForm(initialId, initialName, busy, onDismiss = { add = false }, onSave = { id, name, code -> vm.add(id, name, code, initialProfileId) { add = false } })
        return
    }
    LazyColumn(contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Title(stringResource(R.string.profiles)); Muted(stringResource(R.string.version, BuildConfig.VERSION_NAME)) }
        item { Muted(stringResource(R.string.metrix_not_connected)) }
        item { Button(onClick = { initialProfileId = null; initialId = ""; initialName = ""; add = true }, enabled = !busy) { Icon(Icons.Default.Add, null); Text(stringResource(R.string.add_player)) } }
        items(history.players, key = { it.id }) { p -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { PlayerDot(p.color); Spacer(Modifier.width(8.dp)); Text(p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); IconButton(onClick = { deleting = p }, enabled = !busy) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.delete_player, p.name)) } }
                Muted(when {
                    p.sample -> stringResource(R.string.sample_profile)
                    p.metrixId != null -> stringResource(R.string.profile_id, p.metrixId)
                    else -> stringResource(R.string.no_player_id)
                })
                if (!p.sample) {
                    Muted(stringResource(if (p.hasIntegrationCode) R.string.code_present else R.string.code_absent))
                    TextButton(onClick = { initialProfileId = p.id; initialId = p.metrixId.orEmpty(); initialName = p.name; add = true }, enabled = !busy) {
                        Text(stringResource(R.string.connect_code))
                    }
                    p.metrixId?.let { id -> TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discgolfmetrix.com/player/$id")))
                    }) { Text(stringResource(R.string.open_profile)) } }
                }
                if (!p.sample) p.syncError?.let { Muted(stringResource(syncErrorText(it))) }
                if (!p.sample && p.syncError != "HISTORY_LIMIT" && history.coverage(setOf(p.id)).blockedCards > 0)
                    Muted(stringResource(R.string.sync_history_limit))
                if (!p.sample && p.syncStatus == "SUCCESS") Muted(stringResource(R.string.sync_available))
                Muted(p.lastSyncAt?.let { stringResource(R.string.last_updated, updated(it)) } ?: stringResource(R.string.never_synced))
            }
        } }
        item { HorizontalDivider(); Spacer(Modifier.height(12.dp)); Muted(stringResource(R.string.sample_description)) }
        item { OutlinedButton(onClick = vm::sample, enabled = !busy) { Text(stringResource(R.string.load_sample)) } }
        if (history.players.any { it.sample }) item { TextButton(onClick = { removeSample = true }, enabled = !busy) { Text(stringResource(R.string.remove_sample)) } }
    }
    deleting?.let { p -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.delete_player, p.name)) },
        text = { Text(stringResource(R.string.delete_player_body, p.name)) },
        confirmButton = { TextButton(onClick = { vm.delete(p.id); deleting = null }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }) }
    if (removeSample) AlertDialog(onDismissRequest = { removeSample = false }, title = { Text(stringResource(R.string.remove_sample)) }, text = { Text(stringResource(R.string.remove_sample_body)) },
        confirmButton = { TextButton(onClick = { vm.removeSample(); removeSample = false }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { removeSample = false }) { Text(stringResource(R.string.cancel)) } })
}
@Composable private fun AddPlayerForm(initialId: String, initialName: String, busy: Boolean, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var id by rememberSaveable { mutableStateOf(initialId) }
    // Credentials must never enter the saved-instance-state Bundle or a preview fixture.
    var code by remember { mutableStateOf("") }
    val rawId = id.trim()
    val validId = rawId.matches(Regex("[0-9]{1,20}")) && rawId.any { it != '0' }
    val valid = name.trim().length <= 60 && (validId || (rawId.isEmpty() && code.isNotBlank())) && code.length <= 512 && code.none { it.isISOControl() }
    val defaultName = if (rawId.isEmpty()) stringResource(R.string.default_player) else stringResource(R.string.default_player_id, rawId)
    LazyColumn(modifier = Modifier.imePadding(), contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Title(stringResource(R.string.add_player)) }
        item { Muted(stringResource(R.string.profile_hint)) }
        item { OutlinedTextField(name, { name = it.take(60) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text(stringResource(R.string.display_name)) }, supportingText = { Text(stringResource(R.string.optional_name)) }, singleLine = true) }
        item { OutlinedTextField(id, { id = it.take(20) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text(stringResource(R.string.metrix_id)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
        item { OutlinedTextField(code, { code = it.take(512) }, Modifier.fillMaxWidth(), enabled = !busy,
            label = { Text(stringResource(R.string.integration_code)) }, supportingText = { Text(stringResource(R.string.code_hint)) },
            visualTransformation = PasswordVisualTransformation(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)) }
        item { Muted(stringResource(R.string.code_privacy)) }
        item { Button(onClick = { onSave(rawId, name.trim().ifEmpty { defaultName }, code) }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (busy) R.string.saving else R.string.save)) } }
        item { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } }
    }
}

private fun syncErrorText(code: String): Int = when (code) {
    "COURSE" -> R.string.sync_course_failed
    "NEEDS_ID" -> R.string.sync_needs_id
    "NEEDS_CODE" -> R.string.sync_needs_code
    "INVALID_CODE" -> R.string.invalid_code
    "HISTORY_LIMIT" -> R.string.sync_history_limit
    "ACCESS" -> R.string.sync_access
    "NO_RESULTS" -> R.string.sync_no_results
    "UNSUPPORTED" -> R.string.sync_unsupported
    "CONNECTION" -> R.string.sync_connection
    else -> R.string.sync_response
}
