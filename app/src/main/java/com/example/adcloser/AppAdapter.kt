package com.example.adcloser

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.adcloser.databinding.ItemAppBinding

class AppAdapter(
    private val apps: List<AppEntry>,
    private val selected: MutableSet<String>,
    private val onToggle: (String, Boolean) -> Unit
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = apps.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.binding.tvAppName.text = "${app.label} (${app.packageName})"
        holder.binding.ivAppIcon.setImageDrawable(app.icon)
        holder.binding.checkbox.setOnCheckedChangeListener(null)
        holder.binding.checkbox.isChecked = selected.contains(app.packageName)

        holder.binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selected.add(app.packageName) else selected.remove(app.packageName)
            onToggle(app.packageName, isChecked)
        }
        holder.binding.root.setOnClickListener {
            holder.binding.checkbox.isChecked = !holder.binding.checkbox.isChecked
        }
    }
}
