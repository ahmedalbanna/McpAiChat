package com.mcpai.chat.ui

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mcpai.chat.R
import com.mcpai.chat.data.Settings
import com.mcpai.chat.mcp.McpServerConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-side test for the server edit screen.
 *
 * The bug this guards against: saving an edit wrote `spkiPin = null`, so a
 * trusted server quietly lost its certificate pinning. The same save also
 * dropped the custom headers and reset the enabled flag.
 */
@RunWith(AndroidJUnit4::class)
class ServerEditActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val settings: Settings by lazy { Settings.get(context) }

    private lateinit var originalServers: List<McpServerConfig>

    private val testServer = McpServerConfig(
        id = "edit-screen-test",
        name = "Original name",
        url = "https://example.com/mcp",
        authHeaderName = "X-Token",
        authHeaderValue = "secret-token",
        timeoutSeconds = 45,
        customHeaders = mapOf("X-Trace" to "on"),
        isTrusted = true,
        spkiPin = "sha256:stored-pin",
    )

    @Before
    fun setUp() {
        // Keep the device's own servers; restore them afterwards.
        originalServers = settings.servers()
        settings.saveServers(originalServers.filterNot { it.id == testServer.id } + testServer)
    }

    @After
    fun tearDown() {
        settings.saveServers(originalServers)
    }

    @Test
    fun savingAnEditKeepsThePinAndTheFieldsTheFormDoesNotShow() {
        ActivityScenario.launch<ServerEditActivity>(ServerEditActivity.intent(context, testServer)).use {
            onView(withId(R.id.nameField)).perform(replaceText("Renamed"))
            onView(withId(R.id.saveButton)).perform(click())
        }

        val saved = settings.servers().first { it.id == testServer.id }
        assertEquals("Renamed", saved.name)
        assertEquals("https://example.com/mcp", saved.url)
        assertEquals("X-Token", saved.authHeaderName)
        assertEquals("secret-token", saved.authHeaderValue)
        assertEquals(45, saved.timeoutSeconds)
        assertEquals(mapOf("X-Trace" to "on"), saved.customHeaders)
        assertTrue("the server must stay trusted", saved.isTrusted)
        assertEquals("the stored pin must survive the edit", "sha256:stored-pin", saved.spkiPin)
    }

    @Test
    fun changingTheHostDropsThePin() {
        ActivityScenario.launch<ServerEditActivity>(ServerEditActivity.intent(context, testServer)).use {
            onView(withId(R.id.urlField)).perform(replaceText("https://other.example.com/mcp"))
            onView(withId(R.id.saveButton)).perform(click())
        }

        val saved = settings.servers().first { it.id == testServer.id }
        assertEquals("https://other.example.com/mcp", saved.url)
        assertEquals(null, saved.spkiPin)
    }
}
