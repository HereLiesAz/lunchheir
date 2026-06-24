package app.lawnchair.lunchheir.smartfill

import android.content.Context

/**
 * Facade tying the candidate source ([InstalledAppsSource]) to the on-device engine
 * ([SmartFillEngine]) — the one call site a Smart Group / Smart Folder uses to (re)derive its
 * membership and title from its current seeds.
 *
 * This is the *baseline* path: pure on-device, free, private, offline. When the user has opted into
 * the cloud refiner (consent toggle + a configured [AiProvider] with a key), increment 3 layers a
 * `SmartFillRemote` pass on top of this result; absent that, this stands alone.
 *
 * Dormant in increment 2 — called nowhere yet. Wiring it to run continuously (on install/uninstall
 * and on membership/title edits) lands with the Smart-group create flow + its Room-backed config.
 */
object SmartFill {

    /**
     * Suggest a membership + title for a collection from its current [seeds] and an optional
     * user-set [title], scoring against everything installed. A user title steers but is preserved.
     */
    fun suggest(
        context: Context,
        seeds: List<AppSignals>,
        title: String? = null,
        engine: SmartFillEngine = SmartFillEngine(),
    ): SmartFillEngine.Result {
        val candidates = InstalledAppsSource(context).all()
        return engine.converge(seeds, candidates, title)
    }
}
