package app.lawnchair.lunchheir

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.ViewGroup
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo

/**
 * Hosts a **real app widget** inside a Live Panel, reusing the launcher's own already-running
 * [com.android.launcher3.widget.LauncherWidgetHolder] (`launcher.appWidgetHolder`). Because that
 * host is already `startListening()`-ing for the launcher, the embedded widget updates live with no
 * separate lifecycle to manage here.
 *
 * This is the hosting core: given a *bound* widget id (persisted in prefs), [embed] inflates the
 * `AppWidgetHostView` and drops it into the panel. Choosing/binding a widget is interactive (the
 * system bind+configure dialogs return through an Activity result), so that picker is a separate,
 * device-tested step — until an id is bound here, [LivePanelView] falls back to its kinetic clock.
 */
object LivePanelHost {

    private const val PREFS = "lunchheir_live_panel"
    private const val KEY_WIDGET_ID = "widget_id"

    fun boundWidgetId(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

    fun setBoundWidgetId(context: Context, id: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_WIDGET_ID, id).apply()
    }

    /**
     * Inflate the bound widget into [container] via the launcher's host. Returns false (so the panel
     * keeps its clock) when no widget is bound, the provider is gone, or inflation fails.
     */
    fun embed(launcher: LawnchairLauncher, container: ViewGroup): Boolean {
        val id = boundWidgetId(launcher)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return false
        val info = AppWidgetManager.getInstance(launcher).getAppWidgetInfo(id) ?: return false
        return try {
            val providerInfo = LauncherAppWidgetProviderInfo.fromProviderInfo(launcher, info)
            val view = launcher.appWidgetHolder.createView(id, providerInfo)
            container.removeAllViews()
            container.addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}
