package com.HrshD1eux.DocLite.ui.screens.filemanager

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.HrshD1eux.DocLite.database.entity.TrashedFileEntity
import com.HrshD1eux.DocLite.models.DocumentFile
import com.HrshD1eux.DocLite.models.DocumentFormat
import com.HrshD1eux.DocLite.ui.components.FileCardItem
import com.HrshD1eux.DocLite.ui.components.FileGridItem
import com.HrshD1eux.DocLite.ui.components.FileInfoDialog
import com.HrshD1eux.DocLite.ui.components.FormatIconBadge
import com.HrshD1eux.DocLite.ui.components.PasswordPromptDialog
import com.HrshD1eux.DocLite.ui.components.SearchBarComponent
import com.HrshD1eux.DocLite.ui.components.SetPasswordDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    viewModel: FileManagerViewModel,
    onOpenFile: (DocumentFile) -> Unit,
    onAnalyzeStatement: (DocumentFile) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val trashedFiles by viewModel.trashedFiles.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showRenameDialog by remember { mutableStateOf<DocumentFile?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<DocumentFile?>(null) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var infoFile by remember { mutableStateOf<DocumentFile?>(null) }
    var showTrashDialog by remember { mutableStateOf(false) }

    var selectedFileForUnlock by remember { mutableStateOf<DocumentFile?>(null) }
    var unlockErrorMessage by remember { mutableStateOf<String?>(null) }
    var selectedFileForPasswordSet by remember { mutableStateOf<DocumentFile?>(null) }

    val successState = state as? FileManagerUiState.Success
    val isSelectionMode = successState != null && successState.selectedFileIds.isNotEmpty()
    val selectedCount = successState?.selectedFileIds?.size ?: 0
    val isGridView = successState?.isGridView == true

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

    if (infoFile != null) {
        FileInfoDialog(
            file = infoFile!!,
            onDismiss = { infoFile = null }
        )
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

    // Single File Delete Confirmation Dialog
    if (fileToDelete != null) {
        val file = fileToDelete!!
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Move to Trash?") },
            text = {
                Text("Are you sure you want to delete '${file.name}'? It will remain safely in Trash for 30 days before being permanently deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = fileToDelete
                        fileToDelete = null
                        if (target != null) {
                            viewModel.deleteFile(target)
                        }
                    }
                ) {
                    Text("Move to Trash", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Batch Delete Confirmation Dialog
    if (showBatchDeleteDialog) {
        val count = selectedCount
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("Move $count Files to Trash?") },
            text = { Text("Are you sure you want to delete $count selected file(s)? They will remain safely in Trash for 30 days before being permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatchDeleteDialog = false
                        viewModel.deleteSelectedFiles()
                    }
                ) {
                    Text("Move to Trash", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Trash / Recycle Bin Modal Dialog
    if (showTrashDialog) {
        TrashBinDialog(
            trashedFiles = trashedFiles,
            onDismiss = { showTrashDialog = false },
            onRestore = { viewModel.restoreTrashedFile(it) },
            onDeletePermanently = { viewModel.permanentlyDeleteTrashedFile(it) },
            onEmptyTrash = { viewModel.emptyTrash() }
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
                        // View Toggle Button (List vs Grid)
                        IconButton(
                            onClick = viewModel::toggleViewMode,
                            modifier = Modifier.testTag("btn_toggle_view_mode")
                        ) {
                            Icon(
                                imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = if (isGridView) "Switch to List View" else "Switch to Grid View",
                                tint = Color(0xFF40484B)
                            )
                        }

                        // Trash / Recycle Bin Action with Count Badge
                        IconButton(
                            onClick = { showTrashDialog = true },
                            modifier = Modifier.testTag("btn_open_trash")
                        ) {
                            if (trashedFiles.isNotEmpty()) {
                                BadgedBox(
                                    badge = {
                                        Badge {
                                            Text(
                                                text = if (trashedFiles.size > 99) "99+" else "${trashedFiles.size}",
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Trash (${trashedFiles.size})",
                                        tint = Color(0xFF40484B)
                                    )
                                }
                            } else {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Trash",
                                    tint = Color(0xFF40484B)
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
                if (isGridView) {
                    // GRID VIEW MODE
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SearchBarComponent(
                                query = uiState.searchQuery,
                                onQueryChange = viewModel::updateSearchQuery,
                                placeholderText = "Search local files..."
                            )
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            FilesHeaderRow(
                                count = uiState.files.size,
                                currentSort = uiState.currentSort,
                                isAscending = uiState.isAscending,
                                showSortMenu = showSortMenu,
                                onShowSortMenuChange = { showSortMenu = it },
                                onSortSelect = {
                                    viewModel.setSortOption(it)
                                    showSortMenu = false
                                }
                            )
                        }

                        if (uiState.files.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                EmptyFilesNotice()
                            }
                        } else {
                            items(uiState.files, key = { it.id }) { file ->
                                FileGridItem(
                                    file = file,
                                    onClick = { handleFileClick(file) },
                                    onFavoriteToggle = { viewModel.toggleFavorite(file) },
                                    onRenameClick = {
                                        renameInput = file.name.substringBeforeLast(".")
                                        showRenameDialog = file
                                    },
                                    onDeleteClick = { fileToDelete = file },
                                    onShareClick = { shareFile(file) },
                                    onProtectClick = { selectedFileForPasswordSet = file },
                                    onInfoClick = { infoFile = file },
                                    onAnalyzeStatement = { onAnalyzeStatement(file) },
                                    isSelectionMode = isSelectionMode,
                                    isSelected = uiState.selectedFileIds.contains(file.id),
                                    onSelectToggle = { viewModel.toggleSelectFile(file) },
                                    onLongClick = { viewModel.toggleSelectFile(file) }
                                )
                            }
                        }
                    }
                } else {
                    // LIST VIEW MODE
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
                            FilesHeaderRow(
                                count = uiState.files.size,
                                currentSort = uiState.currentSort,
                                isAscending = uiState.isAscending,
                                showSortMenu = showSortMenu,
                                onShowSortMenuChange = { showSortMenu = it },
                                onSortSelect = {
                                    viewModel.setSortOption(it)
                                    showSortMenu = false
                                }
                            )
                        }

                        if (uiState.files.isEmpty()) {
                            item {
                                EmptyFilesNotice()
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
                                    onDeleteClick = { fileToDelete = file },
                                    onShareClick = { shareFile(file) },
                                    onProtectClick = { selectedFileForPasswordSet = file },
                                    onInfoClick = { infoFile = file },
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
}

@Composable
private fun FilesHeaderRow(
    count: Int,
    currentSort: SortOption,
    isAscending: Boolean,
    showSortMenu: Boolean,
    onShowSortMenuChange: (Boolean) -> Unit,
    onSortSelect: (SortOption) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Documents ($count)",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        Box {
            AssistChip(
                onClick = { onShowSortMenuChange(true) },
                label = { Text("Sort: ${currentSort.name} ${if (isAscending) "↑" else "↓"}") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort") }
            )

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { onShowSortMenuChange(false) }
            ) {
                DropdownMenuItem(
                    text = { Text("Sort by Date") },
                    onClick = { onSortSelect(SortOption.DATE) }
                )
                DropdownMenuItem(
                    text = { Text("Sort by Name") },
                    onClick = { onSortSelect(SortOption.NAME) }
                )
                DropdownMenuItem(
                    text = { Text("Sort by Size") },
                    onClick = { onSortSelect(SortOption.SIZE) }
                )
                DropdownMenuItem(
                    text = { Text("Sort by Type") },
                    onClick = { onSortSelect(SortOption.TYPE) }
                )
            }
        }
    }
}

@Composable
private fun EmptyFilesNotice() {
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

@Composable
private fun TrashBinDialog(
    trashedFiles: List<TrashedFileEntity>,
    onDismiss: () -> Unit,
    onRestore: (TrashedFileEntity) -> Unit,
    onDeletePermanently: (TrashedFileEntity) -> Unit,
    onEmptyTrash: () -> Unit
) {
    var showEmptyConfirm by remember { mutableStateOf(false) }

    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            title = { Text("Empty Trash?") },
            text = { Text("All ${trashedFiles.size} file(s) in Trash will be permanently deleted immediately. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEmptyConfirm = false
                        onEmptyTrash()
                    }
                ) {
                    Text("Empty Trash", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Trash (Recycle Bin)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Items remain for 30 days before purge",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (trashedFiles.isNotEmpty()) {
                        TextButton(
                            onClick = { showEmptyConfirm = true },
                            modifier = Modifier.testTag("btn_empty_trash")
                        ) {
                            Text("Empty", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                if (trashedFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Trash is empty",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(trashedFiles, key = { it.uriString }) { trashed ->
                            val daysLeft = ((trashed.expiryTimestamp - System.currentTimeMillis()) / (24L * 60 * 60 * 1000L)).coerceAtLeast(0)
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val format = DocumentFormat.entries.firstOrNull { it.name.equals(trashed.formatName, ignoreCase = true) }
                                        ?: DocumentFormat.fromExtension(trashed.name.substringAfterLast('.', ""))
                                    FormatIconBadge(format = format)

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = trashed.name,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "$daysLeft days left • ${formatSize(trashed.sizeBytes)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = { onRestore(trashed) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Restore,
                                            contentDescription = "Restore",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeletePermanently(trashed) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteForever,
                                            contentDescription = "Delete Forever",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
