package com.example.autotoucher.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import com.example.autotoucher.service.TaskExecutorService

/**
 * 锁屏唤醒 Activity：任务执行前由 TaskExecutorService 启动。
 *
 * 职责：
 * 1. 通过窗口标志唤亮屏幕，并覆盖显示在锁屏界面之上
 * 2. 尝试解除锁屏（无 PIN/图案时自动完成；有加密锁屏时屏幕亮起但锁屏保留）
 * 3. 向 TaskExecutorService 发出「屏幕已就绪」信号并立即退出，让手势落到
 *    锁屏下方原本可见的界面，而不是被透明 Activity 截获
 *
 * 注意：使用透明主题（Theme.AutoToucher.Transparent），对用户几乎不可见。
 */
class WakeActivity : ComponentActivity() {

    companion object {
        private const val TAG = "WakeActivity"

        fun buildIntent(context: Context): Intent =
            Intent(context, WakeActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
    }

    private lateinit var keyguardManager: KeyguardManager
    private var dismissRequested = false
    private var readySignaled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── 1. 唤亮屏幕并在锁屏上方显示 ──────────────────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // API 27+：推荐方式
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            // API 26 回退（窗口标志方式，已废弃但仍有效）
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        // Activity 退出前保持窗口常亮；之后由执行服务的限时 WakeLock 接管。
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        Log.i(TAG, "WakeActivity created; screen should be turning on.")

        keyguardManager = getSystemService(KeyguardManager::class.java)
    }

    /**
     * requestDismissKeyguard 要求 Activity 已经可见。onCreate 阶段调用在 AOSP 和
     * 厂商 ROM 上都可能回调 onDismissError，因此延迟到 onPostResume。
     */
    override fun onPostResume() {
        super.onPostResume()
        if (dismissRequested || readySignaled) return

        if (!keyguardManager.isKeyguardLocked) {
            Log.i(TAG, "Keyguard not locked. Screen is ready.")
            signalReadyAndFinish()
            return
        }

        dismissRequested = true
        Log.i(TAG, "Keyguard locked. Requesting dismiss from resumed Activity.")
        keyguardManager.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    Log.i(TAG, "Keyguard dismissed successfully.")
                    signalReadyAndFinish()
                }

                override fun onDismissCancelled() {
                    Log.w(TAG, "Keyguard dismiss cancelled; letting Service use its timeout fallback.")
                    finish()
                }

                override fun onDismissError() {
                    Log.w(TAG, "Keyguard dismiss failed; secure lock or ROM policy may require user action.")
                    finish()
                }
            }
        )
    }

    private fun signalReadyAndFinish() {
        if (readySignaled) return
        readySignaled = true
        TaskExecutorService.keyguardReady.value = true
        // 透明窗口若继续存活会成为最上层触摸目标，导致无障碍点击无法到达桌面。
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "WakeActivity destroyed.")
    }
}
