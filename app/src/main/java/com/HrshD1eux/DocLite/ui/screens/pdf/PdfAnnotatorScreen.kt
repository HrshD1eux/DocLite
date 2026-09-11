package com.HrshD1eux.DocLite.ui.screens.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.HrshD1eux.DocLite.models.AnnotationType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfAnnotatorScreen(
    viewModel: PdfViewModel,
    fileUri: Uri,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Chrome & Navigation Controls
    var controlsVisible by remember { mutableStateOf(true) }
    var showStickyNoteDialog by remember { mutableStateOf(false) }
    var showJumpToPageDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    // Lazy list and current page detection
    val listState = rememberLazyListState()
    val visiblePageIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    // Floating Page Pill auto-hide timer
    var isPillVisible by remember { mutableStateOf(false) }
    val isScrolling = listState.isScrollInProgress

    LaunchedEffect(isScrolling, visiblePageIndex, controlsVisible) {
        if (isScrolling || controlsVisible) {
            isPillVisible = true
        } else {
            delay(1800)
            isPillVisible = false
        }
    }

    // Zoom & Pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Document loading
    LaunchedEffect(fileUri) {
        viewModel.openPdf(fileUri)
    }

    // Snackbar notifications
    LaunchedEffect((state as? PdfUiState.Success)?.statusMessage) {
        val msg = (state as? PdfUiState.Success)?.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Sync currentPageIndex in ViewModel as user scrolls
    LaunchedEffect(visiblePageIndex) {
        viewModel.goToPage(visiblePageIndex)
    }

    // Viewer Canvas Theme: Google Drive style dark/neutral viewer canvas
    val isDark = isSystemInDarkTheme()
    val canvasBgColor = if (isDark) Color(0xFF1E1F22) else Color(0xFFE9ECF0)

    // Sticky Note Dialog
    if (showStickyNoteDialog) {
        AlertDialog(
            onDismissRequest = { showStickyNoteDialog = false },
            title = { Text("Add Sticky Note to Page ${visiblePageIndex + 1}") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note content") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sticky_note_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (noteText.isNotBlank()) {
                            viewModel.addStickyNoteAnnotation(visiblePageIndex, noteText)
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

    // Jump to Page Dialog
    if (showJumpToPageDialog && state is PdfUiState.Success) {
        val totalPages = (state as PdfUiState.Success).pageCount
        var targetPageText by remember { mutableStateOf((visiblePageIndex + 1).toString()) }
        var sliderValue by remember { mutableFloatStateOf((visiblePageIndex + 1).toFloat()) }

        AlertDialog(
            onDismissRequest = { showJumpToPageDialog = false },
            title = { Text("Jump to Page") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Page ${sliderValue.toInt()} of $totalPages",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            sliderValue = it
                            targetPageText = it.toInt().toString()
                        },
                        valueRange = 1f..totalPages.toFloat().coerceAtLeast(1f),
                        steps = (totalPages - 2).coerceAtLeast(0)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetPageText,
                        onValueChange = { input ->
                            targetPageText = input.filter { it.isDigit() }
                            targetPageText.toIntOrNull()?.let { p ->
                                sliderValue = p.coerceIn(1, totalPages).toFloat()
                            }
                        },
                        label = { Text("Page Number (1 - $totalPages)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val p = targetPageText.toIntOrNull()?.coerceIn(1, totalPages) ?: (visiblePageIndex + 1)
                        scope.launch {
                            listState.animateScrollToItem((p - 1).coerceAtLeast(0))
                        }
                        showJumpToPageDialog = false
                    }
                ) {
                    Text("Jump")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpToPageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(canvasBgColor)
    ) {
        when (val uiState = state) {
            is PdfUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            is PdfUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            is PdfUiState.Success -> {
                // Main PDF Page Viewport Canvas with Pinch-to-Zoom & Pan
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Double-tap zoom and single-tap controls toggle
                        .pointerInput(uiState.isAnnotationMode) {
                            detectTapGestures(
                                onTap = {
                                    if (!uiState.isAnnotationMode && !uiState.isSearchActive) {
                                        controlsVisible = !controlsVisible
                                    }
                                },
                                onDoubleTap = { tapOffset ->
                                    if (scale > 1.1f) {
                                        scale = 1f
                                        panOffset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                        val centerX = size.width / 2f
                                        val centerY = size.height / 2f
                                        val targetX = (centerX - tapOffset.x) * 1.5f
                                        val targetY = (centerY - tapOffset.y) * 1.5f
                                        panOffset = Offset(targetX, targetY)
                                    }
                                }
                            )
                        }
                        // Multi-touch pinch zoom & pan
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val touchCount = event.changes.size

                                    // Intercept if multi-finger pinch or already zoomed
                                    if (touchCount > 1 || scale > 1.05f) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()

                                        if (zoomChange != 1f || panChange != Offset.Zero) {
                                            val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                                            val maxX = (newScale - 1f) * size.width / 2f
                                            val maxY = (newScale - 1f) * size.height / 2f

                                            panOffset = if (newScale > 1f) {
                                                Offset(
                                                    x = (panOffset.x + panChange.x).coerceIn(-maxX, maxX),
                                                    y = (panOffset.y + panChange.y).coerceIn(-maxY, maxY)
                                                )
                                            } else {
                                                Offset.Zero
                                            }
                                            scale = newScale

                                            // Consume to prevent conflicting scroll
                                            event.changes.forEach {
                                                if (it.positionChanged()) it.consume()
                                            }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = panOffset.x
                            translationY = panOffset.y
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                    ) {
                        // Top spacing so content is visible below top bar
                        item {
                            Spacer(modifier = Modifier.height(72.dp))
                        }

                        items(uiState.pageCount) { pageIndex ->
                            val aspectRatioState = produceState(initialValue = 1.414f, pageIndex) {
                                value = viewModel.getPageAspectRatio(pageIndex)
                            }
                            val bitmapState = produceState<Bitmap?>(initialValue = null, pageIndex) {
                                value = viewModel.getPage(pageIndex)
                            }

                            // Google Drive style clean page sheet with subtle shadow
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .shadow(elevation = 3.dp, shape = RoundedCornerShape(2.dp)),
                                shape = RoundedCornerShape(2.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(aspectRatioState.value),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val bmp = bitmapState.value
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "PDF Page ${pageIndex + 1}",
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Render Annotation Overlays on top of the rendered PDF page
                                        uiState.annotations.filter { it.pageIndex == pageIndex }.forEach { ann ->
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
                                                        .padding(12.dp),
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                                    shadowElevation = 4.dp
                                                ) {
                                                    Text(
                                                        text = ann.noteText,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        modifier = Modifier.padding(6.dp),
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            strokeWidth = 2.5.dp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }

                        // Bottom spacing for navigation/annotation bar
                        item {
                            Spacer(modifier = Modifier.height(84.dp))
                        }
                    }
                }

                // Floating Google Drive Page Pill ("3 / 15")
                AnimatedVisibility(
                    visible = isPillVisible && uiState.pageCount > 0 && !uiState.isSearchActive,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showJumpToPageDialog = true },
                        color = Color(0xDD202124),
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = "${visiblePageIndex + 1} / ${uiState.pageCount}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            ),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                // Top Chrome: Google Drive Top App Bar (Reading Mode or Search Mode)
                AnimatedVisibility(
                    visible = controlsVisible || uiState.isSearchActive,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    if (uiState.isSearchActive) {
                        // In-Document Search Top Bar
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { viewModel.toggleSearch(false) }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Close search"
                                    )
                                }

                                OutlinedTextField(
                                    value = uiState.searchQuery,
                                    onValueChange = { viewModel.performSearch(it) },
                                    placeholder = { Text("Find in document...") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        viewModel.performSearch(uiState.searchQuery)
                                    }),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedBorderColor = Color.Transparent
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                if (uiState.isSearching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .padding(4.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else if (uiState.searchQuery.isNotEmpty()) {
                                    Text(
                                        text = if (uiState.searchResults.isNotEmpty()) {
                                            "${uiState.currentMatchIndex + 1}/${uiState.searchResults.size}"
                                        } else {
                                            "0/0"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )

                                    IconButton(
                                        onClick = {
                                            val targetPage = viewModel.previousSearchMatch()
                                            if (targetPage != null) {
                                                scope.launch { listState.animateScrollToItem(targetPage) }
                                            }
                                        },
                                        enabled = uiState.searchResults.isNotEmpty()
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
                                    }

                                    IconButton(
                                        onClick = {
                                            val targetPage = viewModel.nextSearchMatch()
                                            if (targetPage != null) {
                                                scope.launch { listState.animateScrollToItem(targetPage) }
                                            }
                                        },
                                        enabled = uiState.searchResults.isNotEmpty()
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
                                    }

                                    IconButton(onClick = { viewModel.clearSearch() }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                                    }
                                }
                            }
                        }
                    } else {
                        // Standard Reading Top App Bar
                        TopAppBar(
                            title = {
                                val title = fileUri.lastPathSegment ?: "PDF Document"
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
                                // Search
                                IconButton(onClick = { viewModel.toggleSearch(true) }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search document")
                                }

                                // Share
                                IconButton(onClick = { sharePdf(context, fileUri) }) {
                                    Icon(Icons.Default.Share, contentDescription = "Share PDF")
                                }

                                // Annotate Toggle
                                IconButton(
                                    onClick = { viewModel.toggleAnnotationMode() },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (uiState.isAnnotationMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Draw,
                                        contentDescription = "Annotate",
                                        tint = if (uiState.isAnnotationMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                            )
                        )
                    }
                }

                // Bottom Chrome: Sleek Annotation Panel (Visible only when in Annotation Mode)
                AnimatedVisibility(
                    visible = uiState.isAnnotationMode && controlsVisible,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Highlight
                            IconButton(
                                onClick = { viewModel.addHighlightAnnotation(visiblePageIndex) },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (uiState.selectedAnnotationTool == AnnotationType.HIGHLIGHT) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            ) {
                                Icon(
                                    Icons.Default.Highlight,
                                    contentDescription = "Highlight",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Sticky Note
                            IconButton(
                                onClick = { showStickyNoteDialog = true },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (uiState.selectedAnnotationTool == AnnotationType.STICKY_NOTE) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Note,
                                    contentDescription = "Sticky Note",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Free Draw
                            IconButton(
                                onClick = { viewModel.selectTool(AnnotationType.FREE_DRAW) },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (uiState.selectedAnnotationTool == AnnotationType.FREE_DRAW) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            ) {
                                Icon(
                                    Icons.Default.Create,
                                    contentDescription = "Free Draw",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Close Annotation Mode
                            IconButton(
                                onClick = { viewModel.toggleAnnotationMode(false) }
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Done Annotating"
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

private fun sharePdf(context: Context, fileUri: Uri) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

