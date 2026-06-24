package app.lawnchair.lunchheir.smartfill

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    /**
     * Suggest a membership + title for a collection from its current [seeds] and an optional
     * user-set [title], scoring against everything installed. A user title steers but is preserved.
     *
     * Always computes the on-device baseline first. If the user has consented to a cloud refiner and
     * configured a provider ([SmartFillConfig]), it then asks that backend to refine the result and
     * uses it when it succeeds; on any failure it silently keeps the on-device baseline.
     */
    suspend fun suggest(
        context: Context,
        seeds: List<AppSignals>,
        title: String? = null,
        engine: SmartFillEngine = SmartFillEngine(),
    ): SmartFillEngine.Result = withContext(Dispatchers.IO) {
        // IO dispatcher: all() makes per-app binder IPC; the converge() scoring is light CPU on top.
        val candidates = InstalledAppsSource(context).all()
        val baseline = engine.converge(seeds, candidates, title)

        val provider = SmartFillConfig(context).provider()
            ?: return@withContext baseline
        SmartFillRemote(provider).refine(seeds, candidates, title) ?: baseline
    }
}
