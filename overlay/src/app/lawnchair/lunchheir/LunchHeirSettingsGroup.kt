package app.lawnchair.lunchheir

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup

/**
 * Lunch Heir feature toggles rendered as native Lawnchair settings (a [PreferenceGroup] of
 * [SwitchPreference]s). These are injected into Lawnchair's own preference screens by
 * overlay/apply_overlay.py seams — NOT shown in the Hax menu — so the switches sit where a user
 * looks for settings:
 *
 *  - [LunchHeirFeatureToggles] (all features) is the consolidated "Lunch Heir" section, placed in
 *    the Home Screen settings.
 *  - [LunchHeirFeatureTogglesFor] (one [LunchHeirPrefs.Category]) surfaces each feature inline on
 *    its matching native screen (Dock, Folders, General, …).
 *
 * Toggles persist immediately to [LunchHeirPrefs] (private SharedPreferences). Features whose
 * surfaces are attached in [LunchHeirHome.onCreate] take effect on the next launcher start.
 */
@Composable
fun LunchHeirFeatureToggles() {
    FeatureToggleGroup("Lunch Heir", LunchHeirPrefs.Feature.values().toList())
}

@Composable
fun LunchHeirFeatureTogglesFor(category: LunchHeirPrefs.Category) {
    FeatureToggleGroup("Lunch Heir", LunchHeirPrefs.featuresIn(category))
}

@Composable
private fun FeatureToggleGroup(heading: String, features: List<LunchHeirPrefs.Feature>) {
    if (features.isEmpty()) return
    val context = LocalContext.current
    PreferenceGroup(heading = heading) {
        features.forEach { feature ->
            Item {
                var checked by remember(feature) {
                    mutableStateOf(LunchHeirPrefs.isEnabled(context, feature))
                }
                SwitchPreference(
                    checked = checked,
                    onCheckedChange = {
                        checked = it
                        LunchHeirPrefs.setEnabled(context, feature, it)
                    },
                    label = feature.label,
                    description = feature.description,
                )
            }
        }
    }
}
