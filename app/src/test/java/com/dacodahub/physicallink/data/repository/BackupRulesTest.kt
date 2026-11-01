package com.dacodahub.physicallink.data.repository

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.dacodahub.physicallink.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupRulesTest {
    @Test fun `backup stays enabled and all Android backup modes include only the actual preference files`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        assertTrue(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        val expected = setOf("dashboard_layouts", "appearance").map {
            "file" to context.preferencesDataStoreFile(it).relativeTo(context.filesDir).invariantSeparatorsPath
        }.toSet()
        fun includes(resource: Int): Map<String, Set<Pair<String, String>>> {
            val sections = mutableMapOf<String, MutableSet<Pair<String, String>>>()
            context.resources.getXml(resource).use { xml ->
                var section = ""
                while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType == XmlPullParser.START_TAG) {
                        when (xml.name) {
                            "full-backup-content", "cloud-backup", "device-transfer" -> {
                                section = xml.name
                                sections[section] = linkedSetOf()
                            }
                            "include" -> {
                                assertNotNull(sections[section])
                                sections.getValue(section).add(xml.getAttributeValue(null, "domain") to xml.getAttributeValue(null, "path"))
                            }
                            "exclude" -> fail("Exact file allowlists should not need broad exclusions")
                        }
                    }
                    xml.next()
                }
            }
            return sections
        }
        assertEquals(mapOf("full-backup-content" to expected), includes(R.xml.backup_rules))
        assertEquals(mapOf("cloud-backup" to expected, "device-transfer" to expected), includes(R.xml.data_extraction_rules))
    }
}
