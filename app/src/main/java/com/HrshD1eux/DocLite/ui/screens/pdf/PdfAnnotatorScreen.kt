package com.HrshD1eux.DocLite.ui.screens.pdf

import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.HrshD1eux.DocLite.models.AnnotationType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfAnnotatorScreen(
    viewModel: PdfViewModel,
    fileUri: Uri,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showStickyNoteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    LaunchedEffect(fileUri) {
        viewModel.openPdf(fileUri)
    }

    LaunchedEffect((state as? PdfUiState.Success)?.statusMessage) {
        val msg = (state as? PdfUiState.Success)?.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (showStickyNoteDialog) {
        AlertDialog(
            onDismissRequest = { showStickyNoteDialog = false },
            title = { Text("Add Sticky Note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note content") },
                    modifier = Modifier.fillMaxWidth().testTag("sticky_note_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uiSuccess = state as? PdfUiState.Success
                        if (uiSuccess != null && noteText.isNotBlank()) {
                            viewModel.addStickyNoteAnnotation(uiSuccess.currentPageIndex, noteText)
                        }
                        showStickyNoteDialog = false
                        noteText = ""
                    }
                ) {
                    Text("Save Note")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStickyNoteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = fileUri.lastPathSegment ?: "PDF Document"
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
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        when (val uiState = state) {
            is PdfUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is PdfUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = uiState.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is PdfUiState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Annotation Toolbar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.addHighlightAnnotation(uiState.currentPageIndex) },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (uiState.selectedAnnotationTool == AnnotationType.HIGHLIGHT) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Icon(Icons.Default.Highlight, contentDescription = "Highlight", tint = MaterialTheme.colorScheme.primary)
                            }

                            IconButton(
                                onClick = { showStickyNoteDialog = true },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (uiState.selectedAnnotationTool == AnnotationType.STICKY_NOTE) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Icon(Icons.Default.Note, contentDescription = "Sticky Note", tint = MaterialTheme.colorScheme.primary)
                            }

                            IconButton(
                                onClick = { viewModel.selectTool(AnnotationType.FREE_DRAW) },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (uiState.selectedAnnotationTool == AnnotationType.FREE_DRAW) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Icon(Icons.Default.Create, contentDescription = "Free Draw", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // Main PDF Page Viewport Canvas
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.currentPageBitmap != null) {
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                            ) {
                                Box {
                                    Image(
                                        bitmap = uiState.currentPageBitmap.asImageBitmap(),
                                        contentDescription = "PDF Page ${uiState.currentPageIndex + 1}",
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Render Annotation Overlays on top of the rendered PDF page
                                    uiState.annotations.filter { it.pageIndex == uiState.currentPageIndex }.forEach { ann ->
                                        if (ann.type == AnnotationType.HIGHLIGHT) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(ann.getComposeColor().copy(alpha = 0.35f))
                                            )
                                        } else if (ann.type == AnnotationType.STICKY_NOTE) {
                                            Surface(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(16.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                shadowElevation = 4.dp
                                            ) {
                                                Text(
                                                    text = ann.noteText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.padding(8.dp),
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            CircularProgressIndicator()
                        }
                    }

                    // Page Bottom Navigation Bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.goToPage(uiState.currentPageIndex - 1) },
                                enabled = uiState.currentPageIndex > 0
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page")
                            }

                            Text(
                                text = "Page ${uiState.currentPageIndex + 1} of ${uiState.pageCount}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            IconButton(
                                onClick = { viewModel.goToPage(uiState.currentPageIndex + 1) },
                                enabled = uiState.currentPageIndex < uiState.pageCount - 1
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next Page")
                            }
                        }
                    }
                }
            }
        }
    }
}

