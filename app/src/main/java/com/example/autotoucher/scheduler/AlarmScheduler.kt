package com.example.autotoucher.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.autotoucher.data.db.TaskEntity
import com.example.autotoucher.service.TaskExecutorService
import java.util.Calendar

/**
 * 闹钟调度器：使用 AlarmManager 为每个启用的任务注册每日精确闹钟。
 *
 * - API < 31：直接使用 setExactAndAllowWhileIdle（无需运行时授权）
 * - API >= 31（Android 12+）：检查 canScheduleExactAlarms()；
 *   若未授权则降级为 setAndAllowWhileIdle（非精确，误差可能 >15min）
 *
 * PendingIntent requestCode 直接使用 taskId，确保同 taskId 可幂等更新/取消。
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"

    // ── 公开 API ──────────────────────────────────────────────

    /**
     * 为单个任务注册每日精确闹钟。
     * 若今天触发时间已过，则定为明天；否则定为今天。
     * [task.enabled] 为 false 时自动跳过。
     */
    fun schedule(context: Context, task: TaskEntity) {
        if (!task.enabled) return

        val triggerAt = calcNextTriggerMillis(task.triggerHour, task.triggerMinute)
        val pi = buildPendingIntent(context, task.id)
        val am = context.getSystemService(AlarmManager::class.java)

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    Log.i(TAG, "Exact alarm set for task ${task.id} '${task.name}' at $triggerAt")
                } else {
                    // 未获精确闹钟权限，降级为非精确（PermissionGuideScreen 引导用户授权）
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    Log.w(TAG, "Exact alarm permission not granted; using inexact for task ${task.id}")
                }
            }
            else -> {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                Log.i(TAG, "Exact alarm set for task ${task.id} '${task.name}' at $triggerAt")
            }
        }
    }

    /** 取消指定任务的闹钟。 */
    fun cancel(context: Context, taskId: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = buildPendingIntent(context, taskId)
        am.cancel(pi)
        pi.cancel()
        Log.i(TAG, "Alarm cancelled for task $taskId")
    }

    /** 批量取消闹钟（如禁用所有任务时使用）。 */
    fun cancelAll(context: Context, taskIds: List<Int>) {
        taskIds.forEach { cancel(context, it) }
    }

    // ── 内部工具 ────────────────────────────────────────────

    /**
     * 计算下次触发的 UTC 毫秒时间戳。
     * - 若今天 [hour]:[minute] 的时刻 > 当前时间，则触发时间 = 今天该时刻
     * - 否则触发时间 = 明天该时刻
     */
    internal fun calcNextTriggerMillis(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    /**
     * 构建指向 [AlarmReceiver] 的 PendingIntent。
     * 以 taskId 为 requestCode，确保同一任务的 Intent 可被幂等更新或取消。
     */
    private fun buildPendingIntent(context: Context, taskId: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(TaskExecutorService.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
