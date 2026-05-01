package com.example.autotoucher.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 定时任务实体。
 *
 * @param triggerHour    触发小时（0-23）
 * @param triggerMinute  触发分钟（0-59）
 * @param delayMinSec    任务启动随机延迟最小秒数（默认 0）
 * @param delayMaxSec    任务启动随机延迟最大秒数（默认 5）
 * @param stepDelayMinSec 步骤间隔随机最小秒数（默认 4）
 * @param stepDelayMaxSec 步骤间隔随机最大秒数（默认 8）
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val triggerHour: Int,
    val triggerMinute: Int,
    val delayMinSec: Int = 0,
    val delayMaxSec: Int = 5,
    val stepDelayMinSec: Int = 4,
    val stepDelayMaxSec: Int = 8,
    val enabled: Boolean = true
)
