package com.manhwatracker.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

private const val SECTION_MANHWA = "MANHWA"
private const val SECTION_STUDY = "STUDY"
private const val SECTION_REVIEW = "REVIEW"
private const val SECTION_STATS = "STATS"
private const val SECTION_RATINGS = "RATINGS"
private const val SECTION_SYNC = "SYNC"
private const val SECTION_MORE = "MORE"
private const val SECTION_LOOK = "LOOK"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel = viewModel()) {
    var section by rememberSaveable { mutableStateOf(SECTION_MANHWA) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showAddManhwa by rememberSaveable { mutableStateOf(false) }
    var showAddWord by rememberSaveable { mutableStateOf(false) }

    // Repaso de un solo manhwa (se abre desde su detalle): su clave, su nombre y a cuál detalle volver.
    var reviewKey by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewReturnId by rememberSaveable { mutableStateOf<Long?>(null) }

    val manhwas by vm.items.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    // Aviso "Deshacer" cuando se suma un capítulo con el botón +.
    val undo by vm.undo.collectAsState()
    LaunchedEffect(undo) {
        val info = undo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = info.text,
            actionLabel = "Deshacer",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) vm.undoIncrement(info.previous)
        vm.clearUndo(info)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) vm.exportOwnBackup(uri)
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restoreOwnBackup(uri)
    }
    val currentId = selectedId

    /** Abre el repaso con todas las palabras (sin filtro). */
    fun openReview() {
        reviewKey = null
        reviewLabel = null
        reviewReturnId = null
        section = SECTION_REVIEW
    }

    /** Sale del repaso: vuelve al detalle del manhwa si venía de ahí, o a Estudio. */
    fun leaveReview() {
        val back = reviewReturnId
        reviewKey = null
        reviewLabel = null
        reviewReturnId = null
        if (back != null) {
            section = SECTION_MANHWA
            selectedId = back
        } else {
            section = SECTION_STUDY
        }
    }

    val subScreen = section == SECTION_RATINGS || section == SECTION_SYNC || section == SECTION_LOOK

    // El último BackHandler habilitado es el que manda.
    BackHandler(enabled = currentId == null && (section == SECTION_STUDY || section == SECTION_STATS || section == SECTION_MORE)) {
        section = SECTION_MANHWA
    }
    BackHandler(enabled = currentId == null && subScreen) { section = SECTION_MORE }
    BackHandler(enabled = currentId == null && section == SECTION_REVIEW) { leaveReview() }
    BackHandler(enabled = currentId != null) { selectedId = null }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            when {
                                currentId != null -> "Detalle"
                                section == SECTION_STUDY -> "Estudio"
                                section == SECTION_REVIEW -> "Repaso"
                                section == SECTION_STATS -> "Estadísticas"
                                section == SECTION_RATINGS -> "Calificaciones"
                                section == SECTION_SYNC -> "Sincronización"
                                section == SECTION_LOOK -> "Apariencia"
                                section == SECTION_MORE -> "Más"
                                else -> "Mis manhwas"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        when {
                            currentId != null -> IconButton(onClick = { selectedId = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                            }
                            section == SECTION_REVIEW -> IconButton(onClick = { leaveReview() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                            }
                            subScreen -> IconButton(onClick = { section = SECTION_MORE }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                            }
                        }
                    }
                )
                Box(Modifier.fillMaxWidth().height(3.dp).background(brandBrush()))
            }
        },
        bottomBar = {
            if (currentId == null && section != SECTION_REVIEW) {
                NavigationBar {
                    NavigationBarItem(
                        selected = section == SECTION_MANHWA,
                        onClick = { section = SECTION_MANHWA },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Manhwas") }
                    )
                    NavigationBarItem(
                        selected = section == SECTION_STUDY,
                        onClick = { section = SECTION_STUDY },
                        icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        label = { Text("Estudio") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { openReview() },
                        icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        label = { Text("Repaso") }
                    )
                    NavigationBarItem(
                        selected = section == SECTION_STATS,
                        onClick = { section = SECTION_STATS },
                        icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                        label = { Text("Progreso") }
                    )
                    NavigationBarItem(
                        selected = section == SECTION_MORE || subScreen,
                        onClick = { section = SECTION_MORE },
                        icon = { Icon(Icons.Filled.Menu, contentDescription = null) },
                        label = { Text("Más") }
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentId == null && (section == SECTION_MANHWA || section == SECTION_STUDY)) {
                FloatingActionButton(
                    onClick = {
                        if (section == SECTION_STUDY) showAddWord = true else showAddManhwa = true
                    }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Agregar")
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                currentId != null -> DetailScreen(
                    vm,
                    currentId,
                    onClose = { selectedId = null },
                    onReviewWords = { key, title ->
                        reviewKey = key
                        reviewLabel = title
                        reviewReturnId = currentId
                        selectedId = null
                        section = SECTION_REVIEW
                    }
                )
                section == SECTION_REVIEW -> ReviewScreen(
                    vm,
                    titleKey = reviewKey,
                    filterLabel = reviewLabel,
                    onExit = { leaveReview() }
                )
                section == SECTION_STATS -> StatsScreen(vm)
                section == SECTION_RATINGS -> RatingsScreen(vm, onOpen = { selectedId = it })
                section == SECTION_SYNC -> SyncScreen(vm)
                section == SECTION_LOOK -> AppearanceScreen()
                section == SECTION_MORE -> MoreScreen(
                    onRatings = { section = SECTION_RATINGS },
                    onSync = { section = SECTION_SYNC },
                    onLook = { section = SECTION_LOOK },
                    onImport = { picker.launch(arrayOf("*/*")) },
                    onExport = { exportPicker.launch("manhwa-tracker-${LocalDate.now()}.json") },
                    onRestore = { restorePicker.launch(arrayOf("*/*")) }
                )
                section == SECTION_STUDY -> StudyScreen(vm, onReview = { openReview() })
                else -> ListScreen(vm, onOpen = { selectedId = it })
            }
        }
    }

    if (showAddManhwa) {
        AddDialog(
            onDismiss = { showAddManhwa = false },
            onConfirm = { title, chapter, total ->
                vm.add(title, chapter, total)
                showAddManhwa = false
            }
        )
    }

    if (showAddWord) {
        AddWordDialog(
            manhwas = manhwas,
            onDismiss = { showAddWord = false },
            onConfirm = { text, manhwa, chapter, note ->
                vm.addWord(text, manhwa, chapter, note)
                showAddWord = false
            }
        )
    }
}
