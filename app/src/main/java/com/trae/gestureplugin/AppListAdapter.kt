package com.trae.gestureplugin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppListAdapter(
    private val appList: List<AppInfo>,
    private val onItemClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {
    
    private val scope = MainScope()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_icon)
        val name: TextView = view.findViewById(R.id.tv_name)
        var currentPackageName: String? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = appList[position]
        holder.name.text = app.name
        holder.icon.setImageDrawable(null)
        holder.currentPackageName = app.packageName

        // Async load icon
        scope.launch(Dispatchers.Main) {
            val icon = withContext(Dispatchers.IO) {
                try {
                    holder.itemView.context.packageManager.getApplicationIcon(app.packageName)
                } catch (e: Exception) {
                    null
                }
            }
            if (holder.currentPackageName == app.packageName) {
                holder.icon.setImageDrawable(icon ?: holder.itemView.context.getDrawable(android.R.drawable.sym_def_app_icon))
            }
        }

        holder.itemView.setOnClickListener {
            onItemClick(app)
        }
    }

    override fun getItemCount() = appList.size
}