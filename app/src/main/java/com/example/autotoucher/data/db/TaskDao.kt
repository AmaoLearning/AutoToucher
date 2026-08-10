package com.example.autotoucher.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    /**
     * 观察所有任务（按创建顺序稳定排列），UI 订阅用。
     *
     * id 是自增主键，使用它排序可保证新增任务只追加到列表末尾；不能按触发时间
     * 排序，否则每次插入较早的时间都会让已有卡片换位，同一时间的任务也没有
     * 确定顺序。
     */
    @Query("SELECT * FROM tasks ORDER BY id ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    /** 按 ID 查询单个任务（挂起，执行时用）。 */
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): TaskEntity?

    /** 获取所有已启用的任务（开机重新注册闹钟时用）。 */
    @Query("SELECT * FROM tasks WHERE enabled = 1")
    suspend fun getEnabledTasks(): List<TaskEntity>

    /** 新增任务，返回自动生成的 rowId。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTask(task: TaskEntity): Long

    /** 更新已存在的任务。 */
    @Update
    suspend fun updateTask(task: TaskEntity)

    /** 删除任务（级联删除关联的 actions）。 */
    @Delete
    suspend fun deleteTask(task: TaskEntity)

    /** 切换单个任务的启用状态。 */
    @Query("UPDATE tasks SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Int, enabled: Boolean)
}
