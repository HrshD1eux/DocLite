package com.HrshD1eux.DocLite.ui.screens.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.HrshD1eux.DocLite.models.DocumentFile
import com.HrshD1eux.DocLite.models.DocumentFormat
import com.HrshD1eux.DocLite.ui.components.CategoryChip
import com.HrshD1eux.DocLite.ui.components.CreateDocumentDialog
import com.HrshD1eux.DocLite.ui.components.FileCardItem
import com.HrshD1eux.DocLite.ui.components.PasswordPromptDialog
import com.HrshD1eux.DocLite.ui.components.SearchBarComponent
import com.HrshD1eux.DocLite.ui.components.SetPasswordDialog

import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenFile: (DocumentFile) -> Unit,
    onOpenUri: (Uri, DocumentFormat) -> Unit,
    onOpenBankStatementAnalyser: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    var selectedFileForUnlock by remember { mutableStateOf<DocumentFile?>(null) }
    var unlockErrorMessage by remember { mutableStateOf<String?>(null) }
    var selectedFileForPasswordSet by remember { mutableStateOf<DocumentFile?>(null) }
    var selectedFileForRename by remember { mutableStateOf<DocumentFile?>(null) }
    var renameInput by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshScan()
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:${context.packageName}")
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    context.startActivity(intent)
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            viewModel.refreshScan()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            val mimeType = context.contentResolver.getType(uri)
            val format = if (mimeType != null) {
                when {
                    mimeType.contains("pdf") -> DocumentFormat.PDF
                    mimeType.contains("word") || mimeType.contains("document") || mimeType.contains("text/plain") -> DocumentFormat.WORD
                    mimeType.contains("excel") || mimeType.contains("sheet") || mimeType.contains("csv") -> DocumentFormat.EXCEL
                    mimeType.contains("powerpoint") || mimeType.contains("presentation") -> DocumentFormat.POWERPOINT
                    mimeType.startsWith("image/") -> DocumentFormat.IMAGE
                    else -> DocumentFormat.WORD
                }
            } else {
                val ext = uri.path?.substringAfterLast('.', "") ?: ""
                DocumentFormat.fromExtension(ext)
            }
            onOpenUri(uri, format)
        }
    }

    fun handleFileClick(file: DocumentFile) {
        if (file.isPasswordProtected) {
            unlockErrorMessage = null
            selectedFileForUnlock = file
        } else {
            onOpenFile(file)
        }
    }

    fun shareFile(file: DocumentFile) {
        val uri = Uri.parse(file.uriString)
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

    if (showCreateDialog) {
        CreateDocumentDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, format, password ->
                showCreateDialog = false
                viewModel.createNewDocument(name, format, password) { doc ->
                    onOpenFile(doc)
                }
            }
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

    if (selectedFileForRename != null) {
        AlertDialog(
            onDismissRequest = { selectedFileForRename = null },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val file = selectedFileForRename!!
                        val name = renameInput.trim()
                        if (name.isNotEmpty()) {
                            viewModel.renameDocument(file, name + "." + file.format.extensions.first())
                        }
                        selectedFileForRename = null
                    }
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { selectedFileForRename = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = false; showCreateDialog = true },
                containerColor = Color(0xFF006874),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .size(64.dp)
                    .testTag("new_document_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create Document",
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        containerColor = Color(0xFFFBFDFD),
        modifier = modifier
    ) { innerPadding ->
        when (val uiState = state) {
            is HomeUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF006874))
                }
            }

            is HomeUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Header Bar
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DocLite",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 24.sp,
                                    color = Color(0xFF191C1D)
                                )
                            )

                            Surface(
                                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                shape = CircleShape,
                                color = Color(0xFFE1E3E4),
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("open_external_file_btn")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "Open Storage",
                                        tint = Color(0xFF40484B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Search Bar
                    item {
                        SearchBarComponent(
                            query = uiState.searchQuery,
                            onQueryChange = viewModel::onSearchQueryChange
                        )
                    }

                    // Bank Statement Analyser Feature Card
                    item {
                        Card(
                            onClick = onOpenBankStatementAnalyser,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bank_statement_analyser_card"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFE0F7FA)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF006874),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.AccountBalance,
                                            contentDescription = "Bank Statement Analyser",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Bank Statement Analyser",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF001F24)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Upload Excel (.xls/.xlsx) to decrypt & calculate total credits, debits & top 60 senders/recipients",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF004F58)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Open Analyser",
                                    tint = Color(0xFF006874)
                                )
                            }
                        }
                    }

                    // Category Selector
                    item {
                        Column {
                            Text(
                                text = "CATEGORIES",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp,
                                    color = Color(0xFF40484B)
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                item {
                                    CategoryChip(
                                        format = null,
                                        isSelected = uiState.selectedCategory == null,
                                        onClick = { viewModel.onCategorySelect(null) }
                                    )
                                }
                                items(DocumentFormat.entries) { format ->
                                    CategoryChip(
                                        format = format,
                                        isSelected = uiState.selectedCategory == format,
                                        onClick = { viewModel.onCategorySelect(format) }
                                    )
                                }
                            }
                        }
                    }

                    // Favorites Section
                    if (uiState.favoriteFiles.isNotEmpty() && uiState.selectedCategory == null && uiState.searchQuery.isBlank()) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Favorites",
                                    tint = Color(0xFF006874),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "FAVORITES",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        letterSpacing = 1.sp,
                                        color = Color(0xFF40484B)
                                    )
                                )
                            }
                        }

                        items(uiState.favoriteFiles, key = { "fav_${it.id}" }) { file ->
                            FileCardItem(
                                file = file,
                                onClick = { handleFileClick(file) },
                                onFavoriteToggle = { viewModel.toggleFavorite(file) },
                                onRenameClick = { 
                                    renameInput = file.name.substringBeforeLast(".")
                                    selectedFileForRename = file
                                },
                                onDeleteClick = { viewModel.deleteDocument(file) },
                                onShareClick = { shareFile(file) },
                                onProtectClick = { selectedFileForPasswordSet = file }
                            )
                        }
                    }

                    // Recent / Filtered Documents
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    uiState.selectedCategory != null -> "${uiState.selectedCategory.displayName.uppercase()} DOCUMENTS"
                                    uiState.searchQuery.isNotBlank() -> "SEARCH RESULTS"
                                    else -> "RECENT FILES"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp,
                                    color = Color(0xFF40484B)
                                )
                            )

                            if (uiState.selectedCategory != null || uiState.searchQuery.isNotBlank()) {
                                TextButton(onClick = {
                                    viewModel.onCategorySelect(null)
                                    viewModel.onSearchQueryChange("")
                                }) {
                                    Text(
                                        text = "Clear filter",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF006874)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.filteredFiles.isEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFEDF1F1)
                            ) {
                                Column(
                                    modifier = Modifier.padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.InsertDriveFile,
                                        contentDescription = "No documents",
                                        tint = Color(0xFF70787C),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No documents found",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFF191C1D)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap + to create a new document or open from storage.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF70787C)
                                    )
                                }
                            }
                        }
                    } else {
                        items(uiState.filteredFiles, key = { it.id }) { file ->
                            FileCardItem(
                                file = file,
                                onClick = { handleFileClick(file) },
                                onFavoriteToggle = { viewModel.toggleFavorite(file) },
                                onRenameClick = { 
                                    renameInput = file.name.substringBeforeLast(".")
                                    selectedFileForRename = file
                                },
                                onDeleteClick = { viewModel.deleteDocument(file) },
                                onShareClick = { shareFile(file) },
                                onProtectClick = { selectedFileForPasswordSet = file }
                            )
                        }
                    }
                }
            }
        }
    }
}

