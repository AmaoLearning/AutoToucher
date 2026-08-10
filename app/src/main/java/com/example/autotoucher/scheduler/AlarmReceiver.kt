package com.example.autotoucher.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.autotoucher.service.TaskExecutorService
import com.example.autotoucher.ui.WakeActivity

/**
 * 闹钟广播接收器：由 [AlarmScheduler] 触发，负责启动 [TaskExecutorService] 执行任务。
 *
 * 声明为 `android:exported="false"`，仅接受来自同进程 AlarmManager 的广播。
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(TaskExecutorService.EXTRA_TASK_ID, -1)
        if (taskId == -1) {
            Log.w(TAG, "Received alarm broadcast with no task_id, ignoring.")
            return
        }

        Log.i(TAG, "Alarm fired for task $taskId, starting TaskExecutorService.")

        // 必须在系统交付闹钟 PendingIntent 的短暂后台启动豁免窗口内直接拉起
        // WakeActivity。若先转交给 Service 再启动 Activity，新版 Android/部分 ROM
        // 可能把它视为普通后台 Activity 启动并拦截。
        TaskExecutorService.prepareWakeSession()
        try {
            context.startActivity(WakeActivity.buildIntent(context))
        } catch (e: RuntimeException) {
            // Service 仍会在超时后继续执行并重新注册闹钟，不能让单次唤醒失败
            // 中断整个每日调度链路。
            Log.e(TAG, "Unable to launch WakeActivity for task $taskId.", e)
        }

        val serviceIntent = TaskExecutorService.buildIntent(context, taskId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
