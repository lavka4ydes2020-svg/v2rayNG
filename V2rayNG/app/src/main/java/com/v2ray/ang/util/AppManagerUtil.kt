package com.v2ray.ang.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.v2ray.ang.dto.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppManagerUtil {
    /**
     * Load the list of network applications.
     *
     * @param context The context to use.
     * @return A list of AppInfo objects representing the network applications.
     */
    suspend fun loadNetworkAppList(context: Context): ArrayList<AppInfo> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val packages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            val apps = ArrayList<AppInfo>()

            for (pkg in packages) {
                val applicationInfo = pkg.applicationInfo ?: continue

                val appName = applicationInfo.loadLabel(packageManager).toString()
                val appIcon = applicationInfo.loadIcon(packageManager) ?: continue
                val isSystemApp = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM > 0

                // Detect app category for grouping
                val category = detectCategory(pkg.packageName, appName, isSystemApp)

                val appInfo = AppInfo(appName, pkg.packageName, appIcon, isSystemApp, 0, category)
                apps.add(appInfo)
            }

            return@withContext apps
        }

    fun getLastUpdateTime(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime

    /**
     * Detect app category based on package name.
     */
    fun detectCategory(packageName: String, appName: String, isSystemApp: Boolean): String {
        val pkg = packageName.lowercase()
        return when {
            // Social / messengers
            pkg.startsWith("org.telegram") -> AppInfo.CAT_SOCIAL
            pkg.startsWith("org.thunderdog.challegram") -> AppInfo.CAT_SOCIAL
            pkg == "com.whatsapp" -> AppInfo.CAT_SOCIAL
            pkg.startsWith("com.instagram") -> AppInfo.CAT_SOCIAL
            pkg == "com.discord" -> AppInfo.CAT_SOCIAL
            pkg == "com.facebook.orca" -> AppInfo.CAT_SOCIAL
            pkg == "com.snapchat.android" -> AppInfo.CAT_SOCIAL
            pkg.startsWith("com.twitter.android") -> AppInfo.CAT_SOCIAL
            pkg.startsWith("com.vk") || pkg.startsWith("com.odnoklassniki") -> AppInfo.CAT_SOCIAL
            pkg.contains("tiktok") -> AppInfo.CAT_SOCIAL
            pkg.startsWith("com.skype") -> AppInfo.CAT_SOCIAL
            pkg.startsWith("com.slack") -> AppInfo.CAT_SOCIAL
            // Media
            pkg.startsWith("com.google.android.youtube") -> AppInfo.CAT_MEDIA
            pkg == "com.spotify.music" -> AppInfo.CAT_MEDIA
            pkg.startsWith("com.netflix") -> AppInfo.CAT_MEDIA
            pkg.startsWith("com.zhiliaoapp.musically") -> AppInfo.CAT_MEDIA
            pkg.startsWith("com.google.android.apps.photos") -> AppInfo.CAT_MEDIA
            pkg.startsWith("com.google.android.apps.music") -> AppInfo.CAT_MEDIA
            pkg.contains("twitch") -> AppInfo.CAT_MEDIA
            // Browsers
            pkg == "com.android.chrome" -> AppInfo.CAT_BROWSER
            pkg == "org.mozilla.firefox" -> AppInfo.CAT_BROWSER
            pkg.startsWith("com.sec.android.app.sbrowser") -> AppInfo.CAT_BROWSER
            pkg.startsWith("com.opera") -> AppInfo.CAT_BROWSER
            pkg.startsWith("com.microsoft.emmx") -> AppInfo.CAT_BROWSER
            pkg.startsWith("org.mozilla.focus") -> AppInfo.CAT_BROWSER
            // Finance / banking
            pkg.contains("sberbank") || pkg.contains("tinkoff") || pkg.contains("alfabank") -> AppInfo.CAT_FINANCE
            pkg.contains("vtb") || pkg.contains("gazprombank") || pkg.contains("raiffeisen") -> AppInfo.CAT_FINANCE
            pkg.contains("bank") || pkg.contains("pay") -> AppInfo.CAT_FINANCE
            // Games
            pkg.contains("tencent") || pkg.contains("supercell") || pkg.contains("miHoYo") -> AppInfo.CAT_GAMES
            pkg.startsWith("com.epicgames") || pkg.startsWith("com.roblox") -> AppInfo.CAT_GAMES
            pkg.startsWith("com.king") || pkg.startsWith("com.ea") -> AppInfo.CAT_GAMES
            pkg.contains(".game.") || pkg.contains(".games.") -> AppInfo.CAT_GAMES
            // System apps
            isSystemApp -> AppInfo.CAT_SYSTEM
            // Everything else
            else -> AppInfo.CAT_OTHER
        }
    }
}
