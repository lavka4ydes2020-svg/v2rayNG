package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityBypassListBinding
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.util.AppManagerUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.viewmodel.PerAppProxyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

class PerAppProxyActivity : BaseActivity() {
    private val binding by lazy { ActivityBypassListBinding.inflate(layoutInflater) }

    private var adapter: PerAppProxyAdapter? = null
    private var appsAll: List<AppInfo>? = null
    private val viewModel: PerAppProxyViewModel by viewModels()

    // Filter state
    private var currentFilter = "all"
    private var searchQuery = ""

    companion object {
        private val TELEGRAM_PREFIXES = listOf("org.telegram.messenger", "org.telegram.plus", "org.thunderdog.challegram")
        val RECOMMENDED_PACKAGES = setOf(
            "com.whatsapp",
            "com.instagram.android",
            "com.google.android.youtube",
            "com.discord",
            "com.google.android.apps.meetings",
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = getString(R.string.per_app_proxy_settings))

        // Add custom divider to RecyclerView
        addCustomDividerToRecyclerView(binding.recyclerView, this, R.drawable.custom_divider)

        // Update mode badge — always Proxy mode
        updateModeBadge()

        // Build filter chips
        buildFilterChips()

        // Wire search
        binding.searchView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString() ?: ""
                refreshList()
            }
        })

        // Apply button
        binding.btnApply.setOnClickListener {
            viewModel.run {
                // Save is already automatic on each toggle, but trigger restart
                SettingsChangeManager.makeRestartService()
            }
            toastSuccess(R.string.toast_success)
        }

        // Load apps
        initList()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_bypass_list, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.select_all -> {
            selectAllApp()
            true
        }
        R.id.invert_selection -> {
            invertSelection()
            true
        }
        R.id.select_recommended -> {
            selectRecommendedApps()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // ─── Mode badge ────────────────────────────────────────

    /**
     * Update the mode badge text based on the current bypass/proxy mode.
     */
    private fun updateModeBadge() {
        val count = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)?.size ?: 0
        binding.modeBadge.text = "🔒 VPN включён для $count приложений"
    }

    // ─── Filter chips ────────────────────────────────────────

    /**
     * Build filter chips (TextViews with background) and preset chips dynamically.
     */
    private fun buildFilterChips() {
        val container = binding.chipContainer
        container.removeAllViews()

        // Filter definitions: key -> display label
        val filters = linkedMapOf(
            "all" to getString(R.string.chip_all),
            "vpn" to getString(R.string.chip_vpn),
            AppInfo.CAT_SOCIAL to getString(R.string.chip_social),
            AppInfo.CAT_SYSTEM to getString(R.string.chip_system),
            AppInfo.CAT_BROWSER to getString(R.string.chip_browser),
            AppInfo.CAT_MEDIA to getString(R.string.chip_media),
            AppInfo.CAT_GAMES to getString(R.string.chip_games),
        )

        filters.forEach { (key, label) ->
            val chip = createChipView(label, key == "all")
            chip.setOnClickListener {
                currentFilter = key
                refreshChipSelection(container, key)
                refreshList()
            }
            container.addView(chip)
        }

        // Separator line
        val sep = View(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                1.dpToPx(),
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0x20FFFFFF.toInt())
            setPadding(0, 0, 0, 0)
        }
        // Actually use margin instead
        val sepSpacer = View(this).apply {
            layoutParams = ViewGroup.LayoutParams(12.dpToPx(), 1)
        }
        container.addView(sepSpacer)

        // Preset chips
        addPresetChip(container, "\uD83D\uDCDF ${getString(R.string.chip_preset_messengers)}") {
            applyPresetMessengers()
        }
        addPresetChip(container, "\uD83C\uDFE6 ${getString(R.string.chip_preset_banking)}") {
            applyPresetBanking()
        }
        addPresetChip(container, "\uD83C\uDFB5 ${getString(R.string.chip_preset_media)}") {
            applyPresetMedia()
        }
    }

    private fun createChipView(label: String, selected: Boolean): TextView {
        val dp8 = 8.dpToPx()
        val dp12 = 12.dpToPx()
        return TextView(this).apply {
            text = label
            textSize = 12f
            setPadding(dp12, 6.dpToPx(), dp12, 6.dpToPx())
            val marginEnd = if (selected) dp8 else dp8
            val lp = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = marginEnd
            layoutParams = lp
            setBackgroundResource(if (selected) R.drawable.badge_pill else R.drawable.search_background)
            setTextColor(
                if (selected) ContextCompat.getColor(this@PerAppProxyActivity, android.R.color.white)
                else 0x99FFFFFF.toInt()
            )
            isAllCaps = false
        }
    }

    private fun refreshChipSelection(container: ViewGroup, selectedKey: String) {
        val filters = linkedMapOf(
            "all" to null,
            "vpn" to null,
            AppInfo.CAT_SOCIAL to null,
            AppInfo.CAT_SYSTEM to null,
            AppInfo.CAT_BROWSER to null,
            AppInfo.CAT_MEDIA to null,
            AppInfo.CAT_GAMES to null,
        )
        var idx = 0
        filters.keys.forEach { key ->
            if (idx < container.childCount) {
                val child = container.getChildAt(idx)
                if (child is TextView && !child.text.contains("\uD83D\uDCDF") && !child.text.contains("\uD83C\uDFE6") && !child.text.contains("\uD83C\uDFB5")) {
                    child.setBackgroundResource(
                        if (key == selectedKey) R.drawable.badge_pill
                        else R.drawable.search_background
                    )
                    child.setTextColor(
                        if (key == selectedKey) ContextCompat.getColor(this, android.R.color.white)
                        else 0x99FFFFFF.toInt()
                    )
                }
                idx++
            }
        }
    }

    private fun addPresetChip(container: ViewGroup, label: String, onClick: () -> Unit) {
        val chip = TextView(this).apply {
            text = label
            textSize = 11f
            setPadding(10.dpToPx(), 5.dpToPx(), 10.dpToPx(), 5.dpToPx())
            val lp = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = 6.dpToPx()
            layoutParams = lp
            setBackgroundColor(0x15CE93D8.toInt())
            setTextColor(0xCCCE93D8.toInt())
            setOnClickListener { onClick() }
            isAllCaps = false
        }
        container.addView(chip)
    }

    // ─── Presets ─────────────────────────────────────────────

    private fun applyPresetMessengers() {
        viewModel.clear()
        appsAll?.forEach { app ->
            val pkg = app.packageName
            if (TELEGRAM_PREFIXES.any { pkg.startsWith(it) } || pkg in PerAppProxyViewModel.PRESET_MESSENGERS) {
                viewModel.add(pkg)
            }
        }
        toast(getString(R.string.recommended_apps_selected, viewModel.selectedCount, 0))
        refreshList()
    }

    private fun applyPresetBanking() {
        viewModel.clear()
        appsAll?.forEach { app ->
            if (app.category == AppInfo.CAT_FINANCE) {
                viewModel.add(app.packageName)
            }
        }
        toast("Банки выбраны: ${viewModel.selectedCount}")
        refreshList()
    }

    private fun applyPresetMedia() {
        viewModel.clear()
        appsAll?.forEach { app ->
            val pkg = app.packageName
            if (pkg.startsWith("com.google.android.youtube") || pkg == "com.spotify.music") {
                viewModel.add(pkg)
            }
        }
        toast("Медиа выбраны: ${viewModel.selectedCount}")
        refreshList()
    }

    private fun selectRecommendedApps() {
        viewModel.clear()
        adapter?.let { adapter ->
            var selectedCount = 0
            adapter.displayList.filterIsInstance<AppInfo>().forEach { app ->
                val pkg = app.packageName
                when {
                    TELEGRAM_PREFIXES.any { pkg.startsWith(it) } -> {
                        viewModel.add(pkg); selectedCount++
                    }
                    pkg in RECOMMENDED_PACKAGES -> {
                        viewModel.add(pkg); selectedCount++
                    }
                }
            }
            toast(getString(R.string.recommended_apps_selected, selectedCount, RECOMMENDED_PACKAGES.size + 1))
            refreshList()
        }
    }

    private fun selectAllApp() {
        adapter?.let { adapter ->
            val appItems = adapter.displayList.filterIsInstance<AppInfo>()
            val pkgs = appItems.map { it.packageName }
            val allSelected = pkgs.all { viewModel.contains(it) }
            if (allSelected) {
                viewModel.removeAll(pkgs)
            } else {
                viewModel.addAll(pkgs)
            }
            refreshList()
        }
    }

    private fun invertSelection() {
        adapter?.let { adapter ->
            adapter.displayList.filterIsInstance<AppInfo>().forEach { app ->
                viewModel.toggle(app.packageName)
            }
            refreshList()
        }
    }

    // ─── List loading & refresh ──────────────────────────────

    private fun initList() {
        binding.recyclerView.isNestedScrollingEnabled = false
        showLoading()

        lifecycleScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) {
                    val appsList = AppManagerUtil.loadNetworkAppList(this@PerAppProxyActivity)
                    val collator = Collator.getInstance()
                    appsList.sortedWith(compareBy(collator) { it.appName })
                }
                appsAll = apps
                refreshList()
            } catch (e: Exception) {
                LogUtil.e(ANG_PACKAGE, "Error loading apps", e)
            } finally {
                hideLoading()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun refreshList() {
        val apps = appsAll ?: return

        val displayList = PerAppProxyAdapter.buildDisplayList(
            apps = apps,
            filterCategory = currentFilter,
            searchQuery = searchQuery,
            viewModel = viewModel
        )

        if (adapter == null) {
            adapter = PerAppProxyAdapter(displayList, viewModel)
            binding.recyclerView.adapter = adapter
        } else {
            adapter!!.updateList(displayList)
        }

        updateCount()
    }

    private fun updateCount() {
        val total = appsAll?.size ?: 0
        val selected = viewModel.selectedCount
        binding.selectedCount.text = getString(R.string.per_app_proxy_count, selected, total)
    }

    // ─── Helpers ─────────────────────────────────────────────

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
