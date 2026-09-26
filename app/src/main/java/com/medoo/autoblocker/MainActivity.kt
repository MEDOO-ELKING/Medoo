package com.medoo.autoblocker

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("medoo", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 32)
        }

        val title = TextView(this).apply {
            text = "M̱͗e̲͎͋d̷͈̈o̠ͯ̓ọ̢̲ͣ"
            textSize = 30f
            setPadding(0, 0, 0, 20)
        }

        val info = TextView(this).apply {
            text = """
                حظر أرقام واتساب غير المحفوظة تلقائيًا.

                يعمل التطبيق محليًا على الهاتف. يحتاج إلى:
                1) قراءة جهات الاتصال لمعرفة هل الرقم محفوظ.
                2) Notification Access لاكتشاف إشعار واتساب.
                3) Accessibility للتنقل داخل واتساب وتنفيذ الحظر.

                ملاحظة: واجهة واتساب قد تتغير، لذلك قد لا ينجح الحظر في بعض الإصدارات.
            """.trimIndent()
            textSize = 16f
            setPadding(0, 0, 0, 24)
        }

        val auto = Switch(this).apply {
            text = "تفعيل الحظر التلقائي"
            isChecked = prefs.getBoolean("enabled", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("enabled", checked).apply()
            }
        }

        val contactsBtn = Button(this).apply {
            text = "السماح بقراءة جهات الاتصال"
            setOnClickListener {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_CONTACTS)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.READ_CONTACTS),
                        100
                    )
                }
            }
        }

        val notifBtn = Button(this).apply {
            text = "فتح وصول الإشعارات"
            setOnClickListener {
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
        }

        val accBtn = Button(this).apply {
            text = "فتح إمكانية الوصول"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val status = TextView(this).apply {
            textSize = 15f
            setPadding(0, 20, 0, 0)
        }

        layout.addView(title)
        layout.addView(info)
        layout.addView(auto)
        layout.addView(contactsBtn)
        layout.addView(notifBtn)
        layout.addView(accBtn)
        layout.addView(status)

        setContentView(layout)

        updateStatus(status)
    }

    override fun onResume() {
        super.onResume()
        val root = findViewById<LinearLayout>(android.R.id.content)
        // Status is refreshed by recreating the view when needed.
    }

    private fun updateStatus(status: TextView) {
        val contacts = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        val component = ComponentName(this, BlockAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()

        val accessibility = enabledServices.contains(component.flattenToString(), ignoreCase = true)

        status.text = "الحالة:\n" +
                "جهات الاتصال: ${if (contacts) "✓" else "✗"}\n" +
                "Accessibility: ${if (accessibility) "✓" else "✗"}\n" +
                "الحظر التلقائي: ${if (prefs.getBoolean("enabled", false)) "مفعّل" else "متوقف"}"
    }
}
