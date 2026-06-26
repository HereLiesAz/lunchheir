package app.lawnchair.lunchheir

import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.InsettableFrameLayout
import com.android.quickstep.RecentsModel

/**
 * Entry point for Lunch Heir home-screen extensions (the live recents bar, the Hax menu, the
 * second persistent row, the Live Panel, groups, and the monochrome shell).
 *
 * It is invoked from a single-line hook in [LawnchairLauncher.onCreate] (applied to the
 * pristine upstream submodule by overlay/apply_overlay.py) so that all Lunch Heir logic
 * lives here in the overlay and the upstream edit stays one line — keeping the fork easy
 * to rebase onto new Lawnchair releases.
 *
 * Surfaces are attached to the launcher's [com.android.launcher3.dragndrop.DragLayer] so they
 * persist across all home pages. IMPORTANT: the DragLayer is an [InsettableFrameLayout], whose
 * `generateLayoutParams` rebuilds any foreign `FrameLayout.LayoutParams` through a
 * `ViewGroup.LayoutParams` copy constructor that DROPS the gravity field — so a plain
 * `FrameLayout.LayoutParams(.., Gravity.BOTTOM)` lands top-left. Every view added here therefore
 * uses an [InsettableFrameLayout.LayoutParams] (which `checkLayoutParams` accepts as-is) so the
 * gravity we set actually survives. See [attachToDragLayer].
 */
object LunchHeirHome {
    private const val TAG = "LunchHeirHome"

    @JvmStatic
    fun onCreate(launcher: LawnchairLauncher) {
        val rowHeightPx = (56 * launcher.resources.displayMetrics.density).toInt()

        // Cache the nested-folders accept flag for the context-free FolderInfo seam (drag accept).
        app.lawnchair.lunchheir.folder.NestedFolders.refresh(launcher)

        // Bottom row: the Hax menu trigger (start) and the live recents bar (filling the rest) share
        // ONE row docked to the bottom — so the menu button sits in the recents/dock area, not
        // floating at the top. Each half gates on its own toggle; with both off the row is skipped.
        val hasBottomRow = setupBottomRow(launcher, rowHeightPx)

        if (LunchHeirPrefs.isEnabled(launcher, LunchHeirPrefs.Feature.SECOND_ROW)) {
            try {
                // Sit directly above the bottom row when there is one; otherwise dock to the very
                // bottom so there's no empty gap below the second row.
                val secondRowMargin = if (hasBottomRow) rowHeightPx else 0
                SecondHotseatRow(launcher).also {
                    attachToDragLayer(
                        launcher,
                        it,
                        InsettableFrameLayout.LayoutParams.MATCH_PARENT,
                        rowHeightPx,
                        Gravity.BOTTOM,
                        bottomMargin = secondRowMargin,
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "could not attach second hotseat row", e)
            }
        }

        if (LunchHeirPrefs.isEnabled(launcher, LunchHeirPrefs.Feature.LIVE_PANEL)) {
            // The Live Panel: a flat monotone kinetic clock/status slab, top-start, clear of the
            // bottom rows. Placement is intentionally simple pending on-device tuning.
            try {
                val panel = LivePanelView(launcher)
                val margin = (24 * launcher.resources.displayMetrics.density).toInt()
                attachToDragLayer(
                    launcher,
                    panel,
                    InsettableFrameLayout.LayoutParams.WRAP_CONTENT,
                    InsettableFrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START,
                    topMargin = margin,
                    startMargin = margin,
                )
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

        // Hax monochrome shell: desaturate the whole launcher UI. Self-gates on the MONOCHROME
        // toggle (sets the grayscale layer when on, clears it when off), so it's safe to call always.
        try {
            app.lawnchair.lunchheir.theme.MonochromeShell.apply(launcher)
        } catch (e: Exception) {
            Log.w(TAG, "could not apply monochrome shell", e)
        }

        Log.d(TAG, "Lunch Heir home extensions initialized")
    }

    /**
     * Build and attach the bottom row: `[ Hax menu | live recents bar ]`. The menu trigger (an
     * AzNavRail dropdown) takes its natural width at the start; the recents bar takes the remaining
     * width. Either half can be absent depending on its toggle.
     */
    private fun setupBottomRow(launcher: LawnchairLauncher, rowHeightPx: Int): Boolean {
        val haxEnabled = LunchHeirPrefs.isEnabled(launcher, LunchHeirPrefs.Feature.HAX_MENU)
        val recentsEnabled = LunchHeirPrefs.isEnabled(launcher, LunchHeirPrefs.Feature.LIVE_RECENTS_BAR)
        if (!haxEnabled && !recentsEnabled) return false

        val row = LinearLayout(launcher).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        if (haxEnabled) {
            try {
                val menu = HaxShell.createMenuView(launcher)
                row.addView(
                    menu,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            } catch (e: Exception) {
                Log.w(TAG, "could not attach hax menu", e)
            }
        }

        if (recentsEnabled) {
            try {
                val recentsBar = LiveRecentsBar(launcher)
                // weight 1, width 0 → fill whatever the menu leaves.
                row.addView(
                    recentsBar,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f),
                )
                bindRecents(launcher, recentsBar)
            } catch (e: Exception) {
                Log.w(TAG, "could not attach live recents bar", e)
            }
        }

        if (row.childCount == 0) return false
        attachToDragLayer(
            launcher,
            row,
            InsettableFrameLayout.LayoutParams.MATCH_PARENT,
            rowHeightPx,
            Gravity.BOTTOM,
        )
        return true
    }

    /** Bind a [LiveRecentsBar] to QuickStep's [RecentsModel], listening only while home is visible. */
    private fun bindRecents(launcher: LawnchairLauncher, recentsBar: LiveRecentsBar) {
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

    /**
     * Add [view] to the DragLayer with an [InsettableFrameLayout.LayoutParams] so the [gravity] is
     * preserved (a plain `FrameLayout.LayoutParams` would be regenerated and lose it — see the
     * class kdoc).
     */
    private fun attachToDragLayer(
        launcher: LawnchairLauncher,
        view: View,
        width: Int,
        height: Int,
        gravity: Int,
        bottomMargin: Int = 0,
        topMargin: Int = 0,
        startMargin: Int = 0,
    ) {
        val lp = InsettableFrameLayout.LayoutParams(width, height)
        lp.gravity = gravity
        lp.bottomMargin = bottomMargin
        lp.topMargin = topMargin
        lp.marginStart = startMargin
        launcher.dragLayer.addView(view, lp)
    }
}
