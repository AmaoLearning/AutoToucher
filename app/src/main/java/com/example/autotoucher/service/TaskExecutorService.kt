package com.example.autotoucher.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.autotoucher.MainActivity
import com.example.autotoucher.R
import com.example.autotoucher.data.db.ActionEntity
import com.example.autotoucher.data.db.AppDatabase
import com.example.autotoucher.data.model.ActionType
import com.example.autotoucher.data.repository.TaskRepository
import com.example.autotoucher.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * 前台服务：接收来自 [AlarmScheduler] 广播后，按顺序执行任务的动作序列。
 *
 * 执行流程：
 * 1. 随机延迟（任务级别）
 * 2. 逐步执行 [ActionEntity] 列表
 * 3. 执行完毕后重新注册下一天闹钟
 */
class TaskExecutorService : Service() {

    private lateinit var repository: TaskRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var executionWakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val TAG = "TaskExecutorService"
        const val EXTRA_TASK_ID = "task_id"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "autotoucher_exec"
        private const val WAKE_READY_TIMEOUT_MS = 10_000L
        private const val MAX_EXECUTION_WAKE_MS = 10 * 60 * 1000L

        fun buildIntent(context: Context, taskId: Int): Intent =
            Intent(context, TaskExecutorService::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
            }

        /** 由 AlarmReceiver 在启动 WakeActivity 和 Service 前重置本次协调状态。 */
        fun prepareWakeSession() {
            keyguardReady.value = false
        }

        // ── 屏幕唤醒协调状态（与 WakeActivity 通信）──────────────
        /** WakeActivity 屏幕就绪（解锁）后置 true，Service 等待此信号再注入手势 */
        val keyguardReady = MutableStateFlow(false)
    }

    // ── 生命周期 ────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        repository = TaskRepository(AppDatabase.getInstance(applicationContext))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getIntExtra(EXTRA_TASK_ID, -1) ?: -1
        if (taskId == -1) {
            Log.w(TAG, "Received intent with no task_id, stopping.")
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        // Android 14+ 需要在 startForeground 中声明 foregroundServiceType
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquireExecutionWakeLock()

        serviceScope.launch {
            try {
                // Activity 被 ROM 拦截或系统拒绝解锁时不能永久挂起，否则下一天的
                // 闹钟也不会重新注册。无密码锁屏通常会在此窗口内完成解除。
                val ready = withTimeoutOrNull(WAKE_READY_TIMEOUT_MS) {
                    keyguardReady.first { it }
                } != null
                if (!ready) {
                    Log.w(TAG, "Timed out waiting for screen/keyguard readiness; continuing task.")
                }
                executeTask(taskId)
            } catch (e: Exception) {
                Log.e(TAG, "Task $taskId failed: ${e.message}", e)
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseExecutionWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── 核心执行逻辑 ──────────────────────────────────────────

    private suspend fun executeTask(taskId: Int) {
        val taskWithActions = repository.getTaskWithActions(taskId)
        if (taskWithActions == null) {
            Log.w(TAG, "Task $taskId not found in database.")
            return
        }
        val task = taskWithActions.task
        val actions = taskWithActions.actions

        Log.i(TAG, "Starting task '${task.name}' with ${actions.size} actions.")

        // 1. 任务级随机启动延迟
        val startDelay = randomDelay(task.delayMinSec, task.delayMaxSec)
        if (startDelay > 0) {
            Log.i(TAG, "Start delay: ${startDelay}s")
            delay(startDelay * 1000L)
        }

        // 2. 逐步执行动作序列
        for ((index, action) in actions.withIndex()) {
            Log.i(TAG, "Step ${index + 1}/${actions.size}: ${action.type}" +
                    if (action.type == ActionType.TAP) " (${action.x}, ${action.y})" else "")

            executeAction(action)

            // 最后一步之后不等待
            if (index < actions.lastIndex) {
                val stepDelay = randomDelay(
                    action.overrideDelayMinSec ?: task.stepDelayMinSec,
                    action.overrideDelayMaxSec ?: task.stepDelayMaxSec
                )
                if (stepDelay > 0) delay(stepDelay * 1000L)
            }
        }

        Log.i(TAG, "Task '${task.name}' completed. Re-scheduling for tomorrow.")

        // 3. 重新注册下一天的闹钟
        AlarmScheduler.schedule(applicationContext, task)
    }

    private suspend fun executeAction(action: ActionEntity) {
        val svc = AutoAccessibilityService.instance
        if (svc == null) {
            Log.w(TAG, "AccessibilityService not connected, skipping action ${action.type}.")
            return
        }
        when (action.type) {
            ActionType.TAP -> {
                svc.tapSuspend(action.x.toFloat(), action.y.toFloat())
            }
            ActionType.BACK -> {
                svc.globalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }
            ActionType.HOME -> {
                svc.globalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            }
            ActionType.CLOSE_ALL -> {
                executeCloseAll(svc)
            }
        }
    }

    /**
     * 关闭所有后台应用：
     * 1. 打开最近任务界面
     * 2. 等待动画完成
     * 3. 查找并点击"清除全部"按钮（兼容各厂商 ROM）
     * 4. 若未找到按钮，执行 HOME 兜底
     */
    private suspend fun executeCloseAll(svc: AutoAccessibilityService) {
        svc.globalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        // 等待最近任务界面动画加载完成（不同设备速度有差异）
        delay(800L)

        // 各厂商"清除全部"按钮文字各异，使用多关键词模糊匹配
        val clearKeywords = listOf("清除", "清空", "全部清除", "一键清理", "clear", "clear all")
        val clicked = svc.findAndClickNodeByText(clearKeywords)

        if (clicked) {
            Log.i(TAG, "CLOSE_ALL: found and clicked clear button.")
        } else {
            Log.w(TAG, "CLOSE_ALL: clear button not found, falling back to HOME.")
            svc.globalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
    }

    // ── 工具方法 ────────────────────────────────────────────

    /** 在 [min, max] 范围内生成随机整数秒数，min > max 时安全返回 min。 */
    private fun randomDelay(min: Int, max: Int): Int =
        if (max > min) Random.nextInt(min, max + 1) else min

    /**
     * 窗口的 turnScreenOn 是首选唤醒方式；屏幕 WakeLock 是 API 26 及厂商 ROM 的
     * 兜底，同时在透明 Activity 退出后保持屏幕可交互。系统超时保证异常退出时
     * 不会无限耗电。
     */
    @Suppress("DEPRECATION")
    private fun acquireExecutionWakeLock() {
        if (executionWakeLock?.isHeld == true) return
        try {
            val powerManager = getSystemService(PowerManager::class.java)
            executionWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "$packageName:task-execution"
            ).apply {
                setReferenceCounted(false)
                acquire(MAX_EXECUTION_WAKE_MS)
            }
            Log.i(TAG, "Execution wake lock acquired.")
        } catch (e: SecurityException) {
            executionWakeLock = null
            Log.w(TAG, "Screen wake lock denied; relying on WakeActivity turnScreenOn.", e)
        }
    }

    private fun releaseExecutionWakeLock() {
        executionWakeLock?.let { wakeLock ->
            if (wakeLock.isHeld) wakeLock.release()
        }
        executionWakeLock = null
    }

    // ── 通知构建 ────────────────────────────────────────────

    private fun buildNotification(): Notification {
        // Android 8+ 必须先创建 NotificationChannel
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AutoToucher 执行",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "任务执行时在通知栏显示进度"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)

        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoToucher 执行中")
            .setContentText("正在执行预设点击任务…")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
