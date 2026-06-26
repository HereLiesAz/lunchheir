package com.hereliesaz.lunchheir

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchair.LawnchairLauncher
import com.hereliesaz.lunchheir.smartfill.AiProvider
import com.hereliesaz.lunchheir.smartfill.SmartFillConfig
import app.lawnchair.views.ComposeBottomSheet

/**
 * The Lunch Heir control panel: flips each per-feature toggle and configures the (provider-agnostic)
 * Smart-fill cloud refiner. This is the user-facing surface behind the "all features are togglable,
 * down to vanilla Lawnchair" design — every [LunchHeirPrefs.Feature] is listed here, so the user can
 * switch any of them off (including all of them) without a master switch.
 *
 * Hosted via Lawnchair's [ComposeBottomSheet] (same path as the Hax menu), so it needs no Activity
 * and inherits the Compose lifecycle + theme. Reached from the Hax menu's TWEAKS entry.
 */
object LunchHeirSettings {

    @JvmStatic
    fun show(launcher: LawnchairLauncher) {
        ComposeBottomSheet.show(launcher, contentPaddings = PaddingValues(vertical = 24.dp)) {
            SettingsContent(launcher)
        }
    }
}

@Composable
private fun SettingsContent(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader("FEATURES")
        for (feature in LunchHeirPrefs.Feature.values()) {
            FeatureToggle(context, feature)
        }

        SectionHeader("SMART FILL — AI")
        CloudSection(context)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun FeatureToggle(context: Context, feature: LunchHeirPrefs.Feature) {
    var checked by remember { mutableStateOf(LunchHeirPrefs.isEnabled(context, feature)) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = feature.name.replace('_', ' '),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Switch(
            checked = checked,
            onCheckedChange = {
                checked = it
                LunchHeirPrefs.setEnabled(context, feature, it)
            },
        )
    }
}

@Composable
private fun CloudSection(context: Context) {
    val config = remember { SmartFillConfig(context) }
    val stored = remember { config.provider() }

    var enabled by remember { mutableStateOf(config.cloudEnabled) }
    var baseUrl by remember { mutableStateOf(stored?.baseUrl ?: "") }
    var model by remember { mutableStateOf(stored?.model ?: "") }
    var apiKey by remember { mutableStateOf(stored?.apiKey ?: "") }
    var format by remember { mutableStateOf(stored?.format ?: AiProvider.Format.OPENAI) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("USE CLOUD AI", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Switch(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                config.cloudEnabled = it
            },
        )
    }

    if (enabled) {
        // Any AI backend: an OpenAI-compatible endpoint (OpenAI, Gemini compat, Groq, Ollama, …),
        // Anthropic, or a custom one. Tap FORMAT to cycle the wire shape.
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Endpoint URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("Model") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API key (blank for local)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { format = nextFormat(format) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("FORMAT: ${format.name}")
        }
        Button(
            onClick = { config.setProvider(AiProvider(baseUrl, model, apiKey, format)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("SAVE PROVIDER")
        }
    }
}

private fun nextFormat(current: AiProvider.Format): AiProvider.Format {
    val values = AiProvider.Format.values()
    return values[(current.ordinal + 1) % values.size]
}
