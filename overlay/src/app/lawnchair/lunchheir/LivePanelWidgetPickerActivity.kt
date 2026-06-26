package app.lawnchair.lunchheir

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import app.lawnchair.LawnchairLauncher

/**
 * Translucent helper that runs the interactive widget **pick → bind → configure** flow for a Live
 * Panel and persists the chosen widget id via [LivePanelHost].
 *
 * The id is allocated from the *launcher's own* widget host ([LawnchairLauncher.instance]'s
 * `appWidgetHolder`) so the launcher can later host the view live; the system picker performs the
 * bind (and any permission prompt) for us. Overlay-contained — declared in the manifest overlay and
 * launched from the Hax menu — so no `onActivityResult` patch to the launcher is needed. The panel
 * picks up the new widget the next time it attaches.
 */
class LivePanelWidgetPickerActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcher = LawnchairLauncher.instance
        if (launcher == null) {
            finish()
            return
        }
        if (savedInstanceState != null) {
            appWidgetId = savedInstanceState.getInt(KEY_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            return
        }
        appWidgetId = launcher.appWidgetHolder.allocateAppWidgetId()
        val pick = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        try {
            startActivityForResult(pick, REQ_PICK)
        } catch (e: Exception) {
            abort()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_ID, appWidgetId)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId) ?: appWidgetId
        if (resultCode != RESULT_OK) {
            abort()
            return
        }
        when (requestCode) {
            REQ_PICK -> {
                val configure = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.configure
                if (configure != null) {
                    val cfg = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                        .setComponent(configure)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    try {
                        startActivityForResult(cfg, REQ_CONFIG)
                    } catch (e: Exception) {
                        bindComplete(id)
                    }
                } else {
                    bindComplete(id)
                }
            }
            REQ_CONFIG -> bindComplete(id)
            else -> finish()
        }
    }

    private fun bindComplete(id: Int) {
        LivePanelHost.setBoundWidgetId(this, id)
        finish()
    }

    /** User cancelled or something failed: release the allocated id so it isn't leaked. */
    private fun abort() {
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            LawnchairLauncher.instance?.appWidgetHolder?.deleteAppWidgetId(appWidgetId)
        }
        finish()
    }

    companion object {
        private const val REQ_PICK = 4011
        private const val REQ_CONFIG = 4012
        private const val KEY_ID = "lunchheir_panel_widget_id"
    }
}
