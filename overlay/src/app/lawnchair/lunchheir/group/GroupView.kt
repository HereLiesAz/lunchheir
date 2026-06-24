package app.lawnchair.lunchheir.group

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

/**
 * The inline view for a [GroupInfo] on the workspace. The bind path positions it across the
 * group's cells (its span), like a widget.
 *
 * Increment 2 renders a minimal labelled placeholder so the load -> inflate -> bind pipeline for
 * groups can be proven end to end. Laying out the group's child app icons in its internal grid
 * is the next increment.
 */
class GroupView(context: Context) : FrameLayout(context) {

    private val label = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
    }

    init {
        // Semi-transparent scrim so the placeholder label is visible on light wallpapers/themes.
        setBackgroundColor(0x44000000)
        addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun bind(info: GroupInfo) {
        tag = info
        // Real groups carry a user/AI title; "Group" is only a dormant-placeholder fallback. It
        // moves to a string resource when the feature becomes user-visible.
        label.text = info.title ?: "Group"
    }

    companion object {
        @JvmStatic
        fun inflate(context: Context, parent: ViewGroup?, info: GroupInfo): GroupView =
            GroupView(context).apply { bind(info) }
    }
}
