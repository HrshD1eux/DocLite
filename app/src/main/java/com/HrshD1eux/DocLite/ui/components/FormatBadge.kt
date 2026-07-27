package com.HrshD1eux.DocLite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.HrshD1eux.DocLite.models.DocumentFormat
import com.HrshD1eux.DocLite.ui.theme.ExcelBg
import com.HrshD1eux.DocLite.ui.theme.ExcelText
import com.HrshD1eux.DocLite.ui.theme.ImageBg
import com.HrshD1eux.DocLite.ui.theme.ImageText
import com.HrshD1eux.DocLite.ui.theme.PdfBg
import com.HrshD1eux.DocLite.ui.theme.PdfText
import com.HrshD1eux.DocLite.ui.theme.PptBg
import com.HrshD1eux.DocLite.ui.theme.PptText
import com.HrshD1eux.DocLite.ui.theme.WordBg
import com.HrshD1eux.DocLite.ui.theme.WordText

fun getFormatColors(format: DocumentFormat): Pair<Color, Color> {
    return when (format) {
        DocumentFormat.WORD -> WordBg to WordText
        DocumentFormat.EXCEL -> ExcelBg to ExcelText
        DocumentFormat.PDF -> PdfBg to PdfText
        DocumentFormat.POWERPOINT -> PptBg to PptText
        DocumentFormat.IMAGE -> ImageBg to ImageText
    }
}

fun getFormatBadgeText(format: DocumentFormat): String {
    return when (format) {
        DocumentFormat.WORD -> "DOCX"
        DocumentFormat.EXCEL -> "XLSX"
        DocumentFormat.PDF -> "PDF"
        DocumentFormat.POWERPOINT -> "PPTX"
        DocumentFormat.IMAGE -> "IMG"
    }
}

@Composable
fun FormatIconBadge(
    format: DocumentFormat,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = getFormatColors(format)
    val text = getFormatBadgeText(format)

    Box(
        modifier = modifier
            .size(48.dp)
            .background(color = bgColor, shape = RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
fun FormatBadge(
    format: DocumentFormat,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = getFormatColors(format)

    Box(
        modifier = modifier
            .background(
                color = bgColor,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = getFormatBadgeText(format),
            color = textColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        )
    }
}


