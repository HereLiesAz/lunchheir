package app.lawnchair.lunchheir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.Root
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.LauncherState
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.AzButtonShape

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
                    // Live Panel toggles land in the next increment.
                    sheet.close(true)
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
    }
}

@Composable
private fun HaxEntry(text: String, onClick: () -> Unit) {
    AzButton(
        onClick = onClick,
        text = text,
        modifier = Modifier.fillMaxWidth(),
        shape = AzButtonShape.RECTANGLE,
        contentPadding = PaddingValues(20.dp),
    )
}
