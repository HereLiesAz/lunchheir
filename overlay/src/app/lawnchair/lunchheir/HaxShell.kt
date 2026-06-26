package app.lawnchair.lunchheir

import android.content.Intent
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.navigation.compose.rememberNavController
import app.lawnchair.LawnchairLauncher
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.Root
import com.android.launcher3.LauncherState
import com.hereliesaz.aznavrail.AzDropdownMenu
import com.hereliesaz.aznavrail.model.AzDockingSide
import com.hereliesaz.aznavrail.model.AzDropdownDesign

/**
 * The Hax-style menu — built from the AzNavRail (HereLiesAz) [AzDropdownMenu] component.
 *
 * Rather than a hand-rolled floating button + bottom sheet, the menu IS AzNavRail's standalone
 * dropdown: a small docked header icon (embedded in the bottom recents row, see [LunchHeirHome])
 * that expands into a flat, typography-forward list.
 *
 * The dropdown is **actions only** — APPS, SEARCH, SETTINGS, SYSTEM, TWEAKS, ADD PANEL — each wired
 * to a real launcher action. Feature TOGGLES are NOT here: they live in the launcher Settings (a
 * consolidated "Lunch Heir" section in Home Screen settings, plus each toggle in its matching native
 * category), rendered by [LunchHeirFeatureToggles]. SETTINGS is the path to them.
 *
 * Kinetic typography: the menu words get their WP7 turnstile/tilt motion from AzNavRail itself; the
 * surfaces AzNavRail doesn't render (the [HaxSystem] sheet) use [KineticWord] directly.
 */
object HaxShell {

    /**
     * Build the menu as a [View] that can be added to the launcher's view tree (e.g. the recents
     * row). The returned ComposeView hosts the AzNavRail dropdown; it renders only the small header
     * icon until tapped, then expands the dropdown.
     */
    @JvmStatic
    fun createMenuView(launcher: LawnchairLauncher): View =
        ComposeView(launcher).apply {
            // Tie the composition to the launcher's lifecycle, not window attach/detach. The
            // DragLayer detaches/re-attaches its children during state transitions and drags; the
            // default DisposeOnDetachedFromWindow strategy would dispose and rebuild the menu on
            // every such transition.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    LunchHeirMenu(launcher)
                }
            }
        }
}

@Composable
private fun LunchHeirMenu(launcher: LawnchairLauncher) {
    // AzDropdownMenu requires a NavHostController for its route-based items. We drive every item
    // via onClick instead of routes (there is no Compose nav graph on the home screen), so a
    // throwaway controller is all the component needs.
    val navController = rememberNavController()

    // Actions only — these are the Hax menu sections. Feature toggles live in the launcher Settings.
    AzDropdownMenu(navController = navController) {
        azConfig(
            design = AzDropdownDesign.MENU,
            dockingSide = AzDockingSide.LEFT,
            showFooter = false,
        )

        azItem(text = "APPS") {
            launcher.stateManager.goToState(LauncherState.ALL_APPS)
        }
        azItem(text = "SEARCH") {
            launcher.stateManager.goToState(LauncherState.ALL_APPS)
        }
        azItem(text = "SETTINGS") {
            launcher.startActivity(PreferenceActivity.createIntent(launcher, Root))
        }
        azItem(text = "SYSTEM") {
            HaxSystem.show(launcher)
        }
        // Smart-fill / AI provider config (not feature toggles — those live in launcher Settings).
        azItem(text = "TWEAKS") {
            LunchHeirSettings.show(launcher)
        }
        azItem(text = "ADD PANEL") {
            launcher.startActivity(Intent(launcher, LivePanelWidgetPickerActivity::class.java))
        }
    }
}

// Flat monotone ink, for the sub-sheets (e.g. HaxSystem) that render kinetic words on a light sheet.
internal val HaxInk = Color(0xFF121212)

/**
 * A single Hax menu entry: a big Metro word with the WP7 turnstile entrance + tilt-on-press
 * ([KineticWord]), staggered by [index]. Used by [HaxSystem]'s sheet.
 */
@Composable
internal fun HaxEntry(text: String, index: Int = 0, onClick: () -> Unit) {
    KineticWord(text = text, index = index, color = HaxInk, onClick = onClick)
}
