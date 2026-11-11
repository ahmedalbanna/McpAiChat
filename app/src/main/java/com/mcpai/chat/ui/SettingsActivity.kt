package com.mcpai.chat.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.mcpai.chat.R
import com.mcpai.chat.data.Settings
import com.mcpai.chat.llm.OpenAiProvider
import com.mcpai.chat.ui.theme.McpAiChatTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Configures the OpenAI-compatible provider: key, base URL, model, prompt.
 *
 * The model list is fetched from the provider's `/models` endpoint so the
 * available models can be picked instead of typed.
 */
class SettingsActivity : ComponentActivity() {

    private lateinit var settings: Settings
    private val form = mutableStateOf(ProviderForm())
    private val models = mutableStateOf(listOf<String>())
    private val loadingModels = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settings = Settings.get(applicationContext)

        form.value = ProviderForm(
            apiKey = settings.openAiApiKey,
            baseUrl = settings.openAiBaseUrl,
            model = settings.model,
            systemPrompt = settings.systemPrompt,
            temperature = settings.temperature.coerceIn(0f, 2f),
            toolsEnabled = settings.toolsEnabled,
            confirmToolCalls = settings.confirmToolCalls,
            historyLimit = settings.historyLimit,
        )
        // Seed the dropdown with the current model so it is usable before the
        // provider list arrives.
        models.value = listOf(settings.model).filter { it.isNotBlank() }

        setContent {
            McpAiChatTheme {
                SettingsScreen(
                    form = form.value,
                    models = models.value,
                    loadingModels = loadingModels.value,
                    onFormChange = { form.value = it },
                    onRefreshModels = ::loadModels,
                    onOpenToolDetail = {
                        startActivity(Intent(this, ToolDetailActivity::class.java))
                    },
                    onBack = ::finish,
                    onSave = {
                        val current = form.value
                        settings.openAiApiKey = current.apiKey
                        settings.openAiBaseUrl = current.baseUrl
                        settings.model = current.model
                        settings.systemPrompt = current.systemPrompt
                        settings.temperature = current.temperature
                        settings.toolsEnabled = current.toolsEnabled
                        settings.confirmToolCalls = current.confirmToolCalls
                        settings.historyLimit = current.historyLimit
                        finish()
                    },
                )
            }
        }
    }

    private fun loadModels() {
        val current = form.value
        if (current.baseUrl.isBlank()) {
            Toast.makeText(this, R.string.label_base_url, Toast.LENGTH_SHORT).show()
            return
        }
        if (loadingModels.value) return
        loadingModels.value = true
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                OpenAiProvider(current.baseUrl, current.apiKey, "").listModels()
            }
            loadingModels.value = false
            if (loaded.isEmpty()) {
                Toast.makeText(this@SettingsActivity, R.string.no_models_found, Toast.LENGTH_SHORT).show()
                return@launch
            }
            models.value = loaded
            if (current.model !in loaded) {
                form.value = current.copy(model = loaded.first())
            }
            Toast.makeText(
                this@SettingsActivity,
                getString(R.string.models_loaded, loaded.size),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
