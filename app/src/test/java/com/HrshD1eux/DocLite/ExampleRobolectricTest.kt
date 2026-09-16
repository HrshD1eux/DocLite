package com.HrshD1eux.DocLite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("DocLite", appName)
  }

  @Test
  fun `test MainActivity launch without crash`() {
    val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
    scenario.onActivity { activity ->
      org.junit.Assert.assertNotNull(activity)
    }
    scenario.close()
  }

  @Test
  fun `test DocumentFormat extension mapping`() {
    assertEquals(com.HrshD1eux.DocLite.models.DocumentFormat.TXT, com.HrshD1eux.DocLite.models.DocumentFormat.fromExtension("txt"))
    assertEquals(com.HrshD1eux.DocLite.models.DocumentFormat.PDF, com.HrshD1eux.DocLite.models.DocumentFormat.fromExtension("pdf"))
    assertEquals(com.HrshD1eux.DocLite.models.DocumentFormat.WORD, com.HrshD1eux.DocLite.models.DocumentFormat.fromExtension("docx"))
    assertEquals(com.HrshD1eux.DocLite.models.DocumentFormat.EXCEL, com.HrshD1eux.DocLite.models.DocumentFormat.fromExtension("xlsx"))
    assertEquals(com.HrshD1eux.DocLite.models.DocumentFormat.POWERPOINT, com.HrshD1eux.DocLite.models.DocumentFormat.fromExtension("pptx"))
  }

  @Test
  fun `test default A4 portrait aspect ratio is valid for Compose`() {
    val defaultRatio = 595f / 842f
    org.junit.Assert.assertTrue(defaultRatio in 0.70f..0.71f)
  }

  @Test
  fun `test UpdateManager semantic version comparison`() {
    org.junit.Assert.assertTrue(
      com.HrshD1eux.DocLite.core.update.UpdateManager.isNewerVersion("1.0.1", "v1.0.2")
    )
    org.junit.Assert.assertTrue(
      com.HrshD1eux.DocLite.core.update.UpdateManager.isNewerVersion("v1.0.9", "1.1.0")
    )
    org.junit.Assert.assertTrue(
      com.HrshD1eux.DocLite.core.update.UpdateManager.isNewerVersion("1.9.9", "2.0.0")
    )
    org.junit.Assert.assertFalse(
      com.HrshD1eux.DocLite.core.update.UpdateManager.isNewerVersion("1.0.2", "1.0.2")
    )
    org.junit.Assert.assertFalse(
      com.HrshD1eux.DocLite.core.update.UpdateManager.isNewerVersion("v1.0.3", "v1.0.2")
    )
  }

  @Test
  fun `test WordDocument model with tables and sequential body elements`() {
    val cells = listOf(
      com.HrshD1eux.DocLite.models.WordTableCell("Header 1", isHeader = true),
      com.HrshD1eux.DocLite.models.WordTableCell("Header 2", isHeader = true)
    )
    val row = com.HrshD1eux.DocLite.models.WordTableRow(cells)
    val table = com.HrshD1eux.DocLite.models.WordTable(rows = listOf(row))
    val para = com.HrshD1eux.DocLite.models.Paragraph(
      runs = listOf(com.HrshD1eux.DocLite.models.TextRun("Hello Word")),
      bulletPrefix = "• "
    )
    val doc = com.HrshD1eux.DocLite.models.WordDocument(
      title = "test.docx",
      fileUri = "content://test.docx",
      paragraphs = listOf(para),
      tables = listOf(table),
      bodyElements = listOf(
        com.HrshD1eux.DocLite.models.WordBodyElement.ParagraphElement(para),
        com.HrshD1eux.DocLite.models.WordBodyElement.TableElement(table)
      )
    )

    assertEquals(1, doc.tables.size)
    assertEquals(2, doc.bodyElements.size)
    assertEquals("• Hello Word", para.getPlainText())
  }
}

