package app.lawnchair.lunchheir

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A Lunch Heir **Live Panel**: a flat, monotone, kinetic-typography panel for the home screen, in
 * the spirit of Hax Launcher's animated "Live Panel" widgets — but updated stylistically (clean ink
 * on a translucent slab, wide-tracked type, a restrained kinetic refresh rather than constant
 * motion).
 *
 * This first Live Panel shows time + date and re-renders itself on a tick, animating only when the
 * displayed minute actually changes (cheap, battery-friendly). It is an ordinary [android.view.View]
 * (like [LiveRecentsBar]) so [LunchHeirHome] can drop it onto the DragLayer where it persists across
 * home pages. Hosting *real* app widgets as Live Panels (via QuickStep's HeadlessWidgetsManager) is
 * a later increment; the panel surface and its kinetic refresh are proven here first.
 *
 * The ticker is bound to window attach/detach so it never runs (or leaks) while off-screen.
 *
 * When the user has bound a real app widget (via [LivePanelHost]), the panel hosts that widget
 * instead of the clock, reusing the launcher's running widget host so it updates live. With no
 * widget bound it shows the kinetic clock below. Choosing/binding a widget is an interactive,
 * device-tested step; the hosting and the fallback are proven here.
 */
class LivePanelView(context: Context) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val timeFormat = SimpleDateFormat("h:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEE d MMM", Locale.getDefault())

    private val handler = Handler(Looper.getMainLooper())
    private var lastRendered: String? = null

    private val time = TextView(context).apply {
        setTextColor(HAX_PAPER)
        textSize = 48f
        letterSpacing = 0.02f
        gravity = Gravity.START
    }
    private val date = TextView(context).apply {
        setTextColor(HAX_PAPER_DIM)
        textSize = 14f
        letterSpacing = 0.25f
        gravity = Gravity.START
    }

    /** Holds a hosted app widget when one is bound; hidden (and the clock shown) otherwise. */
    private val widgetHost = FrameLayout(context).apply { visibility = GONE }

    private val tick = object : Runnable {
        override fun run() {
            render()
            // Re-align to the next ~20s boundary; the minute change is what actually animates.
            handler.postDelayed(this, TICK_MS)
        }
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        val pad = (16 * density).toInt()
        setPadding(pad, pad, pad, pad)
        // Flat monotone slab: a translucent ink panel that reads on any wallpaper.
        setBackgroundColor(SLAB)
        addView(time)
        addView(date)
        addView(widgetHost)
    }

    private fun render() {
        val now = Date()
        val timeText = timeFormat.format(now)
        if (timeText == lastRendered) return
        lastRendered = timeText
        time.text = timeText
        date.text = dateFormat.format(now).uppercase(Locale.getDefault())
        animateKinetic()
    }

    /** Restrained kinetic refresh: the time slides up a few dp and fades in on each minute change. */
    private fun animateKinetic() {
        time.translationY = 6 * density
        time.alpha = 0f
        time.animate().translationY(0f).alpha(1f).setDuration(280).start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Prefer a hosted app widget when one is bound; otherwise run the kinetic clock.
        val launcher = com.android.launcher3.Launcher.getLauncher(context) as? app.lawnchair.LawnchairLauncher
        val hosted = launcher != null && LivePanelHost.embed(launcher, widgetHost)
        time.visibility = if (hosted) GONE else VISIBLE
        date.visibility = if (hosted) GONE else VISIBLE
        widgetHost.visibility = if (hosted) VISIBLE else GONE
        if (hosted) {
            handler.removeCallbacks(tick)
        } else {
            lastRendered = null
            handler.post(tick)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(tick)
    }

    companion object {
        // Mirrors HaxShell's flat-monotone palette (ink on paper), as Android color ints.
        private val HAX_PAPER = Color.parseColor("#F4F4F4")
        private val HAX_PAPER_DIM = Color.parseColor("#B8B8B8")
        private const val SLAB = 0x66121212 // translucent ink
        private const val TICK_MS = 20_000L
    }
}
