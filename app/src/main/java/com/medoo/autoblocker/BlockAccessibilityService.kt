package com.medoo.autoblocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class BlockAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_BLOCK_NUMBER = "com.medoo.autoblocker.BLOCK_NUMBER"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_PACKAGE = "package"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingNumber: String? = null
    private var pendingPackage: String = "com.whatsapp"
    private var step = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        loadPending()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return

        if (pendingNumber == null) loadPending()
        if (pendingNumber == null) return

        // Try repeatedly because WhatsApp's UI is asynchronous.
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ runAutomation() }, 350)
    }

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
        pendingNumber = null
        step = 0
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        loadPending()
        return START_NOT_STICKY
    }

    private fun loadPending() {
        val prefs = getSharedPreferences("medoo", MODE_PRIVATE)
        pendingNumber = prefs.getString("pending_number", null)
        pendingPackage = prefs.getString("pending_package", "com.whatsapp") ?: "com.whatsapp"
        if (pendingNumber != null) step = 0
    }

    private fun clearPending() {
        getSharedPreferences("medoo", MODE_PRIVATE).edit()
            .remove("pending_number")
            .remove("pending_package")
            .apply()
        pendingNumber = null
        step = 0
    }

    private fun runAutomation() {
        val root = rootInActiveWindow ?: run {
            retry()
            return
        }

        when (step) {
            0 -> {
                // Chat should be open. Tap the toolbar overflow.
                val clicked = clickByAnyDescription(root, listOf(
                    "More options", "خيارات إضافية", "المزيد", "More"
                ))
                if (clicked) {
                    step = 1
                    retry(600)
                } else {
                    // Some WhatsApp versions expose the overflow as a clickable node
                    // with no useful text/content description.
                    val toolbar = findClickableToolbarButton(root)
                    if (toolbar != null) {
                        toolbar.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        step = 1
                        retry(600)
                    } else {
                        retry()
                    }
                }
            }

            1 -> {
                // First menu: try direct Block, otherwise More.
                val blocked = clickByText(root, listOf("Block", "حظر", "حظر جهة الاتصال"))
                if (blocked) {
                    step = 2
                    retry(500)
                    return
                }

                val more = clickByText(root, listOf("More", "المزيد"))
                if (more) {
                    step = 2
                    retry(500)
                    return
                }

                retry()
            }

            2 -> {
                // If WhatsApp shows a confirmation dialog, click Block.
                val confirmed = clickByText(root, listOf("Block", "حظر", "BLOCK"))
                if (confirmed) {
                    clearPending()
                    return
                }

                // It may have already blocked and returned to the chat.
                if (containsText(root, listOf("Unblock", "إلغاء الحظر"))) {
                    clearPending()
                    return
                }

                retry()
            }
        }
    }

    private fun retry(delay: Long = 700) {
        handler.postDelayed({ runAutomation() }, delay)
    }

    private fun clickByText(root: AccessibilityNodeInfo, candidates: List<String>): Boolean {
        for (candidate in candidates) {
            val nodes = root.findAccessibilityNodeInfosByText(candidate)
            for (node in nodes) {
                if (node.isVisibleToUser && node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
                var p = node.parent
                while (p != null) {
                    if (p.isVisibleToUser && p.isClickable) {
                        p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return true
                    }
                    p = p.parent
                }
            }
        }
        return false
    }

    private fun clickByAnyDescription(root: AccessibilityNodeInfo, candidates: List<String>): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val d = node.contentDescription?.toString().orEmpty()
            if (node.isVisibleToUser && node.isClickable &&
                candidates.any { d.equals(it, ignoreCase = true) || d.contains(it, ignoreCase = true) }) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return false
    }

    private fun containsText(root: AccessibilityNodeInfo, candidates: List<String>): Boolean {
        return candidates.any { root.findAccessibilityNodeInfosByText(it).isNotEmpty() }
    }

    private fun findClickableToolbarButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val rect = Rect()
            node.getBoundsInScreen(rect)
            // Heuristic: top-right clickable node.
            if (node.isVisibleToUser && node.isClickable && rect.top < 220 && rect.right > resources.displayMetrics.widthPixels * 0.80) {
                return node
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }
}
