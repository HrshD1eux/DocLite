package com.HrshD1eux.DocLite.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.HrshD1eux.DocLite.models.DocumentFile
import com.HrshD1eux.DocLite.models.DocumentFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileCardItem(
    file: DocumentFile,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
    onProtectClick: () -> Unit,
    onInfoClick: (() -> Unit)? = null,
    onAnalyzeStatement: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectToggle: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val cardBorder = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, Color(0xFFEDF1F1))
    }

    val cardColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        Color.White
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onSelectToggle?.invoke()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    if (onLongClick != null) {
                        onLongClick()
                    } else {
                        onSelectToggle?.invoke()
                    }
                }
            )
            .testTag("file_card_${file.name}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle?.invoke() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        checkmarkColor = Color.White
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            FormatIconBadge(format = file.format)

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color(0xFF191C1D),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (file.isPasswordProtected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Password Protected",
                            tint = Color(0xFF006874),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Modified ${formatDate(file.lastModified)} • ${file.formattedSize}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Color(0xFF70787C)
                )
            }

            if (!isSelectionMode) {
                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        imageVector = if (file.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (file.isFavorite) Color(0xFF006874) else Color(0xFF70787C)
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color(0xFF40484B)
                        )
                    }

                    FileActionMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        file = file,
                        onRenameClick = onRenameClick,
                        onDeleteClick = onDeleteClick,
                        onShareClick = onShareClick,
                        onProtectClick = onProtectClick,
                        onInfoClick = onInfoClick,
                        onAnalyzeStatement = onAnalyzeStatement
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    file: DocumentFile,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
    onProtectClick: () -> Unit,
    onInfoClick: (() -> Unit)? = null,
    onAnalyzeStatement: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectToggle: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val cardBorder = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, Color(0xFFEDF1F1))
    }

    val cardColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        Color.White
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onSelectToggle?.invoke() else onClick()
                },
                onLongClick = {
                    if (onLongClick != null) onLongClick() else onSelectToggle?.invoke()
                }
            )
            .testTag("file_grid_${file.name}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectToggle?.invoke() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            checkmarkColor = Color.White
                        )
                    )
                } else {
                    FormatIconBadge(format = file.format)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isSelectionMode) {
                        IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = if (file.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (file.isFavorite) Color(0xFF006874) else Color(0xFF70787C),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box {
                            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = Color(0xFF40484B),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            FileActionMenu(
                                expanded = showMenu,
                                onDismiss = { showMenu = false },
                                file = file,
                                onRenameClick = onRenameClick,
                                onDeleteClick = onDeleteClick,
                                onShareClick = onShareClick,
                                onProtectClick = onProtectClick,
                                onInfoClick = onInfoClick,
                                onAnalyzeStatement = onAnalyzeStatement
                            )
                        }
                    }
                }
            }

            if (isSelectionMode) {
                Spacer(modifier = Modifier.height(8.dp))
                FormatIconBadge(format = file.format)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF191C1D),
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (file.isPasswordProtected) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Password Protected",
                        tint = Color(0xFF006874),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${file.formattedSize} • ${formatDate(file.lastModified)}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = Color(0xFF70787C),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FileActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    file: DocumentFile,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
    onProtectClick: () -> Unit,
    onInfoClick: (() -> Unit)?,
    onAnalyzeStatement: (() -> Unit)?
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        if (onInfoClick != null) {
            DropdownMenuItem(
                text = { Text("Info") },
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    onDismiss()
                    onInfoClick()
                }
            )
        }
        DropdownMenuItem(
            text = { Text("Rename") },
            onClick = {
                onDismiss()
                onRenameClick()
            }
        )
        DropdownMenuItem(
            text = { Text("Delete") },
            onClick = {
                onDismiss()
                onDeleteClick()
            }
        )
        DropdownMenuItem(
            text = { Text("Share") },
            onClick = {
                onDismiss()
                onShareClick()
            }
        )
        DropdownMenuItem(
            text = { Text(if (file.isPasswordProtected) "Change/Remove Password" else "Protect with Password") },
            onClick = {
                onDismiss()
                onProtectClick()
            }
        )
        if (onAnalyzeStatement != null && (file.format == DocumentFormat.EXCEL || file.format == DocumentFormat.PDF || file.name.endsWith(".csv", ignoreCase = true))) {
            DropdownMenuItem(
                text = { Text("Analyze Bank Statement") },
                onClick = {
                    onDismiss()
                    onAnalyzeStatement()
                }
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        "Recently"
    }
}
