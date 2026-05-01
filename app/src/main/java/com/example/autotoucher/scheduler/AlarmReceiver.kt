package com.example.autotoucher.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.autotoucher.service.TaskExecutorService

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

        val serviceIntent = TaskExecutorService.buildIntent(context, taskId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
