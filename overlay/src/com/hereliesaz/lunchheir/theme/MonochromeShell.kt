package com.hereliesaz.lunchheir.theme

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import app.lawnchair.LawnchairLauncher
import com.hereliesaz.lunchheir.LunchHeirPrefs

/**
 * The "Hax" **monochrome shell**: renders the entire launcher UI in grayscale by compositing the
 * DragLayer through a saturation-0 color filter on a hardware layer. Everything drawn by the
 * launcher — icons, text, folders, our recents bar and Live Panel — is desaturated in one pass.
 *
 * Overlay-only (no upstream patch) and fully reversible: clearing the layer paint restores color.
 * The wallpaper lives in a separate window, so it stays in color — the effect is a monochrome UI
 * floating over the user's wallpaper. Gated by the MONOCHROME toggle; [apply] reflects the current
 * toggle state each call (sets the filter when on, clears it when off).
 */
object MonochromeShell {

    @JvmStatic
    fun apply(launcher: LawnchairLauncher) {
        val target: View = launcher.dragLayer
        if (LunchHeirPrefs.isEnabled(launcher, LunchHeirPrefs.Feature.MONOCHROME)) {
            val grayscale = ColorMatrix().apply { setSaturation(0f) }
            val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(grayscale) }
            target.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        } else {
            target.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }
}
