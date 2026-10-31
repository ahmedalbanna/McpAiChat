package com.mcpai.chat.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The backup rules exclude the encrypted preferences file by name.
 *
 * If the name in the XML stops matching the one the app writes, the exclusion
 * silently stops working and the API key rides along in every cloud backup.
 * Nothing else catches that, so it is checked here.
 */
class BackupRulesTest {

    private fun read(path: String): String {
        val file = File("src/main/res/xml/$path")
        assertTrue("missing $path (working directory: ${File("").absolutePath})", file.isFile)
        return file.readText()
    }

    @Test
    fun `full backup rules exclude the encrypted preferences file`() {
        val xml = read("backup_rules.xml")
        assertTrue(
            "backup_rules.xml must exclude ${EncryptedPreferences.PREFERENCES_NAME}.xml",
            xml.contains("path=\"${EncryptedPreferences.PREFERENCES_NAME}.xml\""),
        )
    }

    @Test
    fun `cloud backup and device transfer exclude the encrypted preferences file`() {
        val xml = read("data_extraction_rules.xml")
        val expected = "path=\"${EncryptedPreferences.PREFERENCES_NAME}.xml\""
        assertTrue(
            "data_extraction_rules.xml must exclude the preferences file twice " +
                "(cloud backup and device transfer), found ${xml.split(expected).size - 1}",
            xml.split(expected).size - 1 == 2,
        )
    }
}
