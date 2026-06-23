package app.lawnchair.lunchheir

import android.util.Log
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
 * This first increment is a deliberately small "walking skeleton": it wires up the QuickStep
 * [RecentsModel] subscription that the live recents bar is built on, and ties it to the
 * launcher lifecycle. Its real purpose is to verify, on CI, that overlay source compiles and
 * links against upstream and that the patch hook fires. The bar's view + gestures land in the
 * next increment.
 */
object LunchHeirHome {
    private const val TAG = "LunchHeirHome"

    @JvmStatic
    fun onCreate(launcher: LawnchairLauncher) {
        val recentsModel = RecentsModel.INSTANCE.get(launcher)

        // QuickStep notifies on the UI thread when the recent-task list changes; re-query and
        // (for now) just log. The live recents bar will rebind its icons from this callback.
        val listener = RecentsModel.RecentTasksChangedListener {
            recentsModel.getTasks { tasks ->
                Log.d(TAG, "recent tasks changed: ${tasks.size} task group(s)")
            }
        }
        recentsModel.registerRecentTasksChangedListener(listener)

        launcher.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                recentsModel.unregisterRecentTasksChangedListener(listener)
            }
        })

        Log.d(TAG, "Lunch Heir home extensions initialized")
    }
}
