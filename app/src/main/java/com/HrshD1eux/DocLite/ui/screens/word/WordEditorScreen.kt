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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
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
    var fitToScreen by rememberSaveable { mutableStateOf(true) }

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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val estReadTime = (uiState.document.wordCount / 200).coerceAtLeast(1)
                                Text(
                                    text = "~$estReadTime min read",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FilterChip(
                                    selected = fitToScreen,
                                    onClick = { fitToScreen = !fitToScreen },
                                    label = {
                                        Text(
                                            if (fitToScreen) "Fit Screen" else "A4 View",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (fitToScreen) Icons.Default.FitScreen else Icons.Default.AspectRatio,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }

                    // Rich Formatting Toolbar (Visible in Edit Mode)
                    if (uiState.isEditing) {
                        WordFormattingRibbon(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }

                    // Paginated Document Canvas (Fit to Screen or A4 View)
                    val pages = remember(uiState.document.bodyElements) {
                        paginateDocumentElements(uiState.document.bodyElements)
                    }

                    val deskModifier = if (fitToScreen) {
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xFFE8EEF2))
                    } else {
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xFFE8EEF2))
                            .horizontalScroll(rememberScrollState())
                    }

                    Box(
                        modifier = deskModifier,
                        contentAlignment = Alignment.TopCenter
                    ) {
                        val columnModifier = if (fitToScreen) {
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp)
                        } else {
                            Modifier
                                .width(620.dp)
                                .padding(horizontal = 8.dp)
                        }

                        LazyColumn(
                            modifier = columnModifier,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
                        ) {
                            items(
                                items = pages,
                                key = { page -> "page_${page.pageNumber}" }
                            ) { page ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(4.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    border = BorderStroke(0.5.dp, Color(0xFFD0D7DE))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                horizontal = if (fitToScreen) 16.dp else 36.dp,
                                                vertical = 20.dp
                                            )
                                    ) {
                                        // Header
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = uiState.document.title.removeSuffix(".docx").removeSuffix(".doc"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Text(
                                                text = "Page ${page.pageNumber} of ${pages.size}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                                color = Color.Gray.copy(alpha = 0.8f)
                                            )
                                        }
                                        HorizontalDivider(
                                            modifier = Modifier.padding(bottom = 14.dp),
                                            thickness = 0.5.dp,
                                            color = Color.LightGray.copy(alpha = 0.35f)
                                        )

                                        // Page Elements
                                        for (indexedElem in page.elements) {
                                            val elemIndex = indexedElem.globalIndex
                                            val currentPIndex = indexedElem.paragraphIndex
                                            val element = indexedElem.element

                                            if (uiState.isEditing) {
                                                when (element) {
                                                    is WordBodyElement.PageBreakElement -> {
                                                        PageBreakCutDivider(
                                                            pageNumber = page.pageNumber,
                                                            isEditing = true,
                                                            onDelete = { viewModel.deletePageBreak(elemIndex) }
                                                        )
                                                    }
                                                    is WordBodyElement.ParagraphElement -> {
                                                        val isFocused = uiState.activeElementIndex == elemIndex
                                                        if (element.paragraph.hasPageBreakBefore) {
                                                            PageBreakCutDivider(pageNumber = 0, isEditing = true)
                                                        }
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
                                                            onInsertPageBreakBelow = {
                                                                viewModel.insertPageBreak(elemIndex)
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
                                                            fitToScreen = fitToScreen,
                                                            onCellClick = { rIdx, cIdx, text ->
                                                                editingCell = TableCellEditTarget(elemIndex, rIdx, cIdx, text)
                                                                cellEditText = text
                                                            },
                                                            onAddRow = { viewModel.addTableRow(elemIndex) },
                                                            onDeleteRow = { rIdx -> viewModel.deleteTableRow(elemIndex, rIdx) }
                                                        )
                                                    }
                                                    is WordBodyElement.ImageElement -> {
                                                        DocumentImageItem(element = element, fitToScreen = fitToScreen)
                                                    }
                                                }
                                            } else {
                                                when (element) {
                                                    is WordBodyElement.PageBreakElement -> {
                                                        PageBreakCutDivider(
                                                            pageNumber = page.pageNumber,
                                                            isEditing = false
                                                        )
                                                    }
                                                    is WordBodyElement.ParagraphElement -> {
                                                        if (element.paragraph.hasPageBreakBefore) {
                                                            PageBreakCutDivider(pageNumber = 0, isEditing = false)
                                                        }
                                                        ViewModeParagraph(paragraph = element.paragraph)
                                                    }
                                                    is WordBodyElement.TableElement -> {
                                                        InteractiveTableCard(
                                                            table = element.table,
                                                            isEditing = false,
                                                            fitToScreen = fitToScreen
                                                        )
                                                    }
                                                    is WordBodyElement.ImageElement -> {
                                                        DocumentImageItem(element = element, fitToScreen = fitToScreen)
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }

                                        // Page Footer
                                        Spacer(modifier = Modifier.height(16.dp))
                                        HorizontalDivider(
                                            modifier = Modifier.padding(bottom = 8.dp),
                                            thickness = 0.5.dp,
                                            color = Color.LightGray.copy(alpha = 0.25f)
                                        )
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${page.pageNumber}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }

                            if (uiState.isEditing) {
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
                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
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
    onInsertPageBreakBelow: () -> Unit,
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
                    text = { Text("Insert Page Break Below") },
                    leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onInsertPageBreakBelow()
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
private fun PageBreakCutDivider(
    pageNumber: Int,
    isEditing: Boolean = false,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = Color(0xFF90A4AE),
                thickness = 1.dp
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFECEFF1),
                border = BorderStroke(1.dp, Color(0xFFB0BEC5)),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ContentCut,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF455A64)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (pageNumber > 0) "✂ Page Break • Page $pageNumber" else "✂ Page Break",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF37474F)
                    )
                    if (isEditing && onDelete != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Delete Page Break",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onDelete() }
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = Color(0xFF90A4AE),
                thickness = 1.dp
            )
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
    fitToScreen: Boolean = true,
    onCellClick: ((rowIndex: Int, colIndex: Int, text: String) -> Unit)? = null,
    onAddRow: (() -> Unit)? = null,
    onDeleteRow: ((rowIndex: Int) -> Unit)? = null
) {
    if (table.rows.isEmpty()) return

    val numCols = table.rows.maxOfOrNull { it.cells.size } ?: 0
    val needsHorizontalScroll = numCols > 3 || !fitToScreen

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color(0xFFCFD8DC)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val tableContentModifier = if (needsHorizontalScroll) {
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxWidth()
            }

            Column(modifier = tableContentModifier) {
                table.rows.forEachIndexed { rIdx, row ->
                    val isHeaderRow = rIdx == 0
                    Row(
                        modifier = Modifier
                            .then(
                                if (needsHorizontalScroll) Modifier.widthIn(min = (numCols * 110).dp)
                                else Modifier.fillMaxWidth()
                            )
                            .then(
                                if (isHeaderRow) Modifier.background(Color(0xFFF1F5F9))
                                else if (rIdx % 2 == 1) Modifier.background(Color(0xFFFAFAFA))
                                else Modifier.background(Color.White)
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.cells.forEachIndexed { cIdx, cell ->
                            val cellModifier = if (needsHorizontalScroll) {
                                Modifier
                                    .widthIn(min = 100.dp, max = 240.dp)
                                    .border(0.5.dp, Color(0xFFE2E8F0))
                            } else {
                                Modifier
                                    .weight(1f)
                                    .border(0.5.dp, Color(0xFFE2E8F0))
                            }

                            Box(
                                modifier = cellModifier
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
                                        fontWeight = if (cell.isHeader || isHeaderRow) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = if (cell.isHeader || isHeaderRow) 13.sp else 12.sp
                                    ),
                                    color = if (cell.text.isEmpty() && isEditing) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    else if (isHeaderRow) Color(0xFF1E293B)
                                    else Color(0xFF334155)
                                )
                            }
                        }

                        if (isEditing && onDeleteRow != null && table.rows.size > 1 && rIdx > 0) {
                            IconButton(
                                onClick = { onDeleteRow(rIdx) },
                                modifier = Modifier.size(28.dp).padding(2.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Delete Row",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            if (needsHorizontalScroll && numCols > 3) {
                Text(
                    text = "◀ Swipe table horizontally to see all $numCols columns ▶",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
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
private fun DocumentImageItem(
    element: WordBodyElement.ImageElement,
    fitToScreen: Boolean = true
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFCFD8DC)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val context = LocalContext.current
            var isImageError by remember { mutableStateOf(false) }

            if (isImageError) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Image Error",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Embedded Image (${element.widthDp} × ${element.heightDp} dp)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(element.imageUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = element.caption ?: "Embedded Document Image",
                    onError = { isImageError = true },
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 380.dp),
                    contentScale = ContentScale.Fit
                )
            }

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

data class DocumentPage(
    val pageNumber: Int,
    val elements: List<IndexedDocElement>
)

data class IndexedDocElement(
    val globalIndex: Int,
    val paragraphIndex: Int,
    val element: WordBodyElement
)

private fun paginateDocumentElements(bodyElements: List<WordBodyElement>): List<DocumentPage> {
    if (bodyElements.isEmpty()) {
        return listOf(
            DocumentPage(
                pageNumber = 1,
                elements = listOf(
                    IndexedDocElement(
                        globalIndex = 0,
                        paragraphIndex = 0,
                        element = WordBodyElement.ParagraphElement(Paragraph())
                    )
                )
            )
        )
    }

    val pages = mutableListOf<DocumentPage>()
    val currentElements = mutableListOf<IndexedDocElement>()
    var pIndex = 0
    var pageCounter = 1
    var accumulatedWeight = 0
    val maxWeightPerPage = 420 // Word count / visual weight budget per page

    for (elemIndex in bodyElements.indices) {
        val element = bodyElements[elemIndex]
        val currentPIndex = if (element is WordBodyElement.ParagraphElement) pIndex++ else -1

        // Explicit Page Break element
        if (element is WordBodyElement.PageBreakElement) {
            if (currentElements.isNotEmpty()) {
                pages.add(DocumentPage(pageCounter++, currentElements.toList()))
                currentElements.clear()
                accumulatedWeight = 0
            }
            currentElements.add(IndexedDocElement(elemIndex, currentPIndex, element))
            pages.add(DocumentPage(pageCounter++, currentElements.toList()))
            currentElements.clear()
            accumulatedWeight = 0
            continue
        }

        // Paragraph with hasPageBreakBefore
        if (element is WordBodyElement.ParagraphElement && element.paragraph.hasPageBreakBefore) {
            if (currentElements.isNotEmpty()) {
                pages.add(DocumentPage(pageCounter++, currentElements.toList()))
                currentElements.clear()
                accumulatedWeight = 0
            }
        }

        // Weight estimation
        val elemWeight = when (element) {
            is WordBodyElement.ParagraphElement -> {
                val words = element.paragraph.runs.sumOf { it.text.split("\\s+".toRegex()).count { w -> w.isNotBlank() } }
                val lines = element.paragraph.runs.sumOf { it.text.count { c -> c == '\n' } } + 1
                (words + lines * 15).coerceAtLeast(15)
            }
            is WordBodyElement.TableElement -> {
                val cellCount = element.table.rows.sumOf { it.cells.size }
                (cellCount * 12 + 40).coerceAtLeast(40)
            }
            is WordBodyElement.ImageElement -> 120
            is WordBodyElement.PageBreakElement -> 0
        }

        // Natural page break if capacity reached
        if (currentElements.isNotEmpty() && (accumulatedWeight + elemWeight > maxWeightPerPage)) {
            pages.add(DocumentPage(pageCounter++, currentElements.toList()))
            currentElements.clear()
            accumulatedWeight = 0
        }

        currentElements.add(IndexedDocElement(elemIndex, currentPIndex, element))
        accumulatedWeight += elemWeight
    }

    if (currentElements.isNotEmpty() || pages.isEmpty()) {
        pages.add(DocumentPage(pageCounter, currentElements.toList()))
    }

    return pages
}
