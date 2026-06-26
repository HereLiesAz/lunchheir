package com.hereliesaz.lunchheir

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * The optional second persistent row that sits just above the live recents bar — a row of pinned
 * apps that persists across all home pages (it lives in the DragLayer, like the hotseat and the
 * recents bar).
 *
 * To stay overlay-friendly (no edits to the Launcher3 Favorites loader / drag pipeline), this row
 * keeps its own ordered list of pinned components in a private SharedPreferences and renders them
 * with [LauncherApps]. Tap launches; long-press removes. The row hides itself while empty so it
 * never steals space or touches.
 *
 * Populating it from the app drawer (drag, or a long-press "add to second row" action) is the
 * next increment; [addApp] is the entry point that wiring will call — use [get] to find the row.
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

    fun addApp(component: ComponentName) = mutate { list ->
        val s = component.flattenToString()
        if (s !in list) list.add(s)
    }

    fun removeApp(component: ComponentName) = mutate { list ->
        list.remove(component.flattenToString())
    }

    private inline fun mutate(block: (MutableList<String>) -> Unit) {
        val list = storedKeys().toMutableList()
        block(list)
        // Store as an ordered, newline-joined string: a StringSet does not preserve order, which
        // would shuffle the pinned apps. Component flatten strings never contain a newline.
        prefs.edit().putString(KEY_COMPONENTS, list.joinToString("\n")).apply()
        refresh()
    }

    private fun storedKeys(): List<String> =
        prefs.getString(KEY_COMPONENTS, "").orEmpty().split("\n").filter { it.isNotEmpty() }

    fun refresh() {
        removeAllViews()
        val user = Process.myUserHandle()
        storedKeys()
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .forEach { component ->
                // resolveActivity is a direct component lookup — lighter than scanning the full
                // getActivityList for the package on every refresh.
                val info = launcherApps?.resolveActivity(Intent().setComponent(component), user)
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

    companion object {
        private const val PREFS_NAME = "lunchheir_second_hotseat"
        private const val KEY_COMPONENTS = "components"

        /** Find the attached row within a DragLayer (or any parent) so callers can [addApp]. */
        @JvmStatic
        fun get(parent: ViewGroup): SecondHotseatRow? {
            for (i in 0 until parent.childCount) {
                (parent.getChildAt(i) as? SecondHotseatRow)?.let { return it }
            }
            return null
        }
    }
}
