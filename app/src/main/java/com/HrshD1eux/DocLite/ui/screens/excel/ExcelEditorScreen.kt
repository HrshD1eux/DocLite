package com.HrshD1eux.DocLite.ui.screens.excel

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Save
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
                        IconButton(onClick = viewModel::saveSpreadsheet) {
                            Icon(Icons.Default.Save, contentDescription = "Save Spreadsheet")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        when (val uiState = state) {
            is ExcelUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is ExcelUiState.Error -> {
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

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
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
                                listOf("SUM", "AVERAGE", "MIN", "MAX", "COUNT").forEach { fn ->
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

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = viewModel::insertRow) {
                                Icon(Icons.Default.Add, contentDescription = "Add Row", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = viewModel::deleteRow) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Row", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    // Grid View Sheet Canvas
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
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

                                        for (c in 0 until activeSheet.colCount) {
                                            Box(
                                                modifier = Modifier
                                                    .width(100.dp)
                                                    .height(32.dp)
                                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = Sheet.colIndexToName(c),
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                }

                                // Data Rows (1, 2, 3...)
                                items(activeSheet.rowCount) { r ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Row Index Header
                                        Box(
                                            modifier = Modifier
                                                .width(44.dp)
                                                .height(38.dp)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${r + 1}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        for (c in 0 until activeSheet.colCount) {
                                            val isSelected = r == uiState.selectedRow && c == uiState.selectedCol
                                            val cell = activeSheet.getCell(r, c)

                                            Box(
                                                modifier = Modifier
                                                    .width(100.dp)
                                                    .height(38.dp)
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

