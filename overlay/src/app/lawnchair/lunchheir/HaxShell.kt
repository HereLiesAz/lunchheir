package app.lawnchair.lunchheir

import android.content.Intent
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.rememberNavController
import app.lawnchair.LawnchairLauncher
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.Root
import com.android.launcher3.LauncherState
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzDropdownMenu
import com.hereliesaz.aznavrail.model.AzDockingSide
import com.hereliesaz.aznavrail.model.AzDropdownDesign
import kotlinx.coroutines.launch

/**
 * The Hax-style menu — built from the AzNavRail (HereLiesAz) [AzDropdownMenu] component.
 *
 * Rather than a hand-rolled floating button + bottom sheet, the menu IS AzNavRail's standalone
 * dropdown: a small docked header icon that expands into a flat, typography-forward list. It is
 * embedded directly in the bottom recents row (see [LunchHeirHome]) so the trigger sits where the
 * user expects it — in the recents/dock area, not floating at the top of the screen.
 *
 * The dropdown is **actions only** — APPS, SEARCH, SETTINGS, SYSTEM, TWEAKS, ADD PANEL — each wired
 * to a real launcher action. Feature TOGGLES are NOT here: they live in the launcher Settings (a
 * consolidated "Lunch Heir" section in Home Screen settings, plus each toggle in its matching native
 * category), rendered by [LunchHeirFeatureToggles]. SETTINGS is the path to them.
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
            // default DisposeOnDetachedFromWindow strategy would dispose and rebuild the menu (and
            // lose the toggle states) on every such transition.
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

    // Actions only — these are the Hax menu items (the big-typography sections in the original Hax
    // launcher). Feature TOGGLES do NOT live here: they belong in the launcher Settings (a
    // consolidated "Lunch Heir" section plus each toggle in its matching Lawnchair category). The
    // SETTINGS item below is the path to them.
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

// Flat monotone: ink on paper. Shared by the sub-sheets (e.g. HaxSystem) that still render kinetic
// AzNavRail entries inside a ComposeBottomSheet.
private val HaxInk = Color(0xFF121212)
private val HaxPaper = Color(0xFFF4F4F4)

/**
 * A single kinetic, typography-forward menu entry: a big, heavy, wide-tracked word on an
 * AzNavRail [AzButton] that springs up and fades in when its sheet opens. Used by [HaxSystem].
 */
@Composable
internal fun HaxEntry(text: String, onClick: () -> Unit) {
    // Animatables read inside graphicsLayer update the layer without recomposing per frame. Scale
    // uses a bouncy spring; alpha a plain tween so the fade can't overshoot past 1.
    val scaleAnim = remember { Animatable(0.8f) }
    val alphaAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch {
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
        launch {
            alphaAnim.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 300))
        }
    }

    AzButton(
        onClick = onClick,
        text = text,
        modifier = Modifier.fillMaxWidth(),
        color = HaxInk,
        textColor = HaxPaper,
        fillColor = HaxInk,
        itemContent = {
            Text(
                text = text,
                color = HaxPaper,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer {
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                    alpha = alphaAnim.value
                },
            )
        },
    )
}
