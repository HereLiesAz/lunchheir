package com.hereliesaz.lunchheir

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

    /**
     * [label]/[description] are the human-readable strings shown in the launcher Settings (both the
     * consolidated "Lunch Heir" section and each feature's contextual placement). [category] is the
     * Lawnchair settings screen this feature naturally belongs to, used to surface the toggle inline
     * with the matching native settings.
     */
    enum class Feature(
        val key: String,
        val default: Boolean,
        val label: String,
        val description: String,
        val category: Category,
    ) {
        LIVE_RECENTS_BAR(
            "live_recents_bar", true, "Live recents bar",
            "A swipe-to-dismiss recent-apps row docked at the bottom.", Category.DOCK,
        ),
        SECOND_ROW(
            "second_row", true, "Second hotseat row",
            "A second persistent app row above the hotseat.", Category.DOCK,
        ),
        HAX_MENU(
            "hax_menu", true, "Hax menu",
            "The Hax dropdown menu in the bottom row.", Category.HOME_SCREEN,
        ),
        GROUPS(
            "groups", true, "Groups",
            "Folders that never collapse, with smart auto-fill.", Category.FOLDERS,
        ),

        // New visual surface; defaults off so it can't overlap the default home until its placement
        // is tuned on-device. Opt-in via setEnabled / the settings surface.
        LIVE_PANEL(
            "live_panel", false, "Live panel",
            "A flat kinetic clock/widget panel on the home screen.", Category.HOME_SCREEN,
        ),

        // Nested folders (folder-in-folder). Off by default: with it off the loader seam is a no-op
        // and folder loading is identical to upstream. Opt-in until the in-folder UI lands.
        NESTED_FOLDERS(
            "nested_folders", false, "Nested folders",
            "Allow a folder inside a folder.", Category.FOLDERS,
        ),

        // Hax monochrome shell: desaturate the whole launcher UI to grayscale. Off by default.
        MONOCHROME(
            "monochrome", false, "Monochrome",
            "Render the whole launcher UI in grayscale.", Category.GENERAL,
        ),
        ;
    }

    /** The native Lawnchair settings screen a feature is surfaced under (for contextual placement). */
    enum class Category { HOME_SCREEN, DOCK, FOLDERS, GENERAL }

    /** Features that belong under [category], in declaration order. */
    fun featuresIn(category: Category): List<Feature> =
        Feature.values().filter { it.category == category }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context, feature: Feature): Boolean =
        prefs(context).getBoolean(feature.key, feature.default)

    fun setEnabled(context: Context, feature: Feature, enabled: Boolean) {
        prefs(context).edit().putBoolean(feature.key, enabled).apply()
    }
}
