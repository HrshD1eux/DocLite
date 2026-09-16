package com.HrshD1eux.DocLite.ui.screens.filemanager

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.HrshD1eux.DocLite.models.DocumentFile
import com.HrshD1eux.DocLite.ui.components.FileCardItem
import com.HrshD1eux.DocLite.ui.components.PasswordPromptDialog
import com.HrshD1eux.DocLite.ui.components.SearchBarComponent
import com.HrshD1eux.DocLite.ui.components.SetPasswordDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    viewModel: FileManagerViewModel,
    onOpenFile: (DocumentFile) -> Unit,
    onAnalyzeStatement: (DocumentFile) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showRenameDialog by remember { mutableStateOf<DocumentFile?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    var selectedFileForUnlock by remember { mutableStateOf<DocumentFile?>(null) }
    var unlockErrorMessage by remember { mutableStateOf<String?>(null) }
    var selectedFileForPasswordSet by remember { mutableStateOf<DocumentFile?>(null) }

    val successState = state as? FileManagerUiState.Success
    val isSelectionMode = successState != null && successState.selectedFileIds.isNotEmpty()
    val selectedCount = successState?.selectedFileIds?.size ?: 0

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    fun handleFileClick(file: DocumentFile) {
        val currentSuccess = state as? FileManagerUiState.Success
        if (currentSuccess != null && currentSuccess.selectedFileIds.isNotEmpty()) {
            viewModel.toggleSelectFile(file)
            return
        }
        if (file.isPasswordProtected) {
            unlockErrorMessage = null
            selectedFileForUnlock = file
        } else {
            onOpenFile(file)
        }
    }

    fun shareMultipleFiles(files: List<DocumentFile>) {
        if (files.isEmpty()) return
        val uris = ArrayList<android.net.Uri>()
        for (file in files) {
            val uri = android.net.Uri.parse(file.uriString)
            val shareUri = if (uri.scheme == "file" || uri.scheme == null) {
                val path = uri.path ?: file.path
                try {
                    androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", java.io.File(path))
                } catch (t: Throwable) {
                    uri
                }
            } else {
                uri
            }
            uris.add(shareUri)
        }

        if (uris.size == 1) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uris[0])
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
        } else if (uris.size > 1) {
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Documents"))
        }
    }

    LaunchedEffect((state as? FileManagerUiState.Success)?.actionMessage) {
        val msg = (state as? FileManagerUiState.Success)?.actionMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (selectedFileForUnlock != null) {
        PasswordPromptDialog(
            file = selectedFileForUnlock!!,
            onDismiss = { selectedFileForUnlock = null },
            onUnlock = { password ->
                val targetFile = selectedFileForUnlock!!
                viewModel.verifyPassword(targetFile, password) { isValid ->
                    if (isValid) {
                        selectedFileForUnlock = null
                        onOpenFile(targetFile)
                    } else {
                        unlockErrorMessage = "Incorrect password. Please try again."
                    }
                }
            },
            errorMessage = unlockErrorMessage
        )
    }

    fun shareFile(file: DocumentFile) {
        val uri = android.net.Uri.parse(file.uriString)
        val shareUri = if (uri.scheme == "file" || uri.scheme == null) {
            val path = uri.path ?: file.path
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", java.io.File(path))
        } else {
            uri
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
    }

    if (selectedFileForPasswordSet != null) {
        SetPasswordDialog(
            file = selectedFileForPasswordSet!!,
            onDismiss = { selectedFileForPasswordSet = null },
            onSetPassword = { pass ->
                viewModel.setFilePassword(selectedFileForPasswordSet!!, pass)
                selectedFileForPasswordSet = null
            },
            onRemovePassword = {
                viewModel.removeFilePassword(selectedFileForPasswordSet!!)
                selectedFileForPasswordSet = null
            }
        )
    }


    if (showRenameDialog != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New File Name") },
                    modifier = Modifier.fillMaxWidth().testTag("rename_input"),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val file = showRenameDialog
                        if (file != null && renameInput.isNotBlank()) {
                            viewModel.renameFile(file, renameInput.trim() + "." + file.format.extensions.first())
                        }
                        showRenameDialog = null
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBatchDeleteDialog) {
        val count = selectedCount
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("Delete $count Files") },
            text = { Text("Are you sure you want to permanently delete $count selected file(s)? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatchDeleteDialog = false
                        viewModel.deleteSelectedFiles()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "$selectedCount selected",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear Selection",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    },
                    actions = {
                        val currentFiles = (state as? FileManagerUiState.Success)?.files ?: emptyList()
                        val isAllSelected = selectedCount == currentFiles.size && currentFiles.isNotEmpty()
                        IconButton(onClick = {
                            if (isAllSelected) viewModel.clearSelection() else viewModel.selectAll()
                        }) {
                            Icon(
                                imageVector = if (isAllSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = if (isAllSelected) "Deselect All" else "Select All",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        IconButton(onClick = {
                            val selectedFiles = viewModel.getSelectedFiles()
                            if (selectedFiles.isNotEmpty()) {
                                shareMultipleFiles(selectedFiles)
                            }
                        }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share Selected Files",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        IconButton(onClick = { showBatchDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Selected Files",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = "File Manager",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF191C1D)
                            )
                        )
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort Files", tint = Color(0xFF40484B))
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sort by Date") },
                                    onClick = {
                                        viewModel.setSortOption(SortOption.DATE)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort by Name") },
                                    onClick = {
                                        viewModel.setSortOption(SortOption.NAME)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort by Size") },
                                    onClick = {
                                        viewModel.setSortOption(SortOption.SIZE)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort by Type") },
                                    onClick = {
                                        viewModel.setSortOption(SortOption.TYPE)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFFBFDFD),
        modifier = modifier
    ) { innerPadding ->
        when (val uiState = state) {
            is FileManagerUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is FileManagerUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SearchBarComponent(
                            query = uiState.searchQuery,
                            onQueryChange = viewModel::updateSearchQuery,
                            placeholderText = "Search local files..."
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Documents (${uiState.files.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            AssistChip(
                                onClick = { showSortMenu = true },
                                label = { Text("Sort: ${uiState.currentSort.name}") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort") }
                            )
                        }
                    }

                    if (uiState.files.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No documents found in storage.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(uiState.files, key = { it.id }) { file ->
                            FileCardItem(
                                file = file,
                                onClick = { handleFileClick(file) },
                                onFavoriteToggle = { viewModel.toggleFavorite(file) },
                                onRenameClick = {
                                    renameInput = file.name.substringBeforeLast(".")
                                    showRenameDialog = file
                                },
                                onDeleteClick = { viewModel.deleteFile(file) },
                                onShareClick = { shareFile(file) },
                                onProtectClick = { selectedFileForPasswordSet = file },
                                onAnalyzeStatement = { onAnalyzeStatement(file) },
                                isSelectionMode = isSelectionMode,
                                isSelected = uiState.selectedFileIds.contains(file.id),
                                onSelectToggle = { viewModel.toggleSelectFile(file) },
                                onLongClick = { viewModel.toggleSelectFile(file) }
                            )
                        }
                    }
                }
            }
        }
    }
}

