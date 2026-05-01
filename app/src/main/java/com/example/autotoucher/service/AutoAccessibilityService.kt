package com.example.autotoucher.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 无障碍服务：作为全局手势注入入口。
 *
 * 通过 [instance] 单例在进程内暴露给 [TaskExecutorService] 调用。
 * 用户需在系统设置 → 无障碍 中手动开启本服务。
 */
class AutoAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoAccessibilitySvc"

        @Volatile
        var instance: AutoAccessibilityService? = null
            private set

        /** 检查无障碍服务是否已连接并可用。 */
        fun isEnabled(): Boolean = instance != null
    }

    // ── 生命周期 ────────────────────────────────────────────

    override fun onServiceConnected() {
        instance = this
        // 仅订阅窗口状态变化事件（最小化权限占用）
        serviceInfo = serviceInfo?.also {
            it.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            it.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            it.notificationTimeout = 100
        }
        Log.i(TAG, "AutoAccessibilityService connected")
    }

    override fun onDestroy() {
        instance = null
        Log.i(TAG, "AutoAccessibilityService destroyed")
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* 不需要处理事件 */ }
    override fun onInterrupt() { /* 不需要处理中断 */ }

    // ── 公开 API ─────────────────────────────────────────────

    /**
     * 注入单次点击手势（回调版本）。
     *
     * @param x        屏幕像素 X 坐标
     * @param y        屏幕像素 Y 坐标
     * @param callback 手势完成/取消回调（在主线程回调）
     */
    fun tap(x: Float, y: Float, callback: GestureResultCallback) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, callback, null)
    }

    /**
     * 注入单次点击手势（协程版本，挂起直到手势完成）。
     *
     * @throws RuntimeException 手势被取消时抛出
     */
    suspend fun tapSuspend(x: Float, y: Float) = suspendCancellableCoroutine { cont ->
        tap(x, y, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (cont.isActive) cont.resume(Unit)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (cont.isActive) cont.resumeWithException(
                    RuntimeException("Gesture cancelled at ($x, $y)")
                )
            }
        })
    }

    /**
     * 执行系统全局动作。
     *
     * @param action [GLOBAL_ACTION_BACK] / [GLOBAL_ACTION_HOME] /
     *               [GLOBAL_ACTION_RECENTS] 等常量
     * @return true = 执行成功
     */
    fun globalAction(action: Int): Boolean = performGlobalAction(action)

    /**
     * 在当前窗口节点树中查找包含指定关键词的可点击节点并点击。
     *
     * 用于 CLOSE_ALL 操作：各厂商最近任务界面的"清除全部"按钮文字不同，
     * 通过关键词模糊匹配兼容主流 ROM。
     *
     * @param keywords 关键词列表（小写），任一匹配即视为目标
     * @return true = 找到并点击成功
     */
    fun findAndClickNodeByText(keywords: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val target = findClickableNode(root, keywords)
            if (target != null) {
                try {
                    target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } finally {
                    target.recycle()
                }
            } else {
                false
            }
        } finally {
            root.recycle()
        }
    }

    /**
     * 深度优先遍历节点树，返回第一个文本或 contentDescription 包含关键词
     * 且 isClickable = true 的节点。
     *
     * 调用者负责 recycle 返回的节点；中间遍历节点在内部 recycle。
     */
    private fun findClickableNode(
        node: AccessibilityNodeInfo,
        keywords: List<String>
    ): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (node.isClickable && keywords.any { (text + " " + desc).contains(it) }) {
            // 返回一个新引用，调用者负责 recycle
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findClickableNode(child, keywords)
            child.recycle()
            if (result != null) return result
        }
        return null
    }
}
