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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
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
 * The dropdown's top section routes the launcher's real actions (APPS, SETTINGS, SYSTEM, TWEAKS,
 * ADD PANEL); below a divider, every [LunchHeirPrefs.Feature] is an inline toggle so the feature
 * switches are discoverable from the home screen without digging into a settings sheet.
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

    AzDropdownMenu(navController = navController) {
        azConfig(
            design = AzDropdownDesign.MENU,
            dockingSide = AzDockingSide.LEFT,
            showFooter = false,
            inAppAbout = false,
        )

        azItem(text = "APPS") {
            launcher.stateManager.goToState(LauncherState.ALL_APPS)
        }
        azItem(text = "SETTINGS") {
            launcher.startActivity(PreferenceActivity.createIntent(launcher, Root))
        }
        azItem(text = "SYSTEM") {
            HaxSystem.show(launcher)
        }
        azItem(text = "TWEAKS") {
            LunchHeirSettings.show(launcher)
        }
        azItem(text = "ADD PANEL") {
            launcher.startActivity(Intent(launcher, LivePanelWidgetPickerActivity::class.java))
        }

        azDivider()

        // Every feature toggle, inline — flip them straight from the home screen. closeOnClick is
        // false so the menu stays open while the user flips several. Changes that need a relaunch to
        // take effect (e.g. surfaces attached in onCreate) are persisted immediately and apply on the
        // next launcher start.
        for (feature in LunchHeirPrefs.Feature.values()) {
            var checked by remember {
                mutableStateOf(LunchHeirPrefs.isEnabled(launcher, feature))
            }
            azToggle(
                toggleOnText = "${feature.label} ✓",
                toggleOffText = "${feature.label} ✗",
                isChecked = checked,
                closeOnClick = false,
            ) { enabled ->
                checked = enabled
                LunchHeirPrefs.setEnabled(launcher, feature, enabled)
            }
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
