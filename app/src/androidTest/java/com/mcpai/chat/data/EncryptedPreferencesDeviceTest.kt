package com.mcpai.chat.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Device-side tests for [EncryptedPreferences].
 *
 * The Keystore only exists on a device, and so does the file the encrypted
 * values land in — which is where the security claim has to be checked.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedPreferencesDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun valuesSurviveARoundTrip() {
        val prefs = EncryptedPreferences.create(context)
        prefs.putString("probe_string", "hello device")
        assertEquals("hello device", prefs.getString("probe_string", null))
    }

    @Test
    fun intsAndBooleansRoundTrip() {
        val prefs = EncryptedPreferences.create(context)
        prefs.putInt("probe_int", 42)
        prefs.putBoolean("probe_bool", true)
        assertEquals(42, prefs.getInt("probe_int", -1))
        assertTrue(prefs.getBoolean("probe_bool", false))
        assertFalse(prefs.getBoolean("probe_missing", false))
    }

    @Test
    fun theSameValueIsEncryptedDifferentlyEveryTime() {
        val prefs = EncryptedPreferences.create(context)
        prefs.putString("probe_a", "identical")
        prefs.putString("probe_b", "identical")

        // The stored form is IV + ciphertext; the decrypted form is equal by
        // definition, so the comparison has to happen below the encryption.
        val raw = context.getSharedPreferences(
            EncryptedPreferences.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        assertNotEquals(
            "the IV must differ per write, otherwise equal values are recognisable",
            raw.getString("probe_a", null),
            raw.getString("probe_b", null),
        )
        assertEquals("identical", prefs.getString("probe_a", null))
        assertEquals("identical", prefs.getString("probe_b", null))
    }

    @Test
    fun thePlainTextNeverReachesTheDisk() {
        val secret = "secret-value-${System.nanoTime()}"
        val prefs = EncryptedPreferences.create(context)
        prefs.putString("probe_secret", secret)

        // Writes are asynchronous; force the file out before reading it.
        context.getSharedPreferences(EncryptedPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .commit()

        val file = File(
            context.applicationInfo.dataDir,
            "shared_prefs/${EncryptedPreferences.PREFERENCES_NAME}.xml",
        )
        assertTrue("expected the preferences file at $file", file.isFile)
        val contents = file.readText()
        assertFalse("the secret was written to disk in the clear", contents.contains(secret))
        assertTrue("the encrypted value should be there", contents.contains("probe_secret"))
    }
}
