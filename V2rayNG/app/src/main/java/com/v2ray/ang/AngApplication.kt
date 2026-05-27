package com.v2ray.ang

import android.content.Context
import androidx.multidex.MultiDexApplication
import androidx.work.Configuration
import androidx.work.WorkManager
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.fmt.VlessFmt
import com.v2ray.ang.handler.EncryptedPrefsManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager

class AngApplication : MultiDexApplication() {
    companion object {
        lateinit var application: AngApplication
        private const val PREF_PREINSTALLED_SERVER = "pref_preinstalled_server"
        private const val PREF_PREINSTALLED_SERVER_SE = "pref_preinstalled_server_se"
    }

    /**
     * Attaches the base context to the application.
     * @param base The base context.
     */
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    private val workManagerConfiguration: Configuration = Configuration.Builder()
        .setDefaultProcessName("${ANG_PACKAGE}:bg")
        .build()

    /**
     * Initializes the application.
     */
    override fun onCreate() {
        super.onCreate()

        MMKV.initialize(this)

        // Initialize encrypted storage for sensitive server credentials
        EncryptedPrefsManager.init(this)

        // Initialize WorkManager with the custom configuration
        WorkManager.initialize(this, workManagerConfiguration)

        // Ensure critical preference defaults are present in MMKV early
        SettingsManager.initApp(this)
        SettingsManager.setNightMode()

        // Install pre-configured server profile on first launch
        installDefaultServers()

        es.dmoral.toasty.Toasty.Config.getInstance()
            .setGravity(android.view.Gravity.BOTTOM, 0, 300)
            .apply()
    }

    /**
     * Installs the default VLESS+Reality server profile on first app launch.
     * Parses the VLESS link and saves it as a pre-installed server.
     */
    private fun installDefaultServers() {
        // Germany server (AEZA) — fallback
        if (!MmkvManager.decodeSettingsBool(PREF_PREINSTALLED_SERVER, false)) {
            installVlessProfile(
                "vless://6f168bb6-cbf3-4c9d-b60f-d6873a216e42@79.137.202.148:443?encryption=none&security=reality&sni=www.bing.com&fp=chrome&pbk=tBXxynar6xznGkpe8wPPYgF43hfg1k5wo7eUIXnDFA4&sid=912b3d132f4e9fbb&type=tcp&flow=xtls-rprx-vision#Alfredo-VPN-DE",
                PREF_PREINSTALLED_SERVER,
                select = false
            )
        }

        // Sweden server (Fornex) — primary
        if (!MmkvManager.decodeSettingsBool(PREF_PREINSTALLED_SERVER_SE, false)) {
            installVlessProfile(
                "vless://a1161024-5858-4301-812d-afe497d880db@89.127.222.101:443?encryption=none&security=reality&sni=www.microsoft.com&fp=chrome&pbk=5gTR708j2tM8N258ibiQPesNKSBosS6-dE1ew467_Ao&sid=a5a77ccb14e3dc25&type=tcp&flow=xtls-rprx-vision#Alfredo-VPN-SE",
                PREF_PREINSTALLED_SERVER_SE,
                select = true
            )
        }
    }

    private fun installVlessProfile(vlessLink: String, flagKey: String, select: Boolean) {
        try {
            val profile = VlessFmt.parse(vlessLink) ?: return
            profile.subscriptionId = AppConfig.DEFAULT_SUBSCRIPTION_ID
            val guid = MmkvManager.encodeServerConfig("", profile)

            if (select) {
                MmkvManager.setSelectServer(guid)
            }

            MmkvManager.encodeSettings(flagKey, true)
            android.util.Log.i(AppConfig.TAG, "Pre-installed profile: " + profile.remarks)
        } catch (e: Exception) {
            android.util.Log.e(AppConfig.TAG, "Failed to install profile", e)
        }
    }
}
