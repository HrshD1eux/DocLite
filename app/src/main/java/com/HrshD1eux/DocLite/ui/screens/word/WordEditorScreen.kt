package com.HrshD1eux.DocLite.ui.screens.word

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.HrshD1eux.DocLite.models.Paragraph
import com.HrshD1eux.DocLite.models.TextAlignment
import com.HrshD1eux.DocLite.models.WordBodyElement
import com.HrshD1eux.DocLite.models.WordTable

private data class TableCellEditTarget(
    val elementIndex: Int,
    val rowIndex: Int,
    val colIndex: Int,
    val initialText: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordEditorScreen(
    viewModel: WordViewModel,
    fileUri: Uri,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    var editingCell by remember { mutableStateOf<TableCellEditTarget?>(null) }
    var cellEditText by remember { mutableStateOf("") }

    LaunchedEffect(fileUri) {
        viewModel.loadDocument(fileUri)
    }

    LaunchedEffect((state as? WordUiState.Success)?.saveStatus) {
        val status = (state as? WordUiState.Success)?.saveStatus
        if (status != null) {
            snackbarHostState.showSnackbar(status)
        }
    }

    val successState = state as? WordUiState.Success
    val hasUnsavedChanges = successState?.canUndo == true

    BackHandler {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onBack()
        }
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes in this document. Do you want to discard them and exit?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsavedDialog = false
                        onBack()
                    }
                ) {
                    Text("Discard & Exit", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.saveDocument()
                        showUnsavedDialog = false
                        onBack()
                    }
                ) {
                    Text("Save & Exit")
                }
            }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Document name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_word_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            viewModel.renameDocument(renameInput.trim())
                        }
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

    if (editingCell != null) {
        val target = editingCell!!
        AlertDialog(
            onDismissRequest = { editingCell = null },
            title = { Text("Edit Table Cell (Row ${target.rowIndex + 1}, Col ${target.colIndex + 1})") },
            text = {
                OutlinedTextField(
                    value = cellEditText,
                    onValueChange = { cellEditText = it },
                    label = { Text("Cell Text") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateTableCell(target.elementIndex, target.rowIndex, target.colIndex, cellEditText)
                        editingCell = null
                    }
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCell = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val docTitle = successState?.document?.title ?: "Word Editor"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                renameInput = docTitle.substringBeforeLast(".")
                                showRenameDialog = true
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = docTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.DriveFileRenameOutline,
                            contentDescription = "Rename Document",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (hasUnsavedChanges) {
                            Text(
                                text = " •",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (hasUnsavedChanges) showUnsavedDialog = true else onBack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (successState != null) {
                        IconButton(onClick = viewModel::toggleEditMode) {
                            Icon(
                                imageVector = if (successState.isEditing) Icons.Default.Visibility else Icons.Default.Edit,
                                contentDescription = if (successState.isEditing) "View Mode" else "Edit Mode"
                            )
                        }
                        IconButton(onClick = viewModel::saveDocument) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = "Save Document",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        when (val uiState = state) {
            is WordUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is WordUiState.Error -> {
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
                    Text(
                        text = uiState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            is WordUiState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    // Document Stats Ribbon
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Words: ${uiState.document.wordCount}  |  Characters: ${uiState.document.characterCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val estReadTime = (uiState.document.wordCount / 200).coerceAtLeast(1)
                            Text(
                                text = "~$estReadTime min read",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Rich Formatting Toolbar (Visible in Edit Mode)
                    if (uiState.isEditing) {
                        WordFormattingRibbon(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }

                    // Authentic Document Sheet Canvas
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            if (uiState.isEditing) {
                                // EDIT MODE: Clean inline document editing
                                val elements = uiState.document.bodyElements
                                var pIndex = 0

                                itemsIndexed(
                                    items = elements,
                                    key = { _, elem -> elem.id }
                                ) { elemIndex, element ->
                                    when (element) {
                                        is WordBodyElement.ParagraphElement -> {
                                            val currentPIndex = pIndex
                                            pIndex++
                                            val isFocused = uiState.activeElementIndex == elemIndex

                                            EditableParagraphRow(
                                                paragraph = element.paragraph,
                                                elementIndex = elemIndex,
                                                paragraphIndex = currentPIndex,
                                                isFocused = isFocused,
                                                onTextChange = { newText ->
                                                    viewModel.updateParagraphText(currentPIndex, newText)
                                                },
                                                onFocus = {
                                                    viewModel.setActiveElement(elemIndex)
                                                },
                                                onInsertBelow = {
                                                    viewModel.insertParagraphAt(currentPIndex + 1)
                                                },
                                                onInsertTableBelow = {
                                                    viewModel.insertTable(rows = 3, cols = 3, afterIndex = elemIndex)
                                                },
                                                onDuplicate = {
                                                    viewModel.duplicateParagraph(elemIndex)
                                                },
                                                onDelete = {
                                                    viewModel.deleteParagraph(currentPIndex)
                                                },
                                                canDelete = uiState.document.paragraphs.size > 1
                                            )
                                        }

                                        is WordBodyElement.TableElement -> {
                                            InteractiveTableCard(
                                                table = element.table,
                                                isEditing = true,
                                                onCellClick = { rIdx, cIdx, text ->
                                                    editingCell = TableCellEditTarget(elemIndex, rIdx, cIdx, text)
                                                    cellEditText = text
                                                },
                                                onAddRow = { viewModel.addTableRow(elemIndex) },
                                                onDeleteRow = { rIdx -> viewModel.deleteTableRow(elemIndex, rIdx) }
                                            )
                                        }

                                        is WordBodyElement.ImageElement -> {
                                            DocumentImageItem(element = element)
                                        }
                                    }
                                }

                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Button(
                                            onClick = viewModel::addParagraph,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            shape = RoundedCornerShape(20.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Add Paragraph", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            } else {
                                // VIEW MODE: Crisp reading typography
                                items(
                                    items = uiState.document.bodyElements,
                                    key = { it.id }
                                ) { element ->
                                    when (element) {
                                        is WordBodyElement.ParagraphElement -> {
                                            ViewModeParagraph(paragraph = element.paragraph)
                                        }

                                        is WordBodyElement.TableElement -> {
                                            InteractiveTableCard(
                                                table = element.table,
                                                isEditing = false
                                            )
                                        }

                                        is WordBodyElement.ImageElement -> {
                                            DocumentImageItem(element = element)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordFormattingRibbon(
    uiState: WordUiState.Success,
    viewModel: WordViewModel
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Undo / Redo
            IconButton(
                onClick = viewModel::undo,
                enabled = uiState.canUndo,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    modifier = Modifier.size(20.dp),
                    tint = if (uiState.canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
            IconButton(
                onClick = viewModel::redo,
                enabled = uiState.canRedo,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    modifier = Modifier.size(20.dp),
                    tint = if (uiState.canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            RibbonDivider()

            // Bold / Italic / Underline
            FormattingToggleButton(
                icon = Icons.Default.FormatBold,
                label = "Bold",
                isActive = uiState.isBold,
                onClick = viewModel::toggleBold
            )
            FormattingToggleButton(
                icon = Icons.Default.FormatItalic,
                label = "Italic",
                isActive = uiState.isItalic,
                onClick = viewModel::toggleItalic
            )
            FormattingToggleButton(
                icon = Icons.Default.FormatUnderlined,
                label = "Underline",
                isActive = uiState.isUnderline,
                onClick = viewModel::toggleUnderline
            )

            RibbonDivider()

            // Alignments
            FormattingToggleButton(
                icon = Icons.AutoMirrored.Filled.FormatAlignLeft,
                label = "Align Left",
                isActive = uiState.currentAlignment == TextAlignment.LEFT,
                onClick = { viewModel.setAlignment(TextAlignment.LEFT) }
            )
            FormattingToggleButton(
                icon = Icons.Default.FormatAlignCenter,
                label = "Align Center",
                isActive = uiState.currentAlignment == TextAlignment.CENTER,
                onClick = { viewModel.setAlignment(TextAlignment.CENTER) }
            )
            FormattingToggleButton(
                icon = Icons.AutoMirrored.Filled.FormatAlignRight,
                label = "Align Right",
                isActive = uiState.currentAlignment == TextAlignment.RIGHT,
                onClick = { viewModel.setAlignment(TextAlignment.RIGHT) }
            )
            FormattingToggleButton(
                icon = Icons.Default.FormatAlignJustify,
                label = "Justify",
                isActive = uiState.currentAlignment == TextAlignment.JUSTIFY,
                onClick = { viewModel.setAlignment(TextAlignment.JUSTIFY) }
            )

            RibbonDivider()

            // Heading & Bullet chips
            FilterChip(
                selected = uiState.isHeader && uiState.headerLevel == 1,
                onClick = { viewModel.toggleHeading(1) },
                label = { Text("H1", fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.height(32.dp)
            )
            FilterChip(
                selected = uiState.isHeader && uiState.headerLevel == 2,
                onClick = { viewModel.toggleHeading(2) },
                label = { Text("H2", fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.height(32.dp)
            )
            FormattingToggleButton(
                icon = Icons.Default.FormatListBulleted,
                label = "Bullet List",
                isActive = uiState.isBullet,
                onClick = viewModel::toggleBullet
            )

            RibbonDivider()

            // Font Size Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                IconButton(
                    onClick = { viewModel.setFontSize(uiState.fontSizeSp - 2f) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease Font Size", modifier = Modifier.size(16.dp))
                }
                Text(
                    text = "${uiState.fontSizeSp.toInt()} sp",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                IconButton(
                    onClick = { viewModel.setFontSize(uiState.fontSizeSp + 2f) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase Font Size", modifier = Modifier.size(16.dp))
                }
            }

            RibbonDivider()

            // Insert Table Action
            Button(
                onClick = { viewModel.insertTable(rows = 3, cols = 3) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Table", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun RibbonDivider() {
    Spacer(modifier = Modifier.width(2.dp))
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(24.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
    Spacer(modifier = Modifier.width(2.dp))
}

@Composable
private fun FormattingToggleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.size(36.dp)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun EditableParagraphRow(
    paragraph: Paragraph,
    elementIndex: Int,
    paragraphIndex: Int,
    isFocused: Boolean,
    onTextChange: (String) -> Unit,
    onFocus: () -> Unit,
    onInsertBelow: () -> Unit,
    onInsertTableBelow: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val rawText = paragraph.getPlainText()
    // Strip bullet prefix for editing view
    val cleanText = if (paragraph.bulletPrefix != null && rawText.startsWith(paragraph.bulletPrefix)) {
        rawText.removePrefix(paragraph.bulletPrefix)
    } else {
        rawText
    }

    val primaryRun = paragraph.runs.firstOrNull()
    val isBold = paragraph.runs.any { it.style.isBold } || paragraph.isHeader
    val isItalic = paragraph.runs.any { it.style.isItalic }
    val isUnderline = paragraph.runs.any { it.style.isUnderline }
    val fontSizeSp = if (paragraph.isHeader) {
        when (paragraph.headerLevel) {
            1 -> 24f
            2 -> 20f
            else -> 18f
        }
    } else {
        primaryRun?.style?.fontSizeSp ?: 16f
    }
    val fontColor = primaryRun?.style?.getComposeColor() ?: MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isFocused) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Bullet marker if active
        if (paragraph.bulletPrefix != null) {
            Text(
                text = "• ",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = fontSizeSp.sp,
                modifier = Modifier.padding(end = 4.dp, top = 2.dp)
            )
        }

        // Clean, authentic inline text editing using BasicTextField
        BasicTextField(
            value = cleanText,
            onValueChange = { newContent ->
                val reconstructed = if (paragraph.bulletPrefix != null) "${paragraph.bulletPrefix}$newContent" else newContent
                onTextChange(reconstructed)
            },
            modifier = Modifier
                .weight(1f)
                .testTag("word_p_input_$paragraphIndex")
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) onFocus()
                },
            textStyle = TextStyle(
                color = fontColor,
                fontSize = fontSizeSp.sp,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (isUnderline) TextDecoration.Underline else TextDecoration.None,
                textAlign = paragraph.alignment.toComposeTextAlign(),
                lineHeight = (fontSizeSp * 1.35f).sp
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
        )

        // Contextual action menu button (visible when focused or tapped)
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Paragraph Options",
                    modifier = Modifier.size(16.dp),
                    tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Insert Paragraph Below") },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onInsertBelow()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Insert Table Below") },
                    leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onInsertTableBelow()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Duplicate") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onDuplicate()
                    }
                )
                if (canDelete) {
                    DropdownMenuItem(
                        text = { Text("Delete Paragraph", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewModeParagraph(paragraph: Paragraph) {
    val annotatedText = buildAnnotatedString {
        if (!paragraph.bulletPrefix.isNullOrEmpty()) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                append(paragraph.bulletPrefix)
            }
        }
        paragraph.runs.forEach { run ->
            val spanStyle = SpanStyle(
                fontWeight = if (run.style.isBold || paragraph.isHeader) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (run.style.isItalic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (run.style.isUnderline) TextDecoration.Underline else TextDecoration.None,
                fontSize = if (paragraph.isHeader) {
                    when (paragraph.headerLevel) {
                        1 -> 24.sp
                        2 -> 20.sp
                        else -> 18.sp
                    }
                } else run.style.fontSizeSp.sp,
                color = run.style.getComposeColor()
            )
            withStyle(spanStyle) {
                append(run.text)
            }
        }
    }

    Text(
        text = annotatedText,
        textAlign = paragraph.alignment.toComposeTextAlign(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (paragraph.isHeader) 8.dp else 4.dp),
        lineHeight = if (paragraph.isHeader) 30.sp else 22.sp
    )
}

@Composable
private fun InteractiveTableCard(
    table: WordTable,
    isEditing: Boolean,
    onCellClick: ((rowIndex: Int, colIndex: Int, text: String) -> Unit)? = null,
    onAddRow: (() -> Unit)? = null,
    onDeleteRow: ((rowIndex: Int) -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            table.rows.forEachIndexed { rIdx, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (rIdx == 0) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                            else Modifier
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.cells.forEachIndexed { cIdx, cell ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                .then(
                                    if (isEditing && onCellClick != null) {
                                        Modifier.clickable { onCellClick(rIdx, cIdx, cell.text) }
                                    } else Modifier
                                )
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = cell.text.ifEmpty { if (isEditing) "—" else "" },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (cell.isHeader) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (cell.text.isEmpty() && isEditing) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (isEditing && onDeleteRow != null && table.rows.size > 1 && rIdx > 0) {
                        IconButton(
                            onClick = { onDeleteRow(rIdx) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Delete Row",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            if (isEditing && onAddRow != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onAddRow)
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Table Row", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentImageItem(element: WordBodyElement.ImageElement) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(element.imageUri)
                    .crossfade(true)
                    .build(),
                contentDescription = element.caption ?: "Embedded Document Image",
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .height(element.heightDp.dp)
                    .fillMaxWidth(),
                contentScale = ContentScale.Fit
            )
            if (!element.caption.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = element.caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
