package com.example.autotoucher.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.autotoucher.data.model.ActionType

/**
 * 动作步骤实体。
 *
 * @param stepIndex           步骤序号（0-based），决定执行顺序
 * @param type                操作类型：TAP / BACK / HOME / CLOSE_ALL
 * @param x                   屏幕像素坐标 X（仅 TAP 时有意义）
 * @param y                   屏幕像素坐标 Y（仅 TAP 时有意义）
 * @param overrideDelayMinSec 步骤级延迟覆盖最小值（null = 使用任务级配置）
 * @param overrideDelayMaxSec 步骤级延迟覆盖最大值（null = 使用任务级配置）
 */
@Entity(
    tableName = "actions",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class ActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int,
    val stepIndex: Int,
    val type: ActionType,
    val x: Int = 0,
    val y: Int = 0,
    val overrideDelayMinSec: Int? = null,
    val overrideDelayMaxSec: Int? = null
)
