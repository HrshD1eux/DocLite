package com.HrshD1eux.DocLite.ui.screens.excel

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.HrshD1eux.DocLite.models.Sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelEditorScreen(
    viewModel: ExcelViewModel,
    fileUri: Uri,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(fileUri) {
        viewModel.loadSpreadsheet(fileUri)
    }

    LaunchedEffect((state as? ExcelUiState.Success)?.saveStatus) {
        val status = (state as? ExcelUiState.Success)?.saveStatus
        if (status != null) {
            snackbarHostState.showSnackbar(status)
        }
    }

    val passwordState = state as? ExcelUiState.PasswordRequired
    if (passwordState != null) {
        val dummyFile = remember(passwordState.uri) {
            com.HrshD1eux.DocLite.models.DocumentFile(
                id = passwordState.uri.toString(),
                name = passwordState.uri.lastPathSegment ?: "Protected Spreadsheet",
                path = passwordState.uri.path ?: "",
                uriString = passwordState.uri.toString(),
                sizeBytes = 0,
                lastModified = System.currentTimeMillis(),
                format = com.HrshD1eux.DocLite.models.DocumentFormat.EXCEL,
                isPasswordProtected = true
            )
        }
        com.HrshD1eux.DocLite.ui.components.PasswordPromptDialog(
            file = dummyFile,
            onDismiss = onBack,
            onUnlock = { password ->
                viewModel.loadSpreadsheet(passwordState.uri, password)
            },
            errorMessage = passwordState.errorMessage
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (state as? ExcelUiState.Success)?.document?.title ?: "Excel Editor"
                    Text(
                        text = title,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val successState = state as? ExcelUiState.Success
                    if (successState != null) {
                        val canSave = !successState.document.hasUnrecognizedElements
                        IconButton(
                            onClick = viewModel::saveSpreadsheet,
                            enabled = canSave
                        ) {
                            Icon(
                                Icons.Default.Save, 
                                contentDescription = "Save Spreadsheet",
                                tint = if (canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        when (val uiState = state) {
            is ExcelUiState.Loading, is ExcelUiState.PasswordRequired -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is ExcelUiState.Error -> {
                AlertDialog(
                    onDismissRequest = onBack,
                    title = { Text("Unsupported or corrupted file format") },
                    text = { Text(uiState.message) },
                    confirmButton = {
                        TextButton(onClick = onBack) {
                            Text("Go Back")
                        }
                    }
                )
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = uiState.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is ExcelUiState.Success -> {
                val activeSheet = uiState.document.sheets.getOrNull(uiState.activeSheetIndex)
                    ?: Sheet("Sheet1")

                val colScrollState = rememberScrollState()
                
                // State for resizing columns and rows, and for zooming
                val colWidths = remember { mutableStateMapOf<Int, Dp>() }
                val rowHeights = remember { mutableStateMapOf<Int, Dp>() }
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                var showRenameDialog by remember { mutableStateOf(false) }
                var targetSheetForRename by remember { mutableStateOf(0) }
                var renameSheetInput by remember { mutableStateOf("") }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (uiState.document.hasUnrecognizedElements) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Read-Only: Spreadsheet contains unsupported visual elements (charts, drawing objects). Saving is disabled to prevent data loss.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    // Formula Bar Component
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val cellName = "${Sheet.colIndexToName(uiState.selectedCol)}${uiState.selectedRow + 1}"
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = cellName,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }

                                OutlinedTextField(
                                    value = uiState.formulaInput,
                                    onValueChange = viewModel::updateFormulaInput,
                                    placeholder = { Text("Enter text or formula (=SUM...)") },
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = viewModel::applyCellEdit) {
                                            Icon(Icons.Default.Check, contentDescription = "Apply")
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { viewModel.applyCellEdit() }),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("excel_formula_input")
                                )
                            }

                            // Quick Formula Helper Bar
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                listOf("SUM", "AVERAGE", "MIN", "MAX", "COUNT", "IF", "VLOOKUP", "INDEX", "MATCH", "ROUND", "CONCAT", "TODAY").forEach { fn ->
                                    AssistChip(
                                        onClick = { viewModel.insertFormulaSnippet(fn) },
                                        label = { Text("=$fn") },
                                        leadingIcon = { Icon(Icons.Default.Functions, contentDescription = fn, modifier = Modifier.size(16.dp)) }
                                    )
                                }
                            }
                        }
                    }

                    // Row & Column Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Rows: ${activeSheet.rowCount} | Cols: ${activeSheet.colCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { scale = (scale * 1.2f).coerceIn(0.5f, 3f) }) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { scale = (scale / 1.2f).coerceIn(0.5f, 3f) }) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { viewModel.insertRow() }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Row", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { viewModel.deleteRow() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Row", tint = MaterialTheme.colorScheme.error)
                            }
                            IconButton(onClick = { viewModel.insertColumn() }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Column", tint = MaterialTheme.colorScheme.secondary)
                            }
                            IconButton(onClick = { viewModel.deleteColumn() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Column", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                            }
                        }
                    }

                    // Grid View Sheet Canvas
                    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
                        scale = (scale * zoomChange).coerceIn(0.5f, 3f)
                        offset += panChange
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(8.dp)
                            .transformable(state = transformableState)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            val viewportWidthDp = maxWidth

                            val colWidthList = remember(activeSheet.colCount, colWidths.size) {
                                (0 until activeSheet.colCount).map { c ->
                                    (colWidths[c] ?: 100.dp) + 10.dp
                                }
                            }

                            val scrollOffsetDp = with(density) { colScrollState.value.toDp() }

                            var accumulatedWidth = 0.dp
                            var startCol = 0
                            var endCol = (activeSheet.colCount - 1).coerceAtLeast(0)

                            for (c in 0 until activeSheet.colCount) {
                                val w = colWidthList[c]
                                if (accumulatedWidth + w < scrollOffsetDp) {
                                    startCol = c + 1
                                }
                                if (accumulatedWidth > scrollOffsetDp + viewportWidthDp) {
                                    endCol = c
                                    break
                                }
                                accumulatedWidth += w
                            }

                            val safeStartCol = (startCol - 2).coerceAtLeast(0)
                            val safeEndCol = (endCol + 2).coerceAtMost(activeSheet.colCount - 1)

                            val leadingWidth = (0 until safeStartCol).fold(0.dp) { acc, c -> acc + colWidthList[c] }
                            val trailingWidth = ((safeEndCol + 1) until activeSheet.colCount).fold(0.dp) { acc, c -> acc + colWidthList[c] }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .horizontalScroll(colScrollState)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    // Column Headers Row (A, B, C...)
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            // Top-Left Empty Corner Cell
                                            Box(
                                                modifier = Modifier
                                                    .width(44.dp)
                                                    .height(32.dp)
                                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(" ", style = MaterialTheme.typography.labelSmall)
                                            }

                                            if (leadingWidth > 0.dp) {
                                                Spacer(modifier = Modifier.width(leadingWidth))
                                            }

                                            for (c in safeStartCol..safeEndCol) {
                                                val currentWidth = colWidths[c] ?: 100.dp
                                                Row(
                                                    modifier = Modifier
                                                        .height(32.dp)
                                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(currentWidth)
                                                            .height(32.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = Sheet.colIndexToName(c),
                                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                                        )
                                                    }
                                                    // Draggable handle for column resizing
                                                    Box(
                                                        modifier = Modifier
                                                            .width(10.dp)
                                                            .height(32.dp)
                                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                            .pointerInput(c) {
                                                                detectDragGestures { change, dragAmount ->
                                                                    change.consume()
                                                                    val newWidth = (currentWidth + dragAmount.x.dp).coerceAtLeast(40.dp)
                                                                    colWidths[c] = newWidth
                                                                }
                                                            }
                                                    )
                                                }
                                            }

                                            if (trailingWidth > 0.dp) {
                                                Spacer(modifier = Modifier.width(trailingWidth))
                                            }
                                        }
                                    }

                                    // Data Rows (1, 2, 3...)
                                    items(activeSheet.rowCount) { r ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            // Row Index Header
                                            val currentHeight = rowHeights[r] ?: 38.dp
                                            Column(
                                                modifier = Modifier
                                                    .width(44.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(44.dp)
                                                        .height(currentHeight),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "${r + 1}",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                // Draggable handle for row resizing
                                                Box(
                                                    modifier = Modifier
                                                        .width(44.dp)
                                                        .height(6.dp)
                                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                        .pointerInput(r) {
                                                            detectDragGestures { change, dragAmount ->
                                                                change.consume()
                                                                val newHeight = (currentHeight + dragAmount.y.dp).coerceAtLeast(24.dp)
                                                                rowHeights[r] = newHeight
                                                            }
                                                        }
                                                )
                                            }

                                            if (leadingWidth > 0.dp) {
                                                Spacer(modifier = Modifier.width(leadingWidth))
                                            }

                                            for (c in safeStartCol..safeEndCol) {
                                                val isSelected = r == uiState.selectedRow && c == uiState.selectedCol
                                                val cell = activeSheet.getCell(r, c)
                                                val currentWidth = (colWidths[c] ?: 100.dp) + 10.dp

                                                Box(
                                                    modifier = Modifier
                                                        .width(currentWidth)
                                                        .height(currentHeight + 6.dp)
                                                        .background(
                                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                            else cell.format.getBgColor()
                                                        )
                                                        .border(
                                                            width = if (isSelected) 2.dp else 0.5.dp,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                        )
                                                        .clickable { viewModel.selectCell(r, c) }
                                                        .padding(horizontal = 6.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    Text(
                                                        text = cell.displayValue,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = if (cell.format.isBold) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = 13.sp
                                                        ),
                                                        color = cell.format.getTextColor(),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            if (trailingWidth > 0.dp) {
                                                Spacer(modifier = Modifier.width(trailingWidth))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Multi-Sheet Tabs Bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 3.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uiState.document.sheets.forEachIndexed { index, sheet ->
                                val isActive = index == uiState.activeSheetIndex
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.clickable { viewModel.switchSheet(index) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = sheet.name,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isActive) {
                                            IconButton(
                                                onClick = {
                                                    targetSheetForRename = index
                                                    renameSheetInput = sheet.name
                                                    showRenameDialog = true
                                                },
                                                modifier = Modifier.size(24.dp).padding(start = 4.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Rename Sheet", modifier = Modifier.size(13.dp))
                                            }
                                            if (uiState.document.sheets.size > 1) {
                                                IconButton(
                                                    onClick = { viewModel.deleteSheet(index) },
                                                    modifier = Modifier.size(24.dp).padding(start = 2.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Delete Sheet", modifier = Modifier.size(13.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            IconButton(
                                onClick = { viewModel.addSheet() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Sheet", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    if (showRenameDialog) {
                        AlertDialog(
                            onDismissRequest = { showRenameDialog = false },
                            title = { Text("Rename Sheet") },
                            text = {
                                OutlinedTextField(
                                    value = renameSheetInput,
                                    onValueChange = { renameSheetInput = it },
                                    label = { Text("Sheet Name") },
                                    singleLine = true
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.renameSheet(targetSheetForRename, renameSheetInput)
                                        showRenameDialog = false
                                    }
                                ) {
                                    Text("Rename")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showRenameDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

