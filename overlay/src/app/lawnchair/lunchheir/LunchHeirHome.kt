package app.lawnchair.lunchheir

import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
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
        val rowHeightPx = (56 * launcher.resources.displayMetrics.density).toInt()

        // Cache the nested-folders accept flag for the context-free FolderInfo seam (drag accept).
        app.lawnchair.lunchheir.folder.NestedFolders.refresh(launcher)

        // Each feature gates on its own toggle. With all of them off, nothing below is attached and
        // the user is left with plain Lawnchair (there is no master switch by design).
        if (LunchHeirPrefs.isEnabled(launcher, LunchHeirPrefs.Feature.LIVE_RECENTS_BAR)) {
            setupLiveRecentsBar(launcher, rowHeightPx)
        }

        if (LunchHeirPrefs.isEnabled(launcher, LunchHeirPrefs.Feature.SECOND_ROW)) {
            try {
                SecondHotseatRow(launcher).also {
                    attachBottomRow(launcher, it, rowHeightPx, bottomMarginPx = rowHeightPx)
                }
            } catch (e: Exception) {
                Log.w(TAG, "could not attach second hotseat row", e)
            }
        }

        if (LunchHeirPrefs.isEnabled(launcher, LunchHeirPrefs.Feature.HAX_MENU)) {
            // The Hax menu button: tap to summon the AzNavRail-based menu. Bottom-start corner,
            // above the two rows.
            try {
                val menuButton = TextView(launcher).apply {
                    text = "≡"
                    textSize = 28f
                    val pad = (12 * launcher.resources.displayMetrics.density).toInt()
                    setPadding(pad, pad, pad, pad)
                    setOnClickListener { HaxShell.show(launcher) }
                }
                val lp = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.START,
                ).apply { bottomMargin = rowHeightPx * 2 }
                launcher.dragLayer.addView(menuButton, lp)
            } catch (e: Exception) {
                Log.w(TAG, "could not attach hax menu button", e)
            }
        }

        if (LunchHeirPrefs.isEnabled(launcher, LunchHeirPrefs.Feature.LIVE_PANEL)) {
            // The Live Panel: a flat monotone kinetic clock/status slab, top-start, clear of the
            // bottom rows and menu button. Placement is intentionally simple pending on-device tuning.
            try {
                val panel = LivePanelView(launcher)
                val margin = (24 * launcher.resources.displayMetrics.density).toInt()
                val lp = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START,
                ).apply {
                    topMargin = margin
                    marginStart = margin
                }
                launcher.dragLayer.addView(panel, lp)
            } catch (e: Exception) {
                Log.w(TAG, "could not attach live panel", e)
            }
        }

        if (LunchHeirPrefs.isEnabled(launcher, LunchHeirPrefs.Feature.GROUPS)) {
            // Keep smart groups absorbing newly installed apps. Bound to start/stop so the
            // LauncherApps callback is only registered while home is active.
            try {
                val monitor = app.lawnchair.lunchheir.group.GroupAppMonitor(launcher)
                launcher.lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) = monitor.start()
                    override fun onStop(owner: LifecycleOwner) = monitor.stop()
                })
            } catch (e: Exception) {
                Log.w(TAG, "could not start group app monitor", e)
            }
        }

        Log.d(TAG, "Lunch Heir home extensions initialized")
    }

    private fun setupLiveRecentsBar(launcher: LawnchairLauncher, rowHeightPx: Int) {
        // The bar lives in the DragLayer (outside the paged Workspace), so it persists across every
        // home page for free. Guard creation so a failure can't crash home.
        val recentsBar = try {
            LiveRecentsBar(launcher).also { attachBottomRow(launcher, it, rowHeightPx, bottomMarginPx = 0) }
        } catch (e: Exception) {
            Log.w(TAG, "could not attach live recents bar", e)
            return
        }
        val recentsModel = RecentsModel.INSTANCE.get(launcher)

        // Only listen while the launcher is actually visible: registering for the whole
        // create..destroy span keeps firing getTasks() binder IPC in the background (wasting
        // CPU/battery). Bind register/unregister to start/stop, and query once on start so the bar
        // is up to date when the user returns home. QuickStep calls back on the UI thread.
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
                recentsModel.getTasks { tasks -> recentsBar.setTasks(tasks) }
            }
        })
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
