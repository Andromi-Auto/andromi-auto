package com.andrerinas.headunitrevived.main.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.andrerinas.headunitrevived.R

// Sealed class to represent different types of items in the settings list
sealed class SettingItem {
    abstract val stableId: String // Unique ID for DiffUtil

    data class SettingEntry(
        override val stableId: String, // Unique ID for the setting (e.g., "gpsNavigation")
        @StringRes val nameResId: Int,
        var value: String, // Current display value of the setting
        val onClick: (settingId: String) -> Unit // Callback when the setting is clicked
    ) : SettingItem()

    data class ToggleSettingEntry(
        override val stableId: String,
        @StringRes val nameResId: Int,
        @StringRes val descriptionResId: Int,
        var isChecked: Boolean,
        val onCheckedChanged: (Boolean) -> Unit
    ) : SettingItem()

    data class CategoryHeader(override val stableId: String, @StringRes val titleResId: Int) : SettingItem()

    object Divider : SettingItem() {
        override val stableId: String = "Divider" // Static ID for the divider
    }
}

class SettingsAdapter : ListAdapter<SettingItem, RecyclerView.ViewHolder>(SettingsDiffCallback()) { // Inherit from ListAdapter

    // Define View Types
    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_SETTING = 1
        private const val VIEW_TYPE_DIVIDER = 2
        private const val VIEW_TYPE_TOGGLE = 3
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) { // Use getItem() from ListAdapter
            is SettingItem.CategoryHeader -> VIEW_TYPE_HEADER
            is SettingItem.SettingEntry -> VIEW_TYPE_SETTING
            is SettingItem.Divider -> VIEW_TYPE_DIVIDER
            is SettingItem.ToggleSettingEntry -> VIEW_TYPE_TOGGLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.layout_category_header, parent, false))
            VIEW_TYPE_SETTING -> SettingViewHolder(inflater.inflate(R.layout.layout_setting_item, parent, false))
            VIEW_TYPE_DIVIDER -> DividerViewHolder(inflater.inflate(R.layout.layout_divider, parent, false))
            VIEW_TYPE_TOGGLE -> ToggleSettingViewHolder(inflater.inflate(R.layout.layout_setting_item_toggle, parent, false))
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) { // Use getItem() from ListAdapter
            is SettingItem.CategoryHeader -> (holder as HeaderViewHolder).bind(item)
            is SettingItem.SettingEntry -> (holder as SettingViewHolder).bind(item)
            is SettingItem.Divider -> { /* Nothing to bind for a simple divider */ }
            is SettingItem.ToggleSettingEntry -> (holder as ToggleSettingViewHolder).bind(item)
        }
    }

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
            itemView.setOnClickListener { setting.onClick(setting.stableId) }
        }
    }

    class ToggleSettingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val settingName: TextView = itemView.findViewById(R.id.settingName)
        private val settingDescription: TextView = itemView.findViewById(R.id.settingDescription)
        private val settingSwitch: Switch = itemView.findViewById(R.id.settingSwitch)

        fun bind(setting: SettingItem.ToggleSettingEntry) {
            settingName.setText(setting.nameResId)
            settingDescription.setText(setting.descriptionResId)
            // Ensure listener is set BEFORE setting checked state to avoid unwanted triggers
            settingSwitch.setOnCheckedChangeListener(null) 
            settingSwitch.isChecked = setting.isChecked
            settingSwitch.setOnCheckedChangeListener { _, isChecked ->
                setting.onCheckedChanged(isChecked)
            }
            // Allow clicking on the whole item to toggle the switch
            itemView.setOnClickListener {
                settingSwitch.toggle()
            }
        }
    }

    class DividerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // No specific binding needed for a simple divider
    }

    // DiffUtil.ItemCallback implementation
    private class SettingsDiffCallback : DiffUtil.ItemCallback<SettingItem>() {
        override fun areItemsTheSame(oldItem: SettingItem, newItem: SettingItem): Boolean {
            return oldItem.stableId == newItem.stableId
        }

        override fun areContentsTheSame(oldItem: SettingItem, newItem: SettingItem): Boolean {
            return when {
                oldItem is SettingItem.SettingEntry && newItem is SettingItem.SettingEntry ->
                    oldItem.nameResId == newItem.nameResId && oldItem.value == newItem.value
                oldItem is SettingItem.ToggleSettingEntry && newItem is SettingItem.ToggleSettingEntry ->
                    oldItem.nameResId == newItem.nameResId && oldItem.descriptionResId == newItem.descriptionResId && oldItem.isChecked == newItem.isChecked
                oldItem is SettingItem.CategoryHeader && newItem is SettingItem.CategoryHeader ->
                    oldItem.titleResId == newItem.titleResId
                oldItem is SettingItem.Divider && newItem is SettingItem.Divider -> true
                else -> false
            }
        }
    }
}