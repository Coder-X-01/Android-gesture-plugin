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

import android.graphics.drawable.Drawable
import android.content.pm.PackageManager

class MainActivity : AppCompatActivity() {
    private val gestureButtons = mutableMapOf<GestureType, Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val adapter = ArrayAdapter.createFromResource(this, R.array.function_options, android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        bind(GestureType.LEFT_UP, findViewById(R.id.sp_left_up), findViewById(R.id.btn_left_up_app), adapter)
        bind(GestureType.LEFT_HORIZONTAL, findViewById(R.id.sp_left_h), findViewById(R.id.btn_left_h_app), adapter)
        bind(GestureType.LEFT_DOWN, findViewById(R.id.sp_left_down), findViewById(R.id.btn_left_down_app), adapter)
        bind(GestureType.RIGHT_UP, findViewById(R.id.sp_right_up), findViewById(R.id.btn_right_up_app), adapter)
        bind(GestureType.RIGHT_HORIZONTAL, findViewById(R.id.sp_right_h), findViewById(R.id.btn_right_h_app), adapter)
        bind(GestureType.RIGHT_DOWN, findViewById(R.id.sp_right_down), findViewById(R.id.btn_right_down_app), adapter)

        findViewById<Button>(R.id.btn_overlay_perm).setOnClickListener {
            FunctionExecutor.ensureOverlayPermission(this)
        }
        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
            val intent = Intent(Intent.ACTION_PICK_ACTIVITY)
            val main = Intent(Intent.ACTION_MAIN, null)
            main.addCategory(Intent.CATEGORY_LAUNCHER)
            intent.putExtra(Intent.EXTRA_INTENT, main)
            startActivityForResult(intent, gesture.ordinal)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            val component = data.component
            if (component != null) {
                val gesture = GestureType.values()[requestCode]
                Prefs.setAppPackage(this, gesture, component.packageName)
                val btn = gestureButtons[gesture]
                if (btn != null) {
                    updateAppInfo(btn, component.packageName)
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
