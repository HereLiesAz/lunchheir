package com.hereliesaz.lunchheir

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import com.android.quickstep.RecentsModel
import com.android.quickstep.util.GroupTask
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.system.ActivityManagerWrapper
import kotlin.math.abs

/**
 * The Lunch Heir live recents bar: a horizontal row of recent-app icons along the bottom of the
 * home screen, fed by QuickStep's [RecentsModel]. Tap an icon to launch the task; swipe up on it
 * to remove it from recents. The goal is to make the system overview screen unnecessary.
 *
 * Increment 2: the view + its bind/launch/dismiss behavior. It is attached to the DragLayer by
 * [LunchHeirHome]. Exact placement vs. the hotseat / gesture-nav insets is left for on-device
 * tuning (the bar currently pins to the very bottom).
 */
class LiveRecentsBar(context: Context) : HorizontalScrollView(context) {

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val iconCache = RecentsModel.INSTANCE.get(context).iconCache
    private val density = resources.displayMetrics.density
    private val iconSizePx = (44 * density).toInt()
    private val iconMarginPx = (6 * density).toInt()
    private val flingThreshold = (600 * density)

    init {
        isHorizontalScrollBarEnabled = false
        clipChildren = false
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /**
     * Rebind the row to the current recent tasks (RecentsModel returns least-recent first).
     * Existing icon views are reused by task id (kept in the view tag) so a rebind doesn't churn
     * views / reload icons / flicker — only genuinely new tasks create a view.
     */
    fun setTasks(groupTasks: List<GroupTask>) {
        val existing = (0 until row.childCount)
            .map { row.getChildAt(it) }
            .associateBy { it.tag as? Int }
        row.removeAllViews()
        groupTasks.asReversed().forEach { group ->
            val task = group.tasks.firstOrNull() ?: return@forEach
            row.addView(existing[task.key.id] ?: createTaskIcon(task))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createTaskIcon(task: Task): View {
        val iconView = ImageView(context).apply {
            tag = task.key.id
            layoutParams = LinearLayout.LayoutParams(iconSizePx, iconSizePx).apply {
                marginStart = iconMarginPx
                marginEnd = iconMarginPx
                gravity = Gravity.CENTER_VERTICAL
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        // getIconInBackground dispatches its callback on the UI thread, so update directly.
        iconCache.getIconInBackground(task) { icon, contentDescription, _ ->
            iconView.setImageDrawable(icon)
            iconView.contentDescription = contentDescription
        }

        // Launch via a real OnClickListener (so TalkBack works); the gesture detector routes a
        // tap through performClick() and treats an upward fling as dismiss. onDown must return
        // true or the detector never sees the tap/fling. Other events return false so the
        // HorizontalScrollView can still scroll the row.
        iconView.setOnClickListener { launchTask(task) }
        val detector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    iconView.performClick()
                    return true
                }

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float,
                ): Boolean {
                    if (velocityY < -flingThreshold && abs(velocityY) > abs(velocityX)) {
                        dismissTask(task, iconView)
                        return true
                    }
                    return false
                }
            },
        )
        iconView.setOnTouchListener { _, event -> detector.onTouchEvent(event) }
        return iconView
    }

    private fun launchTask(task: Task) {
        ActivityManagerWrapper.getInstance().startActivityFromRecents(task.key.id, null)
    }

    private fun dismissTask(task: Task, view: View) {
        ActivityManagerWrapper.getInstance().removeTask(task.key.id)
        row.removeView(view)
    }
}
