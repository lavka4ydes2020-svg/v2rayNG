package com.v2ray.ang.dto

import android.graphics.drawable.Drawable

data class AppInfo(
    val appName: String,
    val packageName: String,
    val appIcon: Drawable,
    val isSystemApp: Boolean,
    var isSelected: Int = 0,
    val category: String = ""
) {
    companion object {
        // Category labels for UI grouping
        const val CAT_SOCIAL = "social"
        const val CAT_MEDIA = "media"
        const val CAT_BROWSER = "browser"
        const val CAT_FINANCE = "finance"
        const val CAT_GAMES = "games"
        const val CAT_SYSTEM = "system"
        const val CAT_OTHER = "other"

        val CATEGORY_LABELS = mapOf(
            CAT_SOCIAL to "Социальные сети",
            CAT_MEDIA to "Медиа и музыка",
            CAT_BROWSER to "Браузеры",
            CAT_FINANCE to "Банкинг",
            CAT_GAMES to "Игры",
            CAT_SYSTEM to "Системные",
            CAT_OTHER to "Остальные"
        )

        val CATEGORY_ORDER = listOf(
            CAT_SOCIAL, CAT_MEDIA, CAT_BROWSER,
            CAT_FINANCE, CAT_GAMES, CAT_SYSTEM, CAT_OTHER
        )
    }
}