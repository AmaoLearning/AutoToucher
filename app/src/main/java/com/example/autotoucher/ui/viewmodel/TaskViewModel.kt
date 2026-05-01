package com.example.autotoucher.ui.viewmodel

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.autotoucher.data.db.ActionEntity
import com.example.autotoucher.data.db.AppDatabase
import com.example.autotoucher.data.db.TaskEntity
import com.example.autotoucher.data.model.TaskWithActions
import com.example.autotoucher.data.repository.TaskRepository
import com.example.autotoucher.scheduler.AlarmScheduler
import com.example.autotoucher.service.AutoAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TaskRepository(AppDatabase.getInstance(application))

    /** 所有任务列表（UI 订阅用）。 */
    val tasks: StateFlow<List<TaskEntity>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前正在编辑的任务（null = 未进入编辑态）。 */
    private val _editingTask = MutableStateFlow<TaskWithActions?>(null)
    val editingTask: StateFlow<TaskWithActions?> = _editingTask.asStateFlow()

    // ── 权限状态 ─────────────────────────────────────────────
    // 用时间戳 trigger 驱动权限状态重新计算（用户从系统设置返回后调用 refreshPermissions()）

    private val _refreshTrigger = MutableStateFlow(0L)

    val accessibilityEnabled: StateFlow<Boolean> = _refreshTrigger
        .map { AutoAccessibilityService.isEnabled() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            AutoAccessibilityService.isEnabled())

    val exactAlarmGranted: StateFlow<Boolean> = _refreshTrigger
        .map { isExactAlarmGranted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), isExactAlarmGranted())

    val notificationGranted: StateFlow<Boolean> = _refreshTrigger
        .map { isNotificationGranted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), isNotificationGranted())

    /** 从权限设置页返回后调用，强制刷新权限状态。 */
    fun refreshPermissions() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    // ── 编辑态管理 ────────────────────────────────────────────

    /** 加载已有任务到编辑态。 */
    fun loadTaskForEdit(taskId: Int) {
        _editingTask.value = null  // 先清空旧数据，防止 LaunchedEffect 用脏数据提前初始化
        viewModelScope.launch {
            _editingTask.value = repository.getTaskWithActions(taskId)
        }
    }

    /** 初始化一个空任务到编辑态（新建）。 */
    fun newTask() {
        _editingTask.value = TaskWithActions(
            task = TaskEntity(
                name = "",
                triggerHour = 9,
                triggerMinute = 0,
                delayMinSec = 0,
                delayMaxSec = 5,
                stepDelayMinSec = 4,
                stepDelayMaxSec = 8
            ),
            actions = emptyList()
        )
    }

    // ── CRUD + 闹钟联动 ──────────────────────────────────────

    /** 保存任务及步骤列表，同步注册/取消闹钟。 */
    fun saveTask(task: TaskEntity, actions: List<ActionEntity>) {
        viewModelScope.launch {
            val savedId = repository.saveTaskWithActions(task, actions)
            val savedTask = task.copy(id = savedId.toInt())
            if (savedTask.enabled) {
                AlarmScheduler.schedule(getApplication(), savedTask)
            } else {
                AlarmScheduler.cancel(getApplication(), savedTask.id)
            }
        }
    }

    /** 删除任务并取消对应闹钟。 */
    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            AlarmScheduler.cancel(getApplication(), task.id)
            repository.deleteTask(task)
        }
    }

    /** 切换任务启用/禁用，同步更新闹钟注册状态。 */
    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch {
            val newEnabled = !task.enabled
            repository.setTaskEnabled(task.id, newEnabled)
            if (newEnabled) {
                AlarmScheduler.schedule(getApplication(), task.copy(enabled = true))
            } else {
                AlarmScheduler.cancel(getApplication(), task.id)
            }
        }
    }

    // ── 私有工具 ─────────────────────────────────────────────

    private fun isExactAlarmGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = getApplication<Application>().getSystemService(AlarmManager::class.java)
        return am.canScheduleExactAlarms()
    }

    private fun isNotificationGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
