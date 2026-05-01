package com.example.autotoucher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionDao {

    /** 观察某任务的步骤列表（按 stepIndex 排序），UI 订阅用。 */
    @Query("SELECT * FROM actions WHERE taskId = :taskId ORDER BY stepIndex")
    fun getActionsForTask(taskId: Int): Flow<List<ActionEntity>>

    /** 同步获取某任务的步骤列表（执行序列时用）。 */
    @Query("SELECT * FROM actions WHERE taskId = :taskId ORDER BY stepIndex")
    suspend fun getActionsForTaskSync(taskId: Int): List<ActionEntity>

    /** 批量插入步骤（保存任务时先清空再插入）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActions(actions: List<ActionEntity>)

    /** 删除某任务的所有步骤（保存时先清理旧数据）。 */
    @Query("DELETE FROM actions WHERE taskId = :taskId")
    suspend fun deleteActionsForTask(taskId: Int)
}
