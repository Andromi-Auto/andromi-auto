package com.andrerinas.openheadunit.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.utils.VideoStreamInfo

/**
 * Andromi Auto: the cards at the top of Settings. The first says which picture size Android Auto
 * really sent last time (so a blurry picture can be explained), the second opens Automatic start.
 */
class SettingsStatusAdapter(private val onAutoStart: () -> Unit) :
    RecyclerView.Adapter<SettingsStatusAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_settings_status, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val v = holder.itemView
        val ctx = v.context
        val last = VideoStreamInfo.last(ctx)
        v.findViewById<TextView>(R.id.status_video).text =
            last?.first ?: ctx.getString(R.string.andromi_status_video_none)
        v.findViewById<TextView>(R.id.status_video_note).text = when {
            last == null -> ctx.getString(R.string.andromi_status_video_none_note)
            last.second != null -> ctx.getString(R.string.andromi_status_video_fallback, last.second)
            else -> ctx.getString(R.string.andromi_status_video_note)
        }
        val on = App.provide(ctx).settings.autoStartSelfMode
        v.findViewById<TextView>(R.id.status_auto).setText(
            if (on) R.string.andromi_auto_start_home_summary_on else R.string.andromi_auto_start_home_summary_off)
        v.findViewById<View>(R.id.status_auto_card).setOnClickListener { onAutoStart() }
    }
}
