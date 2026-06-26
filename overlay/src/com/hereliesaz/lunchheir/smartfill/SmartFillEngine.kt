package com.hereliesaz.lunchheir.smartfill

import android.content.pm.ApplicationInfo

/**
 * The always-on, on-device half of Smart Group / Smart Folder auto-fill.
 *
 * Given the apps already in a collection (the *members* — initially the user's seeds) plus an
 * optional title and the set of installed *candidate* apps, it scores each candidate on how well it
 * fits the collection's emerging pattern, derives a best-guess title, and runs a self-reinforcing
 * loop so the title and the membership keep informing each other:
 *
 *   members ──suggestTitle──▶ title ──evaluate──▶ new members ──▶ (repeat) …
 *
 * Pure Kotlin, no Android lookups (only the [ApplicationInfo] category *constants*), so it is cheap
 * to run continuously — on every install/uninstall and every membership/title edit — and trivial to
 * reason about. An adapter resolves [AppSignals] from Lawnchair's model and calls [converge]; the
 * optional cloud refiner (any AI provider) layers on top of this baseline, it does not replace it.
 *
 * Dormant until increment 2 wires the adapter — instantiated nowhere yet, so it changes no behavior.
 */
class SmartFillEngine(private val config: Config = Config()) {

    /**
     * Tunables. Weights are relative; [threshold] is the minimum normalized score (0..1) for a
     * candidate to be considered a match. [categoryName] resolves a category int to a display label
     * for title suggestion — defaulting to a small English map so the engine stays context-free; an
     * adapter can pass one backed by [ApplicationInfo.getCategoryTitle].
     */
    data class Config(
        val categoryWeight: Double = 0.45,
        val installerWeight: Double = 0.25,
        val titleWeight: Double = 0.20,
        val labelWeight: Double = 0.10,
        val threshold: Double = 0.35,
        val maxIterations: Int = 6,
        val categoryName: (Int) -> String? = ::defaultCategoryName,
    )

    /** A candidate the engine believes belongs, with its score and a short human-readable reason. */
    data class Match(val app: AppSignals, val score: Double, val reason: String)

    /**
     * The outcome of a [converge] run: the grown membership, a suggested title (null if the engine
     * has no confident guess), and the matches it admitted (for UI explanation / undo).
     */
    data class Result(
        val members: List<AppSignals>,
        val suggestedTitle: String?,
        val admitted: List<Match>,
    )

    /**
     * One scoring pass: rank [candidates] (assumed to exclude current [members]) by fit against the
     * pattern implied by [members] + [title]. Returns only candidates at or above the threshold,
     * best first. A pass with no members and no title returns nothing — there's no pattern yet.
     */
    fun evaluate(members: Collection<AppSignals>, candidates: Collection<AppSignals>, title: String?): List<Match> {
        if (members.isEmpty() && title.isNullOrBlank()) return emptyList()

        val n = members.size.coerceAtLeast(1)
        val categoryCounts = members.filter { it.category != ApplicationInfo.CATEGORY_UNDEFINED }
            .groupingBy { it.category }.eachCount()
        val installerCounts = members.mapNotNull { it.installer }
            .groupingBy { it }.eachCount()
        val titleTokens = tokenize(title)
        val memberLabelTokens = members.flatMap { tokenize(it.label) }
            .groupingBy { it }.eachCount()

        // Normalize by the weight of the signals that are even *applicable* in this context, so a
        // strong match on the few active signals can cross the threshold. Without this, seeding a
        // group from only a title (no members yet) caps every score at titleWeight (0.20) < the
        // 0.35 threshold and the title->apps flow could never admit anything. With all signals
        // active this is a no-op (the weights sum to 1.0).
        val activeWeights =
            (if (categoryCounts.isNotEmpty()) config.categoryWeight else 0.0) +
                (if (installerCounts.isNotEmpty()) config.installerWeight else 0.0) +
                (if (titleTokens.isNotEmpty()) config.titleWeight else 0.0) +
                (if (memberLabelTokens.isNotEmpty()) config.labelWeight else 0.0)
        val scale = if (activeWeights > 0.0) 1.0 / activeWeights else 1.0

        return candidates.mapNotNull { app ->
            var score = 0.0
            val reasons = ArrayList<String>(2)

            if (app.category != ApplicationInfo.CATEGORY_UNDEFINED) {
                categoryCounts[app.category]?.let { c ->
                    score += config.categoryWeight * (c.toDouble() / n)
                    reasons += "same category"
                }
            }
            if (app.installer != null) {
                installerCounts[app.installer]?.let { c ->
                    score += config.installerWeight * (c.toDouble() / n)
                    reasons += "same source"
                }
            }
            val appTokens = tokenize(app.label)
            if (titleTokens.isNotEmpty() && appTokens.isNotEmpty()) {
                val overlap = appTokens.count { it in titleTokens }.toDouble() / appTokens.size
                if (overlap > 0) {
                    score += config.titleWeight * overlap
                    reasons += "matches title"
                }
            }
            if (memberLabelTokens.isNotEmpty() && appTokens.isNotEmpty()) {
                val shared = appTokens.count { memberLabelTokens.containsKey(it) }.toDouble() / appTokens.size
                if (shared > 0) {
                    score += config.labelWeight * shared
                    reasons += "name in common"
                }
            }

            if (score >= config.threshold) {
                Match(app, score.coerceAtMost(1.0), reasons.joinToString(", "))
            } else {
                null
            }
        }.sortedByDescending { it.score }
    }

    /**
     * Best-guess title for a collection from its [members]. Prefers a dominant category name
     * (covering at least half the members), then a label token shared by most members (e.g. "Adobe"
     * across "Adobe Acrobat" / "Adobe Lightroom"), else null. The caller decides whether to apply
     * it or keep a user-set title — a user title is treated as a steering hint, not overwritten.
     */
    fun suggestTitle(members: Collection<AppSignals>): String? {
        if (members.isEmpty()) return null

        val categoryCounts = members.filter { it.category != ApplicationInfo.CATEGORY_UNDEFINED }
            .groupingBy { it.category }.eachCount()
        categoryCounts.maxByOrNull { it.value }?.let { (cat, count) ->
            if (count * 2 >= members.size) config.categoryName(cat)?.let { return it }
        }

        val tokenInMembers = HashMap<String, Int>()
        for (m in members) {
            for (t in tokenize(m.label)) tokenInMembers[t] = (tokenInMembers[t] ?: 0) + 1
        }
        tokenInMembers.entries
            .filter { it.value * 2 > members.size && it.key.length >= 3 }
            .maxByOrNull { it.value }
            ?.let { return it.key.replaceFirstChar { c -> c.uppercase() } }

        return null
    }

    /**
     * The self-reinforcing loop. Starting from [seeds] + an optional [providedTitle], repeatedly:
     * derive a working title, score the remaining candidates, admit those above threshold, and feed
     * them back as members — so each accepted app sharpens the pattern for the next pass. Stops when
     * a pass admits nothing or after [Config.maxIterations] (a guard against pathological growth).
     *
     * A user-provided title steers but is never replaced; with none, the engine's own suggestion is
     * used as the working title each round and surfaced in [Result.suggestedTitle].
     */
    fun converge(seeds: List<AppSignals>, candidates: List<AppSignals>, providedTitle: String? = null): Result {
        val members = LinkedHashMap<String, AppSignals>().apply { seeds.forEach { put(it.key, it) } }
        val pool = LinkedHashMap<String, AppSignals>().apply {
            candidates.forEach { if (!members.containsKey(it.key)) put(it.key, it) }
        }
        val admitted = ArrayList<Match>()
        val userTitle = providedTitle?.takeUnless { it.isBlank() }

        var iterations = 0
        while (iterations++ < config.maxIterations && pool.isNotEmpty()) {
            val workingTitle = userTitle ?: suggestTitle(members.values)
            val matches = evaluate(members.values, pool.values, workingTitle)
            if (matches.isEmpty()) break
            for (m in matches) {
                members[m.app.key] = m.app
                pool.remove(m.app.key)
                admitted += m
            }
        }

        val finalSuggestion = userTitle ?: suggestTitle(members.values)
        return Result(members.values.toList(), finalSuggestion, admitted)
    }

    companion object {
        /** Split a label/title into lowercased word tokens, dropping noise tokens. */
        internal fun tokenize(text: String?): Set<String> {
            if (text.isNullOrBlank()) return emptySet()
            return text.split(Regex("[^\\p{L}\\p{N}]+"))
                .map { it.lowercase() }
                .filter { it.length >= 2 && it !in STOPWORDS }
                .toSet()
        }

        private val STOPWORDS = setOf("the", "and", "for", "app", "free", "pro", "lite", "go", "my")

        /**
         * Context-free fallback names for the common [ApplicationInfo] categories. An adapter with a
         * Context can override [Config.categoryName] with [ApplicationInfo.getCategoryTitle] for
         * localized labels.
         */
    }
}

/**
 * Context-free fallback names for the common [ApplicationInfo] categories. Top-level so it resolves
 * cleanly as a default in [SmartFillEngine.Config]; an adapter with a Context can instead pass
 * [ApplicationInfo.getCategoryTitle] for localized labels.
 */
internal fun defaultCategoryName(category: Int): String? = when (category) {
    ApplicationInfo.CATEGORY_GAME -> "Games"
    ApplicationInfo.CATEGORY_AUDIO -> "Audio"
    ApplicationInfo.CATEGORY_VIDEO -> "Video"
    ApplicationInfo.CATEGORY_IMAGE -> "Photos"
    ApplicationInfo.CATEGORY_SOCIAL -> "Social"
    ApplicationInfo.CATEGORY_NEWS -> "News"
    ApplicationInfo.CATEGORY_MAPS -> "Maps"
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
    ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Accessibility"
    else -> null
}
