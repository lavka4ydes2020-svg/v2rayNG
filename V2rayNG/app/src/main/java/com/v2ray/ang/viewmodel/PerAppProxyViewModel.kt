package com.v2ray.ang.viewmodel

import androidx.lifecycle.ViewModel
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager

class PerAppProxyViewModel : ViewModel() {
    private val selectedSet: MutableSet<String> = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)?.let {
        HashSet(it)
    } ?: HashSet()

    fun contains(packageName: String): Boolean = selectedSet.contains(packageName)

    fun getAll(): Set<String> = selectedSet.toSet()

    fun add(packageName: String): Boolean {
        val changed = selectedSet.add(packageName)
        if (changed) save()
        return changed
    }

    fun remove(packageName: String): Boolean {
        val changed = selectedSet.remove(packageName)
        if (changed) save()
        return changed
    }

    fun toggle(packageName: String) {
        if (selectedSet.contains(packageName)) {
            remove(packageName)
        } else {
            add(packageName)
        }
    }

    fun addAll(packages: Collection<String>) {
        if (selectedSet.addAll(packages)) save()
    }

    fun removeAll(packages: Collection<String>) {
        if (selectedSet.removeAll(packages.toSet())) save()
    }

    fun clear() {
        if (selectedSet.isNotEmpty()) {
            selectedSet.clear()
            save()
        }
    }

    /** Number of currently selected apps */
    val selectedCount: Int
        get() = selectedSet.size

    /** Apply a preset: keep only these packages selected, clear the rest */
    fun applyPreset(packageNames: Set<String>) {
        selectedSet.clear()
        selectedSet.addAll(packageNames)
        save()
    }

    /** Quick presets */
    companion object {
        /** Telegram: prefix match for all variants (messenger, web, plus, etc.) */
        val TELEGRAM_PREFIXES = listOf("org.telegram.messenger", "org.telegram.plus", "org.thunderdog.challegram")

        /** Messengers preset */
        val PRESET_MESSENGERS = setOf(
            "com.whatsapp",
            "com.discord",
        )

        /** Banking preset */
        val PRESET_BANKING = setOf(
            "ru.sberbankmobile",
            "com.idamob.tinkoff.android",
        )

        fun matchesTelegram(pkg: String): Boolean =
            TELEGRAM_PREFIXES.any { pkg.startsWith(it) }
    }

    private fun save() {
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY_SET, selectedSet)
        SettingsChangeManager.makeRestartService()
    }
}
