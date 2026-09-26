package com.medoo.autoblocker

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Intent
import android.os.Bundle
import android.telephony.PhoneNumberUtils
import java.util.regex.Pattern

class WhatsAppNotificationListener : NotificationListenerService() {

    private val prefs by lazy { getSharedPreferences("medoo", MODE_PRIVATE) }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!prefs.getBoolean("enabled", false)) return
        if (sbn.packageName != "com.whatsapp" && sbn.packageName != "com.whatsapp.w4b") return

        val n = sbn.notification ?: return
        val extras = n.extras ?: return

        val title = extras.getString(Notification.EXTRA_TITLE).orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        // Only act when a phone number can be found in the notification.
        // This deliberately avoids guessing a person's identity from a display name.
        val number = extractPhone(title) ?: extractPhone(text) ?: extractPhone(bigText) ?: return

        if (number.length < 7) return

        if (ContactUtils.isSavedContact(this, number)) return

        // Ignore our own recent duplicate events.
        val key = "last_$number"
        val now = System.currentTimeMillis()
        val last = prefs.getLong(key, 0L)
        if (now - last < 30_000L) return
        prefs.edit().putLong(key, now).apply()

        // AccessibilityService is system-managed, so we do not start it directly.
        // Store the pending number, then open the chat. The enabled accessibility
        // service will pick up this pending request from SharedPreferences.
        prefs.edit()
            .putString("pending_number", number)
            .putString("pending_package", sbn.packageName)
            .apply()

        val digits = number.filter { it.isDigit() }
        if (digits.isBlank()) return

        val uri = android.net.Uri.parse("https://wa.me/$digits")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(sbn.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {
                // Leave the pending request stored; it can be retried when WhatsApp opens.
            }
        }
    }

    private fun extractPhone(s: String): String? {
        // Supports common international forms such as +2010..., 002010..., etc.
        val p = Pattern.compile("""(?<!\d)(?:\+\d{7,15}|00\d{7,16})(?!\d)""")
        val m = p.matcher(s)
        return if (m.find()) PhoneNumberUtils.normalizeNumber(m.group()) else null
    }
}
