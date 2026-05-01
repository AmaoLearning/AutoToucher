package com.example.autotoucher.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.autotoucher.data.db.AppDatabase
import com.example.autotoucher.data.repository.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机广播接收器：系统重启后重新注册所有已启用任务的闹钟。
 *
 * Android 在重启后会清除所有 AlarmManager 注册的闹钟，
 * 需要通过监听 [Intent.ACTION_BOOT_COMPLETED] 在开机后恢复。
 *
 * 需在 AndroidManifest 声明 `android:exported="true"` 及 RECEIVE_BOOT_COMPLETED 权限。
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "Boot completed, re-scheduling enabled tasks.")

        // goAsync() 让 BroadcastReceiver 在主线程以外完成异步操作
        // 但由于 onReceive 本身超时限制为 10s，此处使用独立 IO 协程足够：
        // 仅读取 DB + 注册 AlarmManager，均为快速操作。
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = TaskRepository(AppDatabase.getInstance(context))
                val tasks = repo.getEnabledTasks()
                Log.i(TAG, "Re-scheduling ${tasks.size} enabled task(s).")
                tasks.forEach { task ->
                    AlarmScheduler.schedule(context, task)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to re-schedule tasks on boot: ${e.message}", e)
            }
        }
    }
}
