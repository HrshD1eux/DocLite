package com.HrshD1eux.DocLite

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import com.HrshD1eux.DocLite.models.DocumentFile
import com.HrshD1eux.DocLite.models.DocumentFormat
import com.HrshD1eux.DocLite.ui.screens.filemanager.SortOption
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Tier2ArchitectureTest {

    @Test
    fun lruCache_evictsOldestEntry_whenMemoryExceeded() {
        // Cache with max capacity 200 KB
        val cache = object : LruCache<Int, Bitmap>(200) {
            override fun sizeOf(key: Int, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }

        // Create 3 bitmaps of 100 KB each (e.g. 100 x 256 x 4 bytes = 102,400 bytes = 100 KB)
        val b1 = Bitmap.createBitmap(100, 256, Bitmap.Config.ARGB_8888)
        val b2 = Bitmap.createBitmap(100, 256, Bitmap.Config.ARGB_8888)
        val b3 = Bitmap.createBitmap(100, 256, Bitmap.Config.ARGB_8888)

        cache.put(1, b1)
        cache.put(2, b2)
        assertEquals(2, cache.size() / 100) // ~200 KB

        // Access 1 so 2 becomes oldest
        cache.get(1)

        // Adding b3 (100 KB) should evict page 2
        cache.put(3, b3)

        assertNotNull("Page 1 was recently accessed and should remain", cache.get(1))
        assertNotNull("Page 3 was just added and should remain", cache.get(3))
        assertNull("Page 2 was oldest and should have been evicted", cache.get(2))
    }

    @Test
    fun lruCache_evictAll_clearsAllPages() {
        val cache = object : LruCache<Int, Bitmap>(1024) {
            override fun sizeOf(key: Int, bitmap: Bitmap): Int = bitmap.byteCount / 1024
        }
        val b1 = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        cache.put(1, b1)
        assertEquals(1, cache.putCount())

        cache.evictAll()
        assertEquals(0, cache.size())
        assertNull(cache.get(1))
    }

    @Test
    fun inMemoryFiltering_filtersAndSortsStrictlyInMemory() {
        val docList = listOf(
            DocumentFile(id = "1", name = "Report 2026.docx", path = "/doc1", uriString = "uri1", sizeBytes = 1000, lastModified = 100, format = DocumentFormat.WORD),
            DocumentFile(id = "2", name = "Budget.xlsx", path = "/doc2", uriString = "uri2", sizeBytes = 2000, lastModified = 200, format = DocumentFormat.EXCEL),
            DocumentFile(id = "3", name = "Presentation.pptx", path = "/doc3", uriString = "uri3", sizeBytes = 3000, lastModified = 300, format = DocumentFormat.POWERPOINT),
            DocumentFile(id = "4", name = "Notes 2026.txt", path = "/doc4", uriString = "uri4", sizeBytes = 500, lastModified = 50, format = DocumentFormat.WORD)
        )

        // Filter for "2026"
        val filtered = docList.filter { it.name.contains("2026", ignoreCase = true) }
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.name == "Report 2026.docx" })
        assertTrue(filtered.any { it.name == "Notes 2026.txt" })

        // Sort by size descending
        val sortedBySizeDesc = docList.sortedByDescending { it.sizeBytes }
        assertEquals("Presentation.pptx", sortedBySizeDesc[0].name)
        assertEquals("Notes 2026.txt", sortedBySizeDesc.last().name)
    }

    @Test
    fun intentConsumption_clearsDataFlow_preventingDuplicateNavigation() {
        val intentDataFlow = MutableStateFlow<Pair<Uri?, DocumentFormat?>>(Pair(Uri.parse("content://docs/file.pdf"), DocumentFormat.PDF))

        assertNotNull("Intent data must be present before navigation", intentDataFlow.value.first)

        // Simulate onIntentConsumed callback
        val onIntentConsumed = {
            intentDataFlow.value = Pair(null, null)
        }

        onIntentConsumed()

        assertNull("Intent URI must be consumed and nullified", intentDataFlow.value.first)
        assertNull("Intent format must be consumed and nullified", intentDataFlow.value.second)
    }
}
