package app.lawnchair.lunchheir

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.views.ComposeBottomSheet

/**
 * The Hax "Live Panel" — flat system controls reached from the menu's SYSTEM section.
 *
 * Modern Android blocks apps from flipping most radios directly, so these open the relevant
 * system settings; flashlight is a real toggle via [CameraManager]. Truly inline, animated
 * panels (via Lawnchair's HeadlessWidgetsManager) are a later step. Entries reuse the menu's
 * monochrome kinetic-type [HaxEntry] styling.
 */
object HaxSystem {

    private const val TAG = "HaxSystem"
    private var torchOn = false

    @JvmStatic
    fun show(launcher: LawnchairLauncher) {
        ComposeBottomSheet.show(launcher, contentPaddings = PaddingValues(vertical = 24.dp)) {
            val sheet = this
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HaxEntry("WIFI") { sheet.close(true); openSettings(launcher, Settings.ACTION_WIFI_SETTINGS) }
                HaxEntry("BLUETOOTH") { sheet.close(true); openSettings(launcher, Settings.ACTION_BLUETOOTH_SETTINGS) }
                HaxEntry("SOUND") { sheet.close(true); openSettings(launcher, Settings.ACTION_SOUND_SETTINGS) }
                HaxEntry("DISPLAY") { sheet.close(true); openSettings(launcher, Settings.ACTION_DISPLAY_SETTINGS) }
                HaxEntry("FLASHLIGHT") { toggleTorch(launcher) }
            }
        }
    }

    private fun openSettings(context: Context, action: String) {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w(TAG, "could not open $action", e)
        }
    }

    private fun toggleTorch(context: Context) {
        val cameraManager = context.getSystemService(CameraManager::class.java) ?: return
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull {
                cameraManager.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            torchOn = !torchOn
            cameraManager.setTorchMode(cameraId, torchOn)
        } catch (e: Exception) {
            Log.w(TAG, "could not toggle torch", e)
        }
    }
}
