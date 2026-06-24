package app.lawnchair.lunchheir

import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.lawnchair.LawnchairLauncher
import com.android.quickstep.RecentsModel

/**
 * Entry point for Lunch Heir home-screen extensions (the live recents bar and, later, the
 * second persistent row / sections).
 *
 * It is invoked from a single-line hook in [LawnchairLauncher.onCreate] (applied to the
 * pristine upstream submodule by overlay/apply_overlay.py) so that all Lunch Heir logic
 * lives here in the overlay and the upstream edit stays one line — keeping the fork easy
 * to rebase onto new Lawnchair releases.
 *
 * It attaches the [LiveRecentsBar] to the DragLayer (so it persists across all home pages) and
 * keeps it bound to the QuickStep [RecentsModel] while the launcher is visible.
 */
object LunchHeirHome {
    private const val TAG = "LunchHeirHome"

    @JvmStatic
    fun onCreate(launcher: LawnchairLauncher) {
        val recentsModel = RecentsModel.INSTANCE.get(launcher)
        val rowHeightPx = (56 * launcher.resources.displayMetrics.density).toInt()

        // Both rows live in the DragLayer (outside the paged Workspace), so they persist across
        // every home-screen page for free. The recents bar pins to the very bottom; the optional
        // second pinned row sits directly above it. Guard creation so a failure can't crash home.
        val recentsBar = try {
            LiveRecentsBar(launcher).also { attachBottomRow(launcher, it, rowHeightPx, bottomMarginPx = 0) }
        } catch (e: Exception) {
            Log.w(TAG, "could not attach live recents bar", e)
            null
        }

        try {
            SecondHotseatRow(launcher).also {
                attachBottomRow(launcher, it, rowHeightPx, bottomMarginPx = rowHeightPx)
            }
        } catch (e: Exception) {
            Log.w(TAG, "could not attach second hotseat row", e)
        }

        // Only listen while the launcher is actually visible: registering for the whole
        // create..destroy span keeps firing getTasks() binder IPC in the background (wasting
        // CPU/battery). Bind register/unregister to start/stop, and query once on start so the
        // bar is up to date when the user returns home. QuickStep calls back on the UI thread.
        launcher.lifecycle.addObserver(object : DefaultLifecycleObserver {
            private val listener = RecentsModel.RecentTasksChangedListener { queryTasks() }

            override fun onStart(owner: LifecycleOwner) {
                recentsModel.registerRecentTasksChangedListener(listener)
                queryTasks()
            }

            override fun onStop(owner: LifecycleOwner) {
                recentsModel.unregisterRecentTasksChangedListener(listener)
            }

            private fun queryTasks() {
                recentsModel.getTasks { tasks ->
                    recentsBar?.setTasks(tasks)
                }
            }
        })

        Log.d(TAG, "Lunch Heir home extensions initialized")
    }

    private fun attachBottomRow(launcher: LawnchairLauncher, row: View, heightPx: Int, bottomMarginPx: Int) {
        // Stack rows up from the bottom. Fine-tuning against the hotseat and gesture-nav insets is
        // a follow-up; for now this proves the integration end to end.
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            heightPx,
            Gravity.BOTTOM,
        ).apply { bottomMargin = bottomMarginPx }
        launcher.dragLayer.addView(row, lp)
    }
}
