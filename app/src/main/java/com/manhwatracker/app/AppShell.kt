package com.manhwatracker.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

private const val SECTION_MANHWA = "MANHWA"
private const val SECTION_STUDY = "STUDY"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel = viewModel()) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var section by rememberSaveable { mutableStateOf(SECTION_MANHWA) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showAddManhwa by rememberSaveable { mutableStateOf(false) }
    var showAddWord by rememberSaveable { mutableStateOf(false) }

    val manhwas by vm.items.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }

    val currentId = selectedId

    // El último BackHandler habilitado es el que manda.
    BackHandler(enabled = currentId == null && section == SECTION_STUDY) { section = SECTION_MANHWA }
    BackHandler(enabled = currentId != null) { selectedId = null }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentId == null,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "Manhwa Tracker",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Mis manhwas") },
                    selected = section == SECTION_MANHWA,
                    onClick = {
                        section = SECTION_MANHWA
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Estudio") },
                    selected = section == SECTION_STUDY,
                    onClick = {
                        section = SECTION_STUDY
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when {
                                currentId != null -> "Detalle"
                                section == SECTION_STUDY -> "Estudio"
                                else -> "Mis manhwas"
                            }
                        )
                    },
                    navigationIcon = {
                        if (currentId != null) {
                            IconButton(onClick = { selectedId = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menú")
                            }
                        }
                    },
                    actions = {
                        if (currentId == null && section == SECTION_MANHWA) {
                            TextButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                                Text("Importar backup")
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (currentId == null) {
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
                    currentId != null -> DetailScreen(vm, currentId, onClose = { selectedId = null })
                    section == SECTION_STUDY -> StudyScreen(vm)
                    else -> ListScreen(vm, onOpen = { selectedId = it })
                }
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
