package com.HrshD1eux.DocLite.ui.screens.word

import android.net.Uri
import java.io.File
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.compose.foundation.layout.defaultMinSize
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
    var a4PrintMode by rememberSaveable { mutableStateOf(true) }
    var zoomScale by rememberSaveable { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val fitToScreen = !a4PrintMode

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
                    // Paginated Document Canvas Pages
                    val pages = remember(uiState.document.bodyElements) {
                        paginateDocumentElements(uiState.document.bodyElements)
                    }

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
                                text = "${pages.size} Pages",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            FilterChip(
                                selected = a4PrintMode,
                                onClick = {
                                    a4PrintMode = !a4PrintMode
                                    if (!a4PrintMode) {
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    }
                                },
                                label = {
                                    Text(
                                        if (a4PrintMode) "A4 Mode" else "Mobile Flow",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (a4PrintMode) Icons.Default.AspectRatio else Icons.Default.FitScreen,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.height(32.dp)
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

                    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
                        if (a4PrintMode) {
                            val newScale = (zoomScale * zoomChange).coerceIn(0.6f, 3.0f)
                            zoomScale = newScale
                            if (newScale > 1.05f) {
                                panOffset = Offset(
                                    x = (panOffset.x + panChange.x).coerceIn(-400f * (newScale - 1f), 400f * (newScale - 1f)),
                                    y = 0f
                                )
                            } else {
                                panOffset = Offset.Zero
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFE2E8F0))
                            .then(
                                if (a4PrintMode) {
                                    Modifier
                                        .transformable(state = transformableState)
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onDoubleTap = {
                                                    zoomScale = if (zoomScale > 1.05f) 1f else 1.5f
                                                    panOffset = Offset.Zero
                                                }
                                            )
                                        }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        val columnModifier = if (a4PrintMode) {
                            Modifier
                                .widthIn(min = 340.dp, max = 560.dp)
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    translationX = panOffset.x
                                }
                                .padding(horizontal = 8.dp)
                        } else {
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp)
                        }

                        LazyColumn(
                            modifier = columnModifier,
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)
                        ) {
                            items(
                                items = pages,
                                key = { page -> "page_${page.pageNumber}" }
                            ) { page ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (a4PrintMode) Modifier.defaultMinSize(minHeight = 540.dp) else Modifier),
                                    shape = RoundedCornerShape(2.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (a4PrintMode) 4.dp else 1.dp),
                                    border = BorderStroke(0.5.dp, Color(0xFFD0D7DE))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                horizontal = if (a4PrintMode) 10.dp else 10.dp,
                                                vertical = if (a4PrintMode) 18.dp else 14.dp
                                            )
                                    ) {

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
                                            Spacer(modifier = Modifier.height(if (element is WordBodyElement.TableElement || element is WordBodyElement.ImageElement) 6.dp else 1.dp))
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

                        // Floating Zoom Reset Badge in A4 Print View
                        if (a4PrintMode && (zoomScale < 0.95f || zoomScale > 1.05f)) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 16.dp, bottom = 100.dp)
                                    .clickable {
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shadowElevation = 4.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${(zoomScale * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Reset Zoom",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
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
    val maxFontSize = paragraph.runs.maxOfOrNull { it.style.fontSizeSp } ?: 12f
    val isTitleOrHeading = paragraph.isHeader || paragraph.headerLevel > 0 ||
        (maxFontSize >= 20f && paragraph.alignment == TextAlignment.CENTER)
    val isBlankPara = paragraph.getPlainText().isBlank()

    val annotatedText = buildAnnotatedString {
        if (!paragraph.bulletPrefix.isNullOrEmpty()) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                append(paragraph.bulletPrefix)
            }
        }
        paragraph.runs.forEach { run ->
            val rawColor = run.style.getComposeColor()
            val runColor = if (isTitleOrHeading && (rawColor == Color(0xFF1C1B1F) || rawColor == Color.Black || rawColor == Color(0xFF1E293B))) {
                Color(0xFF1F497D)
            } else {
                rawColor
            }
            val spanStyle = SpanStyle(
                fontWeight = if (run.style.isBold || paragraph.isHeader || isTitleOrHeading) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (run.style.isItalic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (run.style.isUnderline) TextDecoration.Underline else TextDecoration.None,
                fontSize = if (paragraph.isHeader) {
                    when (paragraph.headerLevel) {
                        1 -> 24.sp
                        2 -> 20.sp
                        else -> 18.sp
                    }
                } else run.style.fontSizeSp.sp,
                color = runColor
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
            .padding(
                vertical = when {
                    paragraph.isHeader -> 6.dp
                    isBlankPara -> 2.dp
                    else -> 1.5.dp
                }
            ),
        lineHeight = when {
            paragraph.isHeader -> 28.sp
            maxFontSize >= 18f -> (maxFontSize * 1.3f).sp
            isBlankPara -> 8.sp
            else -> 18.sp
        }
    )
}

internal fun computeColumnWeights(table: WordTable): List<Float> {
    val numCols = table.rows.maxOfOrNull { it.cells.size } ?: 0
    if (numCols == 0) return emptyList()

    if (numCols == 2) {
        return listOf(1f, 1f)
    }

    val maxLens = IntArray(numCols) { 0 }
    for (row in table.rows) {
        row.cells.forEachIndexed { cIdx, cell ->
            if (cIdx < numCols) {
                val len = cell.text.trim().length
                if (len > maxLens[cIdx]) {
                    maxLens[cIdx] = len
                }
            }
        }
    }

    return List(numCols) { cIdx ->
        val maxLen = maxLens[cIdx]
        when {
            numCols == 5 -> {
                when (cIdx) {
                    0 -> 0.95f // S.No. (fits on 1 line without wrapping)
                    1 -> 3.75f // Practical Title (wide, wraps cleanly)
                    2 -> 1.45f // Date (fits "11/08/2026" or "11/08/26" on 1 line)
                    3 -> 1.15f // Page No. (fits "Page No." and "37-38")
                    4 -> 1.45f // Teacher's Sign (fits comfortably on mobile)
                    else -> 1.0f
                }
            }
            numCols == 4 -> {
                when (cIdx) {
                    0 -> 1.05f // S.No.
                    1 -> 4.5f  // Practical Title
                    2 -> 1.6f  // Date
                    3 -> 1.3f  // Page
                    else -> 1.0f
                }
            }
            numCols == 3 -> {
                when (cIdx) {
                    0 -> 1.05f // S.No.
                    1 -> 5.2f  // Title
                    2 -> 1.35f // Page No.
                    else -> 1.0f
                }
            }
            cIdx == 0 && maxLen <= 6 -> 1.0f
            maxLen <= 5 -> 1.1f
            maxLen <= 12 -> 1.5f
            maxLen <= 20 -> 1.8f
            else -> 4.0f
        }
    }
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
    // In A4 print mode or fit-to-screen, standard tables with up to 6 columns fit across the paper without horizontal scrolling
    val needsHorizontalScroll = !fitToScreen && numCols > 6
    val colWeights = remember(table) { computeColumnWeights(table) }

    val tableCardModifier = if (table.hasBorders) {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(0.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(2.dp))
    } else {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    }

    Box(
        modifier = tableCardModifier
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
                    Row(
                        modifier = Modifier
                            .then(
                                if (needsHorizontalScroll) Modifier.widthIn(min = (numCols * 95).dp)
                                else Modifier.fillMaxWidth()
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.cells.forEachIndexed { cIdx, cell ->
                            val colWeight = colWeights.getOrElse(cIdx) { 1f }
                            val isHeaderCell = cell.isHeader

                            val cellBg = when {
                                cell.backgroundColorHex != null -> {
                                    try { Color(android.graphics.Color.parseColor(cell.backgroundColorHex)) }
                                    catch (e: Exception) { Color.Transparent }
                                }
                                isHeaderCell -> Color(0xFF1F497D)
                                else -> Color.Transparent
                            }

                            val cellFg = when {
                                cell.textColorHex != null -> {
                                    try { Color(android.graphics.Color.parseColor(cell.textColorHex)) }
                                    catch (e: Exception) { Color(0xFF1E293B) }
                                }
                                cellBg != Color.Transparent && ColorUtils.calculateLuminance(cellBg.toArgb()) < 0.45 -> {
                                    Color.White
                                }
                                else -> Color(0xFF1E293B)
                            }

                            val cellAlignment = when {
                                cell.alignment != TextAlignment.LEFT -> cell.alignment.toComposeTextAlign()
                                isHeaderCell -> TextAlign.Center
                                cIdx == 0 -> TextAlign.Center
                                cIdx >= 2 && numCols >= 4 -> TextAlign.Center
                                else -> TextAlign.Start
                            }

                            val cellWeight = when {
                                isHeaderCell || cell.isBold || (cIdx == 0 && table.hasBorders) -> FontWeight.Bold
                                cIdx == 3 && numCols >= 4 -> FontWeight.SemiBold
                                else -> FontWeight.Normal
                            }

                            val cellBorderModifier = if (table.hasBorders) Modifier.border(0.5.dp, Color(0xFFCBD5E1)) else Modifier

                            val cellModifier = if (needsHorizontalScroll) {
                                val minW = when {
                                    colWeight <= 1.0f -> 45.dp
                                    colWeight <= 2.0f -> 75.dp
                                    else -> 180.dp
                                }
                                Modifier
                                    .widthIn(min = minW, max = 260.dp)
                                    .then(cellBorderModifier)
                                    .background(cellBg)
                            } else {
                                Modifier
                                    .weight(colWeight)
                                    .then(cellBorderModifier)
                                    .background(cellBg)
                            }

                            val cellPadding = if (numCols >= 5) {
                                PaddingValues(horizontal = 1.dp, vertical = if (isHeaderCell) 5.dp else 3.5.dp)
                            } else if (numCols >= 4) {
                                PaddingValues(horizontal = 2.dp, vertical = if (isHeaderCell) 6.dp else 4.dp)
                            } else {
                                PaddingValues(horizontal = 4.dp, vertical = if (isHeaderCell) 8.dp else 5.dp)
                            }

                            val cellFontSize = if (numCols >= 5) 9.5.sp else if (numCols >= 4) 10.5.sp else 11.5.sp
                            val cellLineHeight = if (numCols >= 5) 12.sp else if (numCols >= 4) 14.sp else 15.sp

                            Box(
                                modifier = cellModifier
                                    .then(
                                        if (isEditing && onCellClick != null) {
                                            Modifier.clickable { onCellClick(rIdx, cIdx, cell.text) }
                                        } else Modifier
                                    )
                                    .padding(cellPadding),
                                contentAlignment = when (cellAlignment) {
                                    TextAlign.Center -> Alignment.Center
                                    TextAlign.Right, TextAlign.End -> Alignment.CenterEnd
                                    else -> Alignment.CenterStart
                                }
                            ) {
                                if (cell.text.contains("\n") && !table.hasBorders) {
                                    val lines = cell.text.split("\n")
                                    val annotatedCellText = buildAnnotatedString {
                                        lines.forEachIndexed { lineIdx, line ->
                                            if (lineIdx > 0) append("\n")
                                            val isSubmittedLabel = line.trim().startsWith("SUBMITTED", ignoreCase = true)
                                            val isPersonName = lineIdx == 1 && lines[0].trim().startsWith("SUBMITTED", ignoreCase = true)
                                            val lineStyle = SpanStyle(
                                                fontWeight = if (isSubmittedLabel || isPersonName) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSubmittedLabel) Color(0xFF1F497D) else Color(0xFF1E293B),
                                                fontSize = if (isSubmittedLabel) 11.5.sp else 11.sp
                                            )
                                            withStyle(lineStyle) {
                                                append(line)
                                            }
                                        }
                                    }
                                    Text(
                                        text = annotatedCellText,
                                        modifier = Modifier.fillMaxWidth(),
                                        lineHeight = 15.sp
                                    )
                                } else {
                                    Text(
                                        text = cell.text.ifEmpty { if (isEditing) "—" else "" },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = cellWeight,
                                            fontSize = if (isHeaderCell) (cellFontSize.value + 0.5f).sp else cellFontSize,
                                            lineHeight = cellLineHeight
                                        ),
                                        color = if (cell.text.isEmpty() && isEditing) {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        } else {
                                            cellFg
                                        },
                                        textAlign = cellAlignment,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        if (isEditing && onDeleteRow != null && table.rows.size > 1 && rIdx > 0) {
                            IconButton(
                                onClick = { onDeleteRow(rIdx) },
                                modifier = Modifier.size(24.dp).padding(2.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Delete Row",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            if (needsHorizontalScroll) {
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
                        Text("Add Row", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
    val context = LocalContext.current
    var isImageError by remember { mutableStateOf(false) }

    val fileModel = remember(element.imageUri) {
        try {
            val uri = Uri.parse(element.imageUri)
            if (uri.scheme == "file") {
                val f = File(uri.path ?: "")
                if (f.exists()) f else uri
            } else {
                uri
            }
        } catch (t: Throwable) {
            element.imageUri
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isImageError) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(130.dp)
                    .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Image Placeholder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Embedded Image",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val maxW = (element.widthDp.takeIf { it in 60..380 } ?: 260).dp
            val maxH = (element.heightDp.takeIf { it in 40..420 } ?: 260).dp
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(fileModel)
                    .crossfade(true)
                    .build(),
                contentDescription = element.caption ?: "Embedded Document Image",
                onError = { isImageError = true },
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .sizeIn(maxWidth = maxW, maxHeight = maxH),
                contentScale = ContentScale.Fit
            )
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

internal fun paginateDocumentElements(bodyElements: List<WordBodyElement>): List<DocumentPage> {
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
    val maxWeightPerPage = 230 // Calibrated to Word's authentic A4/Letter page height (52 pages for DSA manual)

    for (elemIndex in bodyElements.indices) {
        val element = bodyElements[elemIndex]
        val currentPIndex = if (element is WordBodyElement.ParagraphElement) pIndex++ else -1

        // Explicit Page Break element: ends current page and begins next page
        if (element is WordBodyElement.PageBreakElement) {
            if (currentElements.isNotEmpty()) {
                pages.add(DocumentPage(pageCounter++, currentElements.toList()))
                currentElements.clear()
                accumulatedWeight = 0
            }
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

        // Weight estimation with realistic line heights
        val elemWeight = when (element) {
            is WordBodyElement.ParagraphElement -> {
                val txt = element.paragraph.getPlainText().trim()
                if (txt.isEmpty()) {
                    8 // Compact empty spacer paragraph
                } else {
                    val words = element.paragraph.runs.sumOf { it.text.split("\\s+".toRegex()).count { w -> w.isNotBlank() } }
                    val lines = element.paragraph.runs.sumOf { it.text.count { c -> c == '\n' } } + 1
                    val maxFontSize = element.paragraph.runs.maxOfOrNull { it.style.fontSizeSp } ?: 16f
                    val isHeading = element.paragraph.isHeader || element.paragraph.headerLevel > 0 || maxFontSize >= 18f
                    val headingWeight = if (isHeading) 18 else 0
                    (lines * 14) + (words * 0.15f).toInt() + headingWeight
                }
            }
            is WordBodyElement.TableElement -> {
                if (!element.table.hasBorders) {
                    (element.table.rows.size * 18).coerceIn(40, 90)
                } else {
                    (element.table.rows.size * 20).coerceAtLeast(35)
                }
            }
            is WordBodyElement.ImageElement -> 130
            is WordBodyElement.PageBreakElement -> 0
        }

        // Keep-with-next heuristic: If current page only contains a heading or "INDEX", don't break before the table/content
        val onlyHasHeadings = currentElements.isNotEmpty() && currentElements.all {
            val elem = it.element
            elem is WordBodyElement.ParagraphElement && (
                elem.paragraph.isHeader ||
                elem.paragraph.headerLevel > 0 ||
                elem.paragraph.getPlainText().trim().lowercase() in listOf("index", "table of contents", "contents", "practical file")
            )
        }

        // Natural page break if capacity reached and page already has substantial content
        if (currentElements.isNotEmpty() && !onlyHasHeadings && (accumulatedWeight + elemWeight > maxWeightPerPage)) {
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
