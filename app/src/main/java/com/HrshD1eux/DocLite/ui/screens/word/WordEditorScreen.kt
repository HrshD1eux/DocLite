package com.HrshD1eux.DocLite.ui.screens.word

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.HrshD1eux.DocLite.models.TextAlignment
import com.HrshD1eux.DocLite.models.WordBodyElement

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
                        val canEdit = !successState.document.hasUnrecognizedElements
                        if (canEdit) {
                            IconButton(onClick = viewModel::toggleEditMode) {
                                Icon(
                                    imageVector = if (successState.isEditing) Icons.Default.Visibility else Icons.Default.Edit,
                                    contentDescription = if (successState.isEditing) "View Mode" else "Edit Mode"
                                )
                            }
                        }
                        IconButton(
                            onClick = viewModel::saveDocument,
                            enabled = canEdit
                        ) {
                            Icon(
                                Icons.Default.Save, 
                                contentDescription = "Save Document",
                                tint = if (canEdit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
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
                                    text = "Read-Only: Document contains embedded media or special objects. Editing is restricted to prevent media corruption.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    // Stats Bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Words: ${uiState.document.wordCount} | Chars: ${uiState.document.characterCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val estReadTime = (uiState.document.wordCount / 200).coerceAtLeast(1)
                            Text(
                                text = "~$estReadTime min read",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Rich Text Formatting Toolbar (Visible in edit mode)
                    if (uiState.isEditing) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = viewModel::undo, enabled = uiState.canUndo) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                                }
                                IconButton(onClick = viewModel::redo, enabled = uiState.canRedo) {
                                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                                }

                                IconButton(
                                    onClick = viewModel::toggleBold,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (uiState.isBold) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Icon(Icons.Default.FormatBold, contentDescription = "Bold")
                                }

                                IconButton(
                                    onClick = viewModel::toggleItalic,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (uiState.isItalic) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Icon(Icons.Default.FormatItalic, contentDescription = "Italic")
                                }

                                IconButton(
                                    onClick = viewModel::toggleUnderline,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (uiState.isUnderline) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Icon(Icons.Default.FormatUnderlined, contentDescription = "Underline")
                                }

                                IconButton(
                                    onClick = { viewModel.setAlignment(TextAlignment.LEFT) },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (uiState.currentAlignment == TextAlignment.LEFT) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.FormatAlignLeft, contentDescription = "Align Left")
                                }

                                IconButton(
                                    onClick = { viewModel.setAlignment(TextAlignment.CENTER) },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (uiState.currentAlignment == TextAlignment.CENTER) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Icon(Icons.Default.FormatAlignCenter, contentDescription = "Align Center")
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { viewModel.setFontSize(uiState.fontSizeSp - 2f) }) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease Font Size", modifier = Modifier.size(16.dp))
                                    }
                                    Icon(Icons.Default.FormatSize, contentDescription = "Font Size", modifier = Modifier.size(20.dp))
                                    IconButton(onClick = { viewModel.setFontSize(uiState.fontSizeSp + 2f) }) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase Font Size", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Document Page Sheet Canvas
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (uiState.isEditing) {
                                // Edit mode: iterate bodyElements — paragraphs are editable, tables are read-only
                                val elements = uiState.document.bodyElements
                                var paragraphIndex = 0

                                items(elements.size) { elementIndex ->
                                    val element = elements[elementIndex]

                                    when (element) {
                                        is WordBodyElement.ParagraphElement -> {
                                            val currentParagraphIndex = paragraphIndex
                                            paragraphIndex++

                                            // Insert-paragraph button above each paragraph
                                            InsertParagraphButton(
                                                onClick = { viewModel.insertParagraphAt(currentParagraphIndex) }
                                            )

                                            // Editable paragraph with delete button
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                OutlinedTextField(
                                                    value = element.paragraph.getPlainText(),
                                                    onValueChange = { newText ->
                                                        viewModel.updateParagraphText(currentParagraphIndex, newText)
                                                    },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("word_p_input_$currentParagraphIndex"),
                                                    shape = RoundedCornerShape(8.dp),
                                                    textStyle = androidx.compose.ui.text.TextStyle(
                                                        fontSize = uiState.fontSizeSp.sp,
                                                        fontWeight = if (uiState.isBold) FontWeight.Bold else FontWeight.Normal
                                                    ),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                                    )
                                                )
                                                // Delete paragraph button
                                                if (uiState.document.paragraphs.size > 1) {
                                                    IconButton(
                                                        onClick = { viewModel.deleteParagraph(currentParagraphIndex) },
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = "Delete Paragraph",
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        is WordBodyElement.TableElement -> {
                                            // Tables rendered as read-only cards in edit mode (same as view mode)
                                            ReadOnlyTableCard(table = element.table)
                                        }
                                    }
                                }

                                // Add paragraph button at the bottom
                                item {
                                    InsertParagraphButton(
                                        onClick = viewModel::addParagraph,
                                        label = "Add Paragraph"
                                    )
                                }
                            } else {
                                // View mode: iterate bodyElements with rich text rendering
                                val elements = uiState.document.bodyElements.ifEmpty {
                                    uiState.document.paragraphs.map { WordBodyElement.ParagraphElement(it) }
                                }
                                items(elements) { element ->
                                    when (element) {
                                        is WordBodyElement.ParagraphElement -> {
                                            val paragraph = element.paragraph
                                            val annotatedText = buildAnnotatedString {
                                                if (!paragraph.bulletPrefix.isNullOrEmpty()) {
                                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                                                        append(paragraph.bulletPrefix)
                                                    }
                                                }
                                                paragraph.runs.forEach { run ->
                                                    val spanStyle = SpanStyle(
                                                        fontWeight = if (run.style.isBold) FontWeight.Bold else if (paragraph.isHeader) FontWeight.Bold else FontWeight.Normal,
                                                        fontStyle = if (run.style.isItalic) FontStyle.Italic else FontStyle.Normal,
                                                        textDecoration = if (run.style.isUnderline) TextDecoration.Underline else TextDecoration.None,
                                                        fontSize = if (paragraph.isHeader) 20.sp else run.style.fontSizeSp.sp,
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
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        is WordBodyElement.TableElement -> {
                                            ReadOnlyTableCard(table = element.table)
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

/**
 * Reusable read-only table card used in both view and edit modes.
 */
@Composable
private fun ReadOnlyTableCard(table: com.HrshD1eux.DocLite.models.WordTable) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            table.rows.forEachIndexed { rIdx, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (rIdx == 0) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            else Modifier
                        )
                ) {
                    row.cells.forEach { cell ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cell.text,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (cell.isHeader) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Small insert-paragraph button shown between elements in edit mode.
 */
@Composable
private fun InsertParagraphButton(
    onClick: () -> Unit,
    label: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = label ?: "Insert Paragraph",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        }
    }
}
