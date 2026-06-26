package app.lawnchair.lunchheir

import android.content.Context

/**
 * Per-feature toggles for Lunch Heir's additions. Backed by a private SharedPreferences so it's
 * fully overlay-contained (no upstream patch). There is no master switch by design: turning every
 * feature off leaves the user with plain Lawnchair.
 *
 * Each feature's entry point checks [isEnabled] before activating. Defaults are `true` to preserve
 * current behavior. A settings surface to flip these is a follow-up; they can already be set via
 * [setEnabled].
 */
object LunchHeirPrefs {

    private const val PREFS_NAME = "lunchheir_features"

    enum class Feature(val key: String, val default: Boolean, val label: String) {
        LIVE_RECENTS_BAR("live_recents_bar", true, "RECENTS BAR"),
        SECOND_ROW("second_row", true, "SECOND ROW"),
        HAX_MENU("hax_menu", true, "HAX MENU"),
        GROUPS("groups", true, "GROUPS"),

        // New visual surface; defaults off so it can't overlap the default home until its placement
        // is tuned on-device. Opt-in via setEnabled / the (forthcoming) settings surface.
        LIVE_PANEL("live_panel", false, "LIVE PANEL"),

        // Nested folders (folder-in-folder). Off by default: with it off the loader seam is a no-op
        // and folder loading is identical to upstream. Opt-in until the in-folder UI lands.
        NESTED_FOLDERS("nested_folders", false, "NESTED FOLDERS"),

        // Hax monochrome shell: desaturate the whole launcher UI to grayscale. Off by default.
        MONOCHROME("monochrome", false, "MONOCHROME"),
        ;
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context, feature: Feature): Boolean =
        prefs(context).getBoolean(feature.key, feature.default)

    fun setEnabled(context: Context, feature: Feature, enabled: Boolean) {
        prefs(context).edit().putBoolean(feature.key, enabled).apply()
    }
}
