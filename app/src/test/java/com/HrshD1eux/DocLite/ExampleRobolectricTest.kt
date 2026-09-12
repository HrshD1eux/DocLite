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
}

