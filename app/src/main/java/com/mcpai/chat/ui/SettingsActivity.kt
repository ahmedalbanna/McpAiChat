package com.mcpai.chat.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.appbar.MaterialToolbar
import com.mcpai.chat.R
import com.mcpai.chat.data.Settings
import com.mcpai.chat.llm.OpenAiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Configures the OpenAI-compatible provider: key, base URL, model, prompt.
 *
 * The model list is fetched from the provider's `/models` endpoint so the
 * available models can be picked instead of typed.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        settings = Settings.get(applicationContext)
        findViewById<MaterialToolbar>(R.id.settingsToolbar).setNavigationOnClickListener { finish() }

        val apiKeyField = findViewById<EditText>(R.id.apiKeyField)
        val baseUrlField = findViewById<EditText>(R.id.baseUrlField)
        val modelField = findViewById<MaterialAutoCompleteTextView>(R.id.modelField)
        val systemPromptField = findViewById<EditText>(R.id.systemPromptField)
        val temperatureSlider = findViewById<Slider>(R.id.temperatureSlider)
        val toolsSwitch = findViewById<MaterialSwitch>(R.id.toolsSwitch)

        apiKeyField.setText(settings.openAiApiKey)
        baseUrlField.setText(settings.openAiBaseUrl)
        modelField.setText(settings.model, false)
        systemPromptField.setText(settings.systemPrompt)
        temperatureSlider.value = settings.temperature.coerceIn(0f, 2f)
        toolsSwitch.isChecked = settings.toolsEnabled
        findViewById<MaterialSwitch>(R.id.confirmToolsSwitch).apply {
            isChecked = settings.confirmToolCalls
        }

        val historySlider = findViewById<Slider>(R.id.historySlider)
        val historyLabel = findViewById<TextView>(R.id.historyLabel)
        // The slider steps in whole values; snap to the stored limit so the label
        // and the value agree even if the setting was never touched.
        historySlider.value = settings.historyLimit.toFloat()
        historyLabel.text = getString(R.string.label_history, settings.historyLimit)
        historySlider.addOnChangeListener { _, value, _ ->
            historyLabel.text = getString(R.string.label_history, value.toInt())
        }

        // Seed the dropdown with the current model so it is usable before the
        // provider list arrives.
        modelField.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, listOf(settings.model)),
        )

        findViewById<MaterialButton>(R.id.saveButton).setOnClickListener {
            settings.openAiApiKey = apiKeyField.text.toString()
            settings.openAiBaseUrl = baseUrlField.text.toString()
            settings.model = modelField.text.toString()
            settings.systemPrompt = systemPromptField.text.toString()
            settings.temperature = temperatureSlider.value
            settings.toolsEnabled = toolsSwitch.isChecked
            settings.confirmToolCalls =
                findViewById<MaterialSwitch>(R.id.confirmToolsSwitch).isChecked
            settings.historyLimit = historySlider.value.toInt()
            finish()
        }

        findViewById<MaterialButton>(R.id.refreshModelsButton).setOnClickListener {
            loadModels(baseUrlField.text.toString(), apiKeyField.text.toString(), modelField)
        }

        findViewById<MaterialButton>(R.id.manageToolsButton).setOnClickListener {
            startActivity(Intent(this, ToolDetailActivity::class.java))
        }
    }

    private fun loadModels(baseUrl: String, apiKey: String, target: MaterialAutoCompleteTextView) {
        if (baseUrl.isBlank()) {
            Toast.makeText(this, R.string.label_base_url, Toast.LENGTH_SHORT).show()
            return
        }
        val button = findViewById<MaterialButton>(R.id.refreshModelsButton)
        button.isEnabled = false
        lifecycleScope.launch {
            val models = withContext(Dispatchers.IO) {
                OpenAiProvider(baseUrl, apiKey, "").listModels()
            }
            button.isEnabled = true
            if (models.isEmpty()) {
                Toast.makeText(
                    this@SettingsActivity,
                    R.string.no_models_found,
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            target.setAdapter(
                ArrayAdapter(
                    this@SettingsActivity,
                    android.R.layout.simple_list_item_1,
                    models,
                ),
            )
            if (!models.contains(settings.model)) {
                target.setText(models.first(), false)
            }
            Toast.makeText(
                this@SettingsActivity,
                getString(R.string.models_loaded, models.size),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
