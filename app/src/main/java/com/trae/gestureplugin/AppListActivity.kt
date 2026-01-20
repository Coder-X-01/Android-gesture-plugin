package com.trae.gestureplugin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trae.gestureplugin.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var isMultiSelect = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_list)
        
        isMultiSelect = intent.getBooleanExtra("multi_select", false)
        title = if (isMultiSelect) "Select Apps to Shield" else "Select App"

        recyclerView = findViewById(R.id.recycler_view)
        progressBar = findViewById(R.id.progress_bar)

        recyclerView.layoutManager = LinearLayoutManager(this)

        loadApps()
    }

    private fun loadApps() {
        scope.launch {
            val blockedApps = if (isMultiSelect) Prefs.getBlockedApps(this@AppListActivity) else emptySet()
            
            val apps = withContext(Dispatchers.IO) {
                val pm = packageManager
                val packages = pm.getInstalledPackages(0)
                packages.mapNotNull { info ->
                    val pkgName = info.packageName
                    if (pm.getLaunchIntentForPackage(pkgName) != null) {
                        val appName = info.applicationInfo.loadLabel(pm).toString()
                        val icon = info.applicationInfo.loadIcon(pm)
                        AppItem(appName, pkgName, icon, blockedApps.contains(pkgName))
                    } else {
                        null
                    }
                }.sortedBy { it.name.lowercase() }
            }

            progressBar.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = AppAdapter(apps, isMultiSelect) { pkg, isChecked ->
                if (isMultiSelect) {
                    val current = Prefs.getBlockedApps(this@AppListActivity).toMutableSet()
                    if (isChecked) {
                        current.add(pkg)
                    } else {
                        current.remove(pkg)
                    }
                    Prefs.setBlockedApps(this@AppListActivity, current)
                } else {
                    val data = Intent()
                    data.putExtra("package_name", pkg)
                    setResult(RESULT_OK, data)
                    finish()
                }
            }
        }
    }

    data class AppItem(val name: String, val packageName: String, val icon: Drawable, var isSelected: Boolean)

    class AppAdapter(
        private val items: List<AppItem>, 
        private val isMultiSelect: Boolean,
        private val onAction: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.iv_icon)
            val name: TextView = view.findViewById(R.id.tv_name)
            val check: CheckBox = view.findViewById(R.id.cb_select)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_select, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.name.text = item.name
            holder.icon.setImageDrawable(item.icon)
            
            if (isMultiSelect) {
                holder.check.visibility = View.VISIBLE
                holder.check.setOnCheckedChangeListener(null)
                holder.check.isChecked = item.isSelected
                holder.check.setOnCheckedChangeListener { _, isChecked ->
                    item.isSelected = isChecked
                    onAction(item.packageName, isChecked)
                }
                holder.itemView.setOnClickListener {
                    holder.check.performClick()
                }
            } else {
                holder.check.visibility = View.GONE
                holder.itemView.setOnClickListener {
                    onAction(item.packageName, true)
                }
            }
        }

        override fun getItemCount() = items.size
    }
}
