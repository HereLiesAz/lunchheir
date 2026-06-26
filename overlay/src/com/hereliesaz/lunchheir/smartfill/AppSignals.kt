package com.hereliesaz.lunchheir.smartfill

import android.content.pm.ApplicationInfo

/**
 * The signals the on-device [SmartFillEngine] scores an app against. This is a flat, context-free
 * value type deliberately: the engine stays pure (no Android lookups), so an adapter resolves these
 * once from Lawnchair's model and feeds them in.
 *
 * @param key       a stable identity for the app (component key / package+user); used for set math.
 * @param packageName the app's package, e.g. "com.adobe.reader".
 * @param label     the user-visible name, e.g. "Adobe Acrobat".
 * @param category  [ApplicationInfo.category] (CATEGORY_GAME, _PRODUCTIVITY, ...), or
 *                  [ApplicationInfo.CATEGORY_UNDEFINED] (-1) when unknown.
 * @param installer the installing package (e.g. "com.android.vending"); a coarse developer proxy.
 */
data class AppSignals(
    val key: String,
    val packageName: String,
    val label: String,
    val category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
    val installer: String? = null,
)
