package com.trae.gestureplugin

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import android.util.Log

import android.graphics.drawable.Drawable
import android.content.pm.PackageManager

class MainActivity : AppCompatActivity() {
    private val gestureButtons = mutableMapOf<GestureType, Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val adapter = ArrayAdapter.createFromResource(this, R.array.function_options, android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        // Bind gestures
        bind(GestureType.LEFT_UP, findViewById(R.id.sp_left_up), findViewById(R.id.btn_left_up_app), adapter)
        bind(GestureType.LEFT_HORIZONTAL, findViewById(R.id.sp_left_h), findViewById(R.id.btn_left_h_app), adapter)
        bind(GestureType.LEFT_DOWN, findViewById(R.id.sp_left_down), findViewById(R.id.btn_left_down_app), adapter)
        bind(GestureType.RIGHT_UP, findViewById(R.id.sp_right_up), findViewById(R.id.btn_right_up_app), adapter)
        bind(GestureType.RIGHT_HORIZONTAL, findViewById(R.id.sp_right_h), findViewById(R.id.btn_right_h_app), adapter)
        bind(GestureType.RIGHT_DOWN, findViewById(R.id.sp_right_down), findViewById(R.id.btn_right_down_app), adapter)

        // Bind screenshot setting
        val switchScreenshot = findViewById<android.widget.Switch>(R.id.switch_screenshot_confirm)
        switchScreenshot.isChecked = Prefs.getUseMediaProjection(this)
        switchScreenshot.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setUseMediaProjection(this, isChecked)
        }

        // Bind animation setting
        val switchAnimation = findViewById<android.widget.Switch>(R.id.switch_animation)
        switchAnimation.isChecked = Prefs.getShowAnimation(this)
        switchAnimation.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setShowAnimation(this, isChecked)
        }

        // Permissions and Service Control
        findViewById<Button>(R.id.btn_overlay_perm).setOnClickListener {
            FunctionExecutor.ensureOverlayPermission(this)
        }
        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            showRestrictedSettingsGuide()
        }
        findViewById<Button>(R.id.btn_start_service).setOnClickListener {
            if (FunctionExecutor.ensureOverlayPermission(this)) {
                val intent = Intent(this, OverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            }
        }
        findViewById<Button>(R.id.btn_stop_service).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
        }
    }

    private fun showRestrictedSettingsGuide() {
        android.app.AlertDialog.Builder(this)
            .setTitle("无法开启辅助功能？")
            .setMessage("如果在辅助功能列表中显示“受限设置”或无法勾选：\n\n1. 打开手机系统的【设置】->【应用】->【Gesture Plugin】\n2. 点击右上角三个点图标\n3. 选择【允许受限设置】\n4. 返回此处重新开启辅助功能")
            .setPositiveButton("知道了", null)
            .setNeutralButton("去应用详情页") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .show()
    }

    private fun bind(gesture: GestureType, spinner: Spinner, appBtn: Button, adapter: ArrayAdapter<CharSequence>) {
        gestureButtons[gesture] = appBtn
        spinner.adapter = adapter
        val stored = Prefs.getFunction(this, gesture)
        spinner.setSelection(stored.ordinal)
        
        updateAppInfo(appBtn, Prefs.getAppPackage(this, gesture))
        appBtn.visibility = if (stored == FunctionType.LAUNCH_APP) android.view.View.VISIBLE else android.view.View.GONE
        
        spinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val func = FunctionType.values()[position]
                Prefs.setFunction(this@MainActivity, gesture, func)
                appBtn.visibility = if (func == FunctionType.LAUNCH_APP) android.view.View.VISIBLE else android.view.View.GONE
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
        appBtn.setOnClickListener {
            val intent = Intent(this, AppListActivity::class.java)
            startActivityForResult(intent, gesture.ordinal)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("MainActivity", "onActivityResult: req=$requestCode, res=$resultCode, data=$data")
        if (resultCode == RESULT_OK && data != null) {
            val packageName = data.getStringExtra("package_name")
            Log.d("MainActivity", "Selected package: $packageName")
            if (packageName != null) {
                val gesture = GestureType.values()[requestCode]
                Prefs.setAppPackage(this, gesture, packageName)
                val btn = gestureButtons[gesture]
                if (btn != null) {
                    updateAppInfo(btn, packageName)
                }
            }
        }
    }

    private fun updateAppInfo(button: Button, packageName: String?) {
        if (packageName == null) {
            button.text = "选择应用"
            button.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
            return
        }
        try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(info)
            val icon = pm.getApplicationIcon(info)
            
            // 缩放图标到 48x48 dp (大约 96px for xhdpi)
            val density = resources.displayMetrics.density
            val size = (32 * density).toInt()
            icon.setBounds(0, 0, size, size)
            
            button.text = label
            button.setCompoundDrawables(icon, null, null, null)
            button.compoundDrawablePadding = (8 * density).toInt()
        } catch (e: Exception) {
            button.text = "未知应用"
            button.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
        }
    }
}
