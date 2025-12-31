package com.andrerinas.headunitrevived.main.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.andrerinas.headunitrevived.R

// Sealed class to represent different types of items in the settings list
sealed class SettingItem {
    data class SettingEntry(
        val id: String, // Unique ID for the setting (e.g., "gpsNavigation")
        @StringRes val nameResId: Int,
        var value: String, // Current display value of the setting
        val onClick: (settingId: String) -> Unit // Callback when the setting is clicked
    ) : SettingItem()

    data class CategoryHeader(@StringRes val titleResId: Int) : SettingItem()

    object Divider : SettingItem() // Simple object for a divider
}

class SettingsAdapter(
    private val items: List<SettingItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // Define View Types
    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_SETTING = 1
        private const val VIEW_TYPE_DIVIDER = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SettingItem.CategoryHeader -> VIEW_TYPE_HEADER
            is SettingItem.SettingEntry -> VIEW_TYPE_SETTING
            is SettingItem.Divider -> VIEW_TYPE_DIVIDER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.layout_category_header, parent, false))
            VIEW_TYPE_SETTING -> SettingViewHolder(inflater.inflate(R.layout.layout_setting_item, parent, false))
            VIEW_TYPE_DIVIDER -> DividerViewHolder(inflater.inflate(R.layout.layout_divider, parent, false))
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SettingItem.CategoryHeader -> (holder as HeaderViewHolder).bind(item)
            is SettingItem.SettingEntry -> (holder as SettingViewHolder).bind(item)
            is SettingItem.Divider -> (holder as DividerViewHolder) // Nothing to bind for a simple divider
        }
    }

    override fun getItemCount(): Int = items.size

    // --- ViewHolder implementations ---

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.categoryTitle)
        fun bind(header: SettingItem.CategoryHeader) {
            title.setText(header.titleResId)
        }
    }

    class SettingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val settingName: TextView = itemView.findViewById(R.id.settingName)
        private val settingValue: TextView = itemView.findViewById(R.id.settingValue)
        
        fun bind(setting: SettingItem.SettingEntry) {
            settingName.setText(setting.nameResId)
            settingValue.text = setting.value
            itemView.setOnClickListener { setting.onClick(setting.id) }
        }
    }

    class DividerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // No specific binding needed for a simple divider
    }
}
