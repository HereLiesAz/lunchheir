package app.lawnchair.lunchheir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.Root
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.LauncherState
import com.hereliesaz.aznavrail.AzButton
import kotlinx.coroutines.launch

/**
 * The Hax-style menu — a summoned, flat, typography-forward menu, built from the AzNavRail
 * (HereLiesAz) component library.
 *
 * Increment 1 of the Hax shell: it hosts the menu through Lawnchair's existing Compose floating
 * view ([ComposeBottomSheet], which already wires the Compose lifecycle + theme), and renders the
 * top-level sections as AzNavRail [AzButton]s wired to the launcher's real actions. Later
 * increments move to AzNavRail's full rail-that-expands ([com.hereliesaz.aznavrail.AzHostActivityLayout]),
 * add Live Panel toggles (azRailToggle/azRailCycler), the monochrome theme, and kinetic type.
 */
object HaxShell {

    @JvmStatic
    fun show(launcher: LawnchairLauncher) {
        ComposeBottomSheet.show(launcher, contentPaddings = PaddingValues(vertical = 24.dp)) {
            val sheet = this
            HaxMenu(
                onApps = {
                    sheet.close(true)
                    launcher.stateManager.goToState(LauncherState.ALL_APPS)
                },
                onSearch = {
                    sheet.close(true)
                    launcher.stateManager.goToState(LauncherState.ALL_APPS)
                },
                onSettings = {
                    sheet.close(true)
                    launcher.startActivity(PreferenceActivity.createIntent(launcher, Root))
                },
                onSystem = {
                    sheet.close(true)
                    HaxSystem.show(launcher)
                },
                onTweaks = {
                    sheet.close(true)
                    LunchHeirSettings.show(launcher)
                },
            )
        }
    }
}

@Composable
fun HaxMenu(
    onApps: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onSystem: () -> Unit,
    onTweaks: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HaxEntry(text = "APPS", onClick = onApps)
        HaxEntry(text = "SEARCH", onClick = onSearch)
        HaxEntry(text = "SETTINGS", onClick = onSettings)
        HaxEntry(text = "SYSTEM", onClick = onSystem)
        HaxEntry(text = "TWEAKS", onClick = onTweaks)
    }
}

// Flat monotone: ink on paper. Kept local to the menu for now; a global Lunch Heir monochrome
// theme is a separate step.
private val HaxInk = Color(0xFF121212)
private val HaxPaper = Color(0xFFF4F4F4)

@Composable
internal fun HaxEntry(text: String, onClick: () -> Unit) {
    // Kinetic entrance: each word springs up and fades in when the menu opens. Animatables read
    // inside graphicsLayer update the layer without recomposing per frame. Scale uses a bouncy
    // spring; alpha uses a plain tween so the fade can't overshoot past 1.
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
        // Kinetic, typography-forward sections: big, heavy, wide-tracked words.
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
