package com.hereliesaz.lunchheir.group

import android.content.Context

/**
 * Remembers which groups are **smart** (auto-filling) so they can be re-evaluated continuously as
 * apps are installed — the "not one-shot" half of the spec. Keyed by the group's workspace item id.
 *
 * A tiny SharedPreferences set, overlay-contained. (Group metadata will eventually live in a Room
 * side-table alongside Lawnchair's; this keeps the smart-fill loop shippable without that schema.)
 */
class SmartGroupRegistry(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun markSmart(groupId: Int) {
        prefs.edit().putStringSet(KEY_IDS, ids() + groupId.toString()).apply()
    }

    fun forget(groupId: Int) {
        prefs.edit().putStringSet(KEY_IDS, ids() - groupId.toString()).apply()
    }

    fun isSmart(groupId: Int): Boolean = groupId.toString() in ids()

    fun isEmpty(): Boolean = ids().isEmpty()

    private fun ids(): Set<String> = prefs.getStringSet(KEY_IDS, emptySet()) ?: emptySet()

    companion object {
        private const val PREFS_NAME = "lunchheir_smart_groups"
        private const val KEY_IDS = "smart_group_ids"
    }
}
