package com.HrshD1eux.DocLite.ui.screens.word

import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.HrshD1eux.DocLite.models.TextAlignment

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

    LaunchedEffect(fileUri) {
        viewModel.loadDocument(fileUri)
    }

    LaunchedEffect((state as? WordUiState.Success)?.saveStatus) {
        val status = (state as? WordUiState.Success)?.saveStatus
        if (status != null) {
            snackbarHostState.showSnackbar(status)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val docTitle = (state as? WordUiState.Success)?.document?.title ?: "Word Editor"
                    Text(
                        text = docTitle,
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
                    val successState = state as? WordUiState.Success
                    if (successState != null) {
                        IconButton(onClick = viewModel::toggleEditMode) {
                            Icon(
                                imageVector = if (successState.isEditing) Icons.Default.Visibility else Icons.Default.Edit,
                                contentDescription = if (successState.isEditing) "View Mode" else "Edit Mode"
                            )
                        }
                        IconButton(onClick = viewModel::saveDocument) {
                            Icon(Icons.Default.Save, contentDescription = "Save Document")
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
                                    Icon(Icons.Default.FormatAlignLeft, contentDescription = "Align Left")
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
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(uiState.document.paragraphs) { index, paragraph ->
                                if (uiState.isEditing) {
                                    OutlinedTextField(
                                        value = paragraph.getPlainText(),
                                        onValueChange = { newText -> viewModel.updateParagraphText(index, newText) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("word_p_input_$index"),
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
                                } else {
                                    Text(
                                        text = paragraph.getPlainText(),
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = paragraph.runs.firstOrNull()?.style?.fontSizeSp?.sp ?: 16.sp,
                                            fontWeight = if (paragraph.runs.firstOrNull()?.style?.isBold == true) FontWeight.Bold else if (paragraph.isHeader) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = paragraph.alignment.toComposeTextAlign()
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            if (uiState.isEditing) {
                                item {
                                    IconButton(
                                        onClick = viewModel::addParagraph,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Paragraph")
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

