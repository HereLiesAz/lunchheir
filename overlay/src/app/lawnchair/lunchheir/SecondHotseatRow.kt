package app.lawnchair.lunchheir

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * The optional second persistent row that sits just above the live recents bar — a row of pinned
 * apps that persists across all home pages (it lives in the DragLayer, like the hotseat and the
 * recents bar).
 *
 * To stay overlay-friendly (no edits to the Launcher3 Favorites loader / drag pipeline), this row
 * keeps its own list of pinned components in a private SharedPreferences and renders them with
 * [LauncherApps]. Tap launches; long-press removes. The row hides itself while empty so it never
 * steals space or touches.
 *
 * Populating it from the app drawer (drag, or a long-press "add to second row" action) is the
 * next increment; [addApp] is the entry point that wiring will call.
 */
class SecondHotseatRow(context: Context) : LinearLayout(context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val density = resources.displayMetrics.density
    private val iconSizePx = (44 * density).toInt()
    private val iconMarginPx = (8 * density).toInt()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        refresh()
    }

    fun addApp(component: ComponentName) = mutate { it.add(component.flattenToString()) }

    fun removeApp(component: ComponentName) = mutate { it.remove(component.flattenToString()) }

    private inline fun mutate(block: (MutableSet<String>) -> Unit) {
        val set = prefs.getStringSet(KEY_COMPONENTS, emptySet())!!.toMutableSet()
        block(set)
        prefs.edit().putStringSet(KEY_COMPONENTS, set).apply()
        refresh()
    }

    fun refresh() {
        removeAllViews()
        val user = Process.myUserHandle()
        prefs.getStringSet(KEY_COMPONENTS, emptySet())!!
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .forEach { component ->
                val info = launcherApps
                    ?.getActivityList(component.packageName, user)
                    ?.firstOrNull { it.componentName == component }
                    ?: return@forEach
                addView(
                    ImageView(context).apply {
                        layoutParams = LayoutParams(iconSizePx, iconSizePx).apply {
                            marginStart = iconMarginPx
                            marginEnd = iconMarginPx
                        }
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(info.getIcon(resources.displayMetrics.densityDpi))
                        contentDescription = info.label
                        setOnClickListener { launcherApps?.startMainActivity(component, user, null, null) }
                        setOnLongClickListener { removeApp(component); true }
                    },
                )
            }
        visibility = if (childCount == 0) View.GONE else View.VISIBLE
    }

    private companion object {
        const val PREFS_NAME = "lunchheir_second_hotseat"
        const val KEY_COMPONENTS = "components"
    }
}
