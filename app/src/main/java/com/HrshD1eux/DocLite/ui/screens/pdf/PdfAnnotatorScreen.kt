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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomOut
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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.HrshD1eux.DocLite.models.AnnotationType
import com.HrshD1eux.DocLite.models.DocumentFile
import com.HrshD1eux.DocLite.models.DocumentFormat
import com.HrshD1eux.DocLite.models.DrawingPoint
import com.HrshD1eux.DocLite.models.PdfAnnotation
import com.HrshD1eux.DocLite.ui.components.PasswordPromptDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    var selectedNoteForInspection by remember { mutableStateOf<PdfAnnotation?>(null) }

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

    // Viewer Canvas Theme
    val isDark = isSystemInDarkTheme()
    val canvasBgColor = if (isDark) Color(0xFF1E1F22) else Color(0xFFE9ECF0)

    // Password Required Prompt Dialog
    val passwordState = state as? PdfUiState.PasswordRequired
    if (passwordState != null) {
        val dummyFile = remember(passwordState.uri) {
            DocumentFile(
                id = passwordState.uri.toString(),
                name = passwordState.uri.lastPathSegment ?: "Protected Document",
                path = passwordState.uri.path ?: "",
                uriString = passwordState.uri.toString(),
                sizeBytes = 0,
                lastModified = System.currentTimeMillis(),
                format = DocumentFormat.PDF,
                isPasswordProtected = true
            )
        }
        PasswordPromptDialog(
            file = dummyFile,
            onDismiss = onBack,
            onUnlock = { password ->
                viewModel.openPdf(passwordState.uri, password)
            },
            errorMessage = passwordState.errorMessage
        )
    }

    // Inspect / Delete Sticky Note Dialog
    if (selectedNoteForInspection != null) {
        val note = selectedNoteForInspection!!
        AlertDialog(
            onDismissRequest = { selectedNoteForInspection = null },
            title = { Text("Sticky Note") },
            text = { Text(note.noteText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAnnotation(note.id)
                        selectedNoteForInspection = null
                    }
                ) {
                    Text("Delete Note", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedNoteForInspection = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Add Sticky Note Dialog
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

            is PdfUiState.PasswordRequired -> {
                // Password prompt is active in dialog above
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            is PdfUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = onBack) {
                            Text("Go Back")
                        }
                    }
                }
            }

            is PdfUiState.Success -> {
                val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                // Main PDF Page Viewport Canvas with non-locking Pinch-to-Zoom & Horizontal Pan
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Double-tap zoom and single-tap controls toggle
                        .pointerInput(uiState.isAnnotationMode, uiState.isSearchActive) {
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
                                        scale = 2.2f
                                        val centerX = size.width / 2f
                                        val targetX = (centerX - tapOffset.x) * 1.2f
                                        panOffset = Offset(targetX, 0f)
                                    }
                                }
                            )
                        }
                        // Multi-touch pinch zoom & horizontal pan without blocking vertical scroll
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val touchCount = event.changes.size

                                    // Two-finger gesture: pinch zoom and two-finger pan
                                    if (touchCount > 1) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()

                                        if (zoomChange != 1f || panChange != Offset.Zero) {
                                            val newScale = (scale * zoomChange).coerceIn(1f, 3.5f)
                                            if (newScale <= 1.05f) {
                                                scale = 1f
                                                panOffset = Offset.Zero
                                            } else {
                                                val maxX = (newScale - 1f) * size.width / 2f
                                                panOffset = Offset(
                                                    (panOffset.x + panChange.x).coerceIn(-maxX, maxX),
                                                    0f
                                                )
                                                scale = newScale
                                            }

                                            event.changes.forEach {
                                                if (it.positionChanged()) it.consume()
                                            }
                                        }
                                    } else if (scale > 1.05f && touchCount == 1) {
                                        // Single finger when zoomed in: allow horizontal pan for wide pages
                                        // only consume if dragging horizontally so vertical scrolling works!
                                        val panChange = event.calculatePan()
                                        if (abs(panChange.x) > abs(panChange.y) * 1.3f && panChange.x != 0f) {
                                            val maxX = (scale - 1f) * size.width / 2f
                                            panOffset = Offset(
                                                (panOffset.x + panChange.x).coerceIn(-maxX, maxX),
                                                0f
                                            )
                                            event.changes.forEach {
                                                if (it.positionChanged()) it.consume()
                                            }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })

                                if (scale <= 1.05f) {
                                    scale = 1f
                                    panOffset = Offset.Zero
                                }
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = panOffset.x
                            translationY = 0f
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
                        contentPadding = PaddingValues(
                            top = statusBarTop + if (controlsVisible) 60.dp else 8.dp,
                            bottom = navBarBottom + if (controlsVisible) 76.dp else 16.dp
                        )
                    ) {
                        items(uiState.pageCount) { pageIndex ->
                            val aspectRatioState = produceState(initialValue = 595f / 842f, pageIndex) {
                                value = viewModel.getPageAspectRatio(pageIndex)
                            }
                            val bitmapState = produceState<Bitmap?>(initialValue = null, pageIndex) {
                                value = viewModel.getPage(pageIndex)
                            }
                            val bmp = bitmapState.value
                            val effectiveRatio = bmp?.let { it.width.toFloat() / it.height.toFloat() } ?: aspectRatioState.value

                            // Active freehand live stroke points for this page
                            val activeLiveStroke = remember { mutableStateListOf<DrawingPoint>() }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(2.dp)),
                                shape = RoundedCornerShape(2.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(effectiveRatio),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val containerWidth = maxWidth
                                    val containerHeight = maxHeight

                                    if (bmp != null) {
                                        // Remember ImageBitmap to prevent continuous object allocations
                                        val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
                                        Image(
                                            bitmap = imageBitmap,
                                            contentDescription = "PDF Page ${pageIndex + 1}",
                                            contentScale = ContentScale.FillBounds,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Render Persisted Annotations for this page
                                        val pageAnnotations = uiState.annotations.filter { it.pageIndex == pageIndex }

                                        // 1. Highlighting
                                        pageAnnotations.filter { it.type == AnnotationType.HIGHLIGHT }.forEach { ann ->
                                            val leftOffset = containerWidth * ann.boundsLeftRatio
                                            val topOffset = containerHeight * ann.boundsTopRatio
                                            val highlightWidth = containerWidth * ann.boundsWidthRatio
                                            val highlightHeight = containerHeight * ann.boundsHeightRatio

                                            Box(
                                                modifier = Modifier
                                                    .offset(x = leftOffset, y = topOffset)
                                                    .size(width = highlightWidth, height = highlightHeight)
                                                    .background(
                                                        ann.getComposeColor().copy(alpha = 0.4f),
                                                        RoundedCornerShape(2.dp)
                                                    )
                                            )
                                        }

                                        // 2. Free Draw Paths
                                        val freeDrawAnnotations = pageAnnotations.filter { it.type == AnnotationType.FREE_DRAW && it.points.size >= 2 }
                                        if (freeDrawAnnotations.isNotEmpty() || activeLiveStroke.size >= 2) {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                // Draw saved strokes
                                                for (ann in freeDrawAnnotations) {
                                                    val path = Path()
                                                    val first = ann.points.first()
                                                    path.moveTo(first.xRatio * size.width, first.yRatio * size.height)
                                                    for (pt in ann.points.drop(1)) {
                                                        path.lineTo(pt.xRatio * size.width, pt.yRatio * size.height)
                                                    }
                                                    drawPath(
                                                        path = path,
                                                        color = ann.getComposeColor(),
                                                        style = Stroke(
                                                            width = ann.strokeWidthDp.dp.toPx(),
                                                            cap = StrokeCap.Round,
                                                            join = StrokeJoin.Round
                                                        )
                                                    )
                                                }

                                                // Draw live in-progress stroke
                                                if (activeLiveStroke.size >= 2) {
                                                    val livePath = Path()
                                                    val first = activeLiveStroke.first()
                                                    livePath.moveTo(first.xRatio * size.width, first.yRatio * size.height)
                                                    for (pt in activeLiveStroke.drop(1)) {
                                                        livePath.lineTo(pt.xRatio * size.width, pt.yRatio * size.height)
                                                    }
                                                    drawPath(
                                                        path = livePath,
                                                        color = Color(0xFFF44336),
                                                        style = Stroke(
                                                            width = 3.dp.toPx(),
                                                            cap = StrokeCap.Round,
                                                            join = StrokeJoin.Round
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        // 3. Sticky Notes
                                        pageAnnotations.filter { it.type == AnnotationType.STICKY_NOTE }.forEach { ann ->
                                            val leftOffset = containerWidth * ann.boundsLeftRatio
                                            val topOffset = containerHeight * ann.boundsTopRatio

                                            Surface(
                                                modifier = Modifier
                                                    .offset(x = leftOffset, y = topOffset)
                                                    .clickable { selectedNoteForInspection = ann },
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                shadowElevation = 4.dp
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.Note,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.size(4.dp))
                                                    Text(
                                                        text = ann.noteText,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                                    )
                                                }
                                            }
                                        }

                                        // 4. Interactive Live Drawing Overlay when Free Draw mode is active
                                        if (uiState.isAnnotationMode && uiState.selectedAnnotationTool == AnnotationType.FREE_DRAW) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .pointerInput(pageIndex) {
                                                        detectDragGestures(
                                                            onDragStart = { offset ->
                                                                activeLiveStroke.clear()
                                                                activeLiveStroke.add(
                                                                    DrawingPoint(
                                                                        xRatio = (offset.x / size.width).coerceIn(0f, 1f),
                                                                        yRatio = (offset.y / size.height).coerceIn(0f, 1f)
                                                                    )
                                                                )
                                                            },
                                                            onDrag = { change, _ ->
                                                                change.consume()
                                                                val pos = change.position
                                                                activeLiveStroke.add(
                                                                    DrawingPoint(
                                                                        xRatio = (pos.x / size.width).coerceIn(0f, 1f),
                                                                        yRatio = (pos.y / size.height).coerceIn(0f, 1f)
                                                                    )
                                                                )
                                                            },
                                                            onDragEnd = {
                                                                if (activeLiveStroke.size >= 2) {
                                                                    viewModel.addFreeDrawAnnotation(
                                                                        pageIndex = pageIndex,
                                                                        points = activeLiveStroke.toList()
                                                                    )
                                                                }
                                                                activeLiveStroke.clear()
                                                            },
                                                            onDragCancel = {
                                                                activeLiveStroke.clear()
                                                            }
                                                        )
                                                    }
                                            )
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
                    }
                }

                // Reset Zoom Floating Button (Visible when zoomed in)
                if (scale > 1.05f) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = navBarBottom + if (uiState.isAnnotationMode) 80.dp else 24.dp, end = 16.dp)
                            .clip(CircleShape)
                            .clickable {
                                scale = 1f
                                panOffset = Offset.Zero
                            },
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.ZoomOut,
                                contentDescription = "Reset Zoom",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(
                                text = "1.0x",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
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
                        .padding(top = statusBarTop + 68.dp)
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
                            modifier = Modifier.fillMaxWidth().statusBarsPadding(),
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

                                // Share (Flattens annotations into exported PDF if present)
                                IconButton(onClick = {
                                    viewModel.prepareSharePdf { exportUri ->
                                        sharePdf(context, exportUri)
                                    }
                                }) {
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

                // Bottom Chrome: Sleek Annotation Panel
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
                                onClick = {
                                    viewModel.selectTool(AnnotationType.HIGHLIGHT)
                                    viewModel.addHighlightAnnotation(visiblePageIndex)
                                },
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
                                onClick = {
                                    viewModel.selectTool(AnnotationType.STICKY_NOTE)
                                    showStickyNoteDialog = true
                                },
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

                            // Free Draw (activates interactive canvas)
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
