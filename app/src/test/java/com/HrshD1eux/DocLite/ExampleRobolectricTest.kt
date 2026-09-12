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
}

