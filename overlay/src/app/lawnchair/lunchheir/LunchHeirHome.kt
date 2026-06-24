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
                    Log.d(TAG, "recent tasks changed: ${tasks.size} task group(s)")
                }
            }
        })

        Log.d(TAG, "Lunch Heir home extensions initialized")
    }
}
