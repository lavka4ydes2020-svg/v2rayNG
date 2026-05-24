package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ItemRecyclerBypassListBinding
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.viewmodel.PerAppProxyViewModel

class PerAppProxyAdapter(
    var displayList: List<Any>,
    val viewModel: PerAppProxyViewModel
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_SECTION = 0
        const val TYPE_ITEM = 1

        /**
         * Build a grouped list from apps for display.
         * Returns list of mixed items: String (category) or AppInfo.
         */
        fun buildDisplayList(
            apps: List<AppInfo>,
            filterCategory: String?,
            searchQuery: String,
            viewModel: PerAppProxyViewModel
        ): List<Any> {
            var filtered = apps

            if (!filterCategory.isNullOrEmpty() && filterCategory != "all") {
                if (filterCategory == "vpn") {
                    filtered = filtered.filter { viewModel.contains(it.packageName) }
                } else {
                    filtered = filtered.filter { it.category == filterCategory }
                }
            }

            if (searchQuery.isNotEmpty()) {
                val q = searchQuery.uppercase()
                filtered = filtered.filter {
                    it.appName.uppercase().contains(q) || it.packageName.uppercase().contains(q)
                }
            }

            val grouped = linkedMapOf<String, MutableList<AppInfo>>()
            AppInfo.CATEGORY_ORDER.forEach { cat -> grouped[cat] = mutableListOf() }

            filtered.forEach { app ->
                val cat = if (app.category in grouped) app.category else AppInfo.CAT_OTHER
                grouped[cat]!!.add(app)
            }

            val result = mutableListOf<Any>()
            grouped.forEach { (cat, appsInCat) ->
                if (appsInCat.isNotEmpty()) {
                    result.add(cat)
                    result.addAll(appsInCat)
                }
            }

            return result
        }
    }

    fun updateList(newList: List<Any>) {
        displayList = newList
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (displayList[position] is String) TYPE_SECTION else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SECTION -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(android.R.layout.simple_list_item_1, parent, false)
                val padding16 = parent.context.resources.getDimensionPixelSize(R.dimen.padding_spacing_dp16)
                val padding8 = parent.context.resources.getDimensionPixelSize(R.dimen.padding_spacing_dp8)
                view.setPadding(padding16, padding8, padding16, 2)
                SectionViewHolder(view)
            }
            else -> AppViewHolder(
                ItemRecyclerBypassListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SectionViewHolder -> holder.bind(displayList[position] as String)
            is AppViewHolder -> holder.bind(displayList[position] as AppInfo)
        }
    }

    override fun getItemCount(): Int = displayList.size

    inner class SectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(android.R.id.text1)

        fun bind(category: String) {
            textView.text = AppInfo.CATEGORY_LABELS[category] ?: category
            textView.setTextAppearance(itemView.context, android.R.style.TextAppearance_Small)
            textView.setTextColor(0x99FFFFFF.toInt())
            textView.setAllCaps(true)
            textView.textSize = 11f
        }
    }

    inner class AppViewHolder(private val itemBypassBinding: ItemRecyclerBypassListBinding) :
        RecyclerView.ViewHolder(itemBypassBinding.root), View.OnClickListener {

        private lateinit var appInfo: AppInfo

        fun bind(appInfo: AppInfo) {
            this.appInfo = appInfo

            itemBypassBinding.icon.setImageDrawable(appInfo.appIcon)
            itemBypassBinding.name.text = appInfo.appName
            itemBypassBinding.packageName.text = appInfo.packageName

            val isSelected = viewModel.contains(appInfo.packageName)
            itemBypassBinding.switchToggle.isChecked = isSelected
            itemBypassBinding.badgeVpn.visibility = if (isSelected) View.VISIBLE else View.GONE
            itemBypassBinding.root.setBackgroundColor(
                if (isSelected) 0x0DCE93D8.toInt() else 0x00000000.toInt()
            )

            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            val packageName = appInfo.packageName
            viewModel.toggle(packageName)
            val isNowSelected = viewModel.contains(packageName)
            itemBypassBinding.switchToggle.isChecked = isNowSelected
            itemBypassBinding.badgeVpn.visibility = if (isNowSelected) View.VISIBLE else View.GONE
            itemBypassBinding.root.setBackgroundColor(
                if (isNowSelected) 0x0DCE93D8.toInt() else 0x00000000.toInt()
            )
        }
    }
}
