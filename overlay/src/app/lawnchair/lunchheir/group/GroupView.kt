package app.lawnchair.lunchheir.group

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView

/**
 * The inline view for a [GroupInfo] on the workspace. The bind path positions it across the group's
 * cells (its span), like a widget — and, unlike a folder, it never collapses: its children render
 * inline here.
 *
 * Increment 3 lays the group's child app icons out in an internal grid (columns following the
 * group's [GroupInfo.spanX]), drawing each from its loaded [com.android.launcher3.icons.BitmapInfo]
 * — no coupling to the Launcher3 loader, so it stays additive/dormant. A header label shows the
 * group's title; when the group is empty it falls back to a centered placeholder.
 *
 * The group **drags as a unit**: ItemInflater wires this view to the workspace long-click/drag
 * listener, and each child icon forwards its long-press here, so the whole group can be picked up
 * and reordered like any workspace item (the drop persists via the standard ModelWriter path).
 */
class GroupView(context: Context) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private val iconSizePx = (48 * density).toInt()
    private val iconPadPx = (4 * density).toInt()

    private val label = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
    }
    private val grid = GridLayout(context)

    init {
        // Semi-transparent scrim so icons/label read on light wallpapers/themes.
        setBackgroundColor(0x44000000)
        addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun bind(info: GroupInfo) {
        tag = info

        val apps = info.getAppContents()
        grid.removeAllViews()
        grid.columnCount = info.spanX.coerceAtLeast(1)

        for (item in apps) {
            val icon = ImageView(context).apply {
                setPadding(iconPadPx, iconPadPx, iconPadPx, iconPadPx)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = iconSizePx
                    height = iconSizePx
                }
                contentDescription = item.title
                // Draw straight from the loaded bitmap; no icon-cache round-trip needed here.
                item.bitmap?.let { setImageDrawable(it.newIcon(context)) }
                setOnClickListener { item.intent?.let { intent -> context.startActivity(intent) } }
                // Long-press an icon drags the whole group as a unit (forwarded to the GroupView's
                // own long-click listener, which ItemInflater sets to the workspace drag listener).
                setOnLongClickListener { this@GroupView.performLongClick() }
            }
            grid.addView(icon)
        }

        // The label is a fallback only: title header when empty/untitled, hidden once icons show.
        val empty = apps.isEmpty()
        label.visibility = if (empty) VISIBLE else GONE
        grid.visibility = if (empty) GONE else VISIBLE
        label.text = info.title ?: "Group"
    }

    companion object {
        @JvmStatic
        fun inflate(context: Context, parent: ViewGroup?, info: GroupInfo): GroupView =
            GroupView(context).apply { bind(info) }
    }
}
