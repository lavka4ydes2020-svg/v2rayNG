     1|package com.v2ray.ang
     2|
     3|import android.content.Context
     4|import androidx.multidex.MultiDexApplication
     5|import androidx.work.Configuration
     6|import androidx.work.WorkManager
     7|import com.tencent.mmkv.MMKV
     8|import com.v2ray.ang.AppConfig.ANG_PACKAGE
     9|import com.v2ray.ang.fmt.VlessFmt
    10|import com.v2ray.ang.handler.EncryptedPrefsManager
    11|import com.v2ray.ang.handler.MmkvManager
    12|import com.v2ray.ang.handler.SettingsManager
    13|
    14|class AngApplication : MultiDexApplication() {
    15|    companion object {
    16|        lateinit var application: AngApplication
    17|        private const val PREF_PREINSTALLED_SERVER_SE = "pref_preinstalled_server_se"

    private const val PREF_PREINSTALLED_SERVER = "pref_preinstalled_server"
    18|    }
    19|
    20|    /**
    21|     * Attaches the base context to the application.
    22|     * @param base The base context.
    23|     */
    24|    override fun attachBaseContext(base: Context?) {
    25|        super.attachBaseContext(base)
    26|        application = this
    27|    }
    28|
    29|    private val workManagerConfiguration: Configuration = Configuration.Builder()
    30|        .setDefaultProcessName("${ANG_PACKAGE}:bg")
    31|        .build()
    32|
    33|    /**
    34|     * Initializes the application.
    35|     */
    36|    override fun onCreate() {
    37|        super.onCreate()
    38|
    39|        MMKV.initialize(this)
    40|
    41|        // Initialize encrypted storage for sensitive server credentials
    42|        EncryptedPrefsManager.init(this)
    43|
    44|        // Initialize WorkManager with the custom configuration
    45|        WorkManager.initialize(this, workManagerConfiguration)
    46|
    47|        // Ensure critical preference defaults are present in MMKV early
    48|        SettingsManager.initApp(this)
    49|        SettingsManager.setNightMode()
    50|
    51|        // Install pre-configured server profile on first launch
    52|        installDefaultServers()
    53|
    54|        es.dmoral.toasty.Toasty.Config.getInstance()
    55|            .setGravity(android.view.Gravity.BOTTOM, 0, 300)
    56|            .apply()
    57|    }
    58|
    59|    /**
    60|     * Installs the default VLESS+Reality server profile on first app launch.
    61|     * Parses the VLESS link and saves it as a pre-installed server.
    62|     */
    63|    private fun installDefaultServers()  {
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
            val guid = MmkvManager.encodeServerConfig(""", profile)

            if (select) {
                MmkvManager.setSelectServer(guid)
            }

            MmkvManager.encodeSettings(flagKey, true)
            android.util.Log.i(AppConfig.TAG, "Pre-installed profile: " + profile.remarks)
        } catch (e: Exception) {
            android.util.Log.e(AppConfig.TAG, "Failed to install profile", e)
        }
    }
    88|}
    89|