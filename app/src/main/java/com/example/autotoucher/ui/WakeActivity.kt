package com.example.autotoucher.ui

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.autotoucher.service.TaskExecutorService
import kotlinx.coroutines.launch

/**
 * 锁屏唤醒 Activity：任务执行前由 TaskExecutorService 启动。
 *
 * 职责：
 * 1. 通过窗口标志唤亮屏幕，并覆盖显示在锁屏界面之上
 * 2. 尝试解除锁屏（无 PIN/图案时自动完成；有加密锁屏时屏幕亮起但锁屏保留）
 * 3. 向 TaskExecutorService 发出「屏幕已就绪」信号，允许手势注入开始
 * 4. 监听任务完成信号，执行结束后自动 finish()，锁屏恢复正常
 *
 * 注意：使用透明主题（Theme.AutoToucher.Transparent），对用户几乎不可见。
 */
class WakeActivity : ComponentActivity() {

    companion object {
        private const val TAG = "WakeActivity"
    }

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
        // 保持屏幕常亮，直到任务执行完毕
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        Log.i(TAG, "WakeActivity created; screen should be turning on.")

        // ── 2. 尝试解除锁屏 ──────────────────────────────────────────────────
        val km = getSystemService(KeyguardManager::class.java)
        when {
            !km.isKeyguardLocked -> {
                // 当前无锁屏，直接发出就绪信号
                Log.i(TAG, "Keyguard not locked. Signaling ready immediately.")
                TaskExecutorService.keyguardReady.value = true
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                // API 26+：通过系统 API 请求解锁
                // 无加密锁（仅滑动/无密码）：自动解锁
                // 有加密锁（PIN/图案/指纹）：弹出验证界面，用户需手动解锁
                Log.i(TAG, "Keyguard locked. Requesting dismiss (API 26+).")
                km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        Log.i(TAG, "Keyguard dismissed successfully.")
                        TaskExecutorService.keyguardReady.value = true
                    }
                    override fun onDismissCancelled() {
                        // 用户取消解锁，或锁屏已被其他方式解除
                        Log.w(TAG, "Keyguard dismiss cancelled.")
                        TaskExecutorService.keyguardReady.value = true
                    }
                    override fun onDismissError() {
                        // 无法解锁（通常是设备有加密锁）
                        // 仍发出信号；手势将作用于当前可见界面（可能是锁屏）
                        Log.w(TAG, "Keyguard dismiss error (secure lock set). Gestures may target lock screen.")
                        TaskExecutorService.keyguardReady.value = true
                    }
                })
            }
            else -> {
                // API < 26 不应出现（minSdk=26），保守回退
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(1500L)
                    TaskExecutorService.keyguardReady.value = true
                }
            }
        }

        // ── 3. 监听任务完成信号，完成后退出 ─────────────────────────────────
        lifecycleScope.launch {
            TaskExecutorService.executionComplete.collect { done ->
                if (done) {
                    Log.i(TAG, "Task execution complete. Finishing WakeActivity.")
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "WakeActivity destroyed.")
    }
}
