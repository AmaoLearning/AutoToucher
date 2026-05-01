package com.example.autotoucher.data.repository

import com.example.autotoucher.data.db.ActionEntity
import com.example.autotoucher.data.db.AppDatabase
import com.example.autotoucher.data.db.TaskEntity
import com.example.autotoucher.data.model.TaskWithActions
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class TaskRepository(private val db: AppDatabase) {

    /** 观察所有任务（UI 订阅用的 Flow）。 */
    val allTasks: Flow<List<TaskEntity>> = db.taskDao().getAllTasks()

    /**
     * 获取带步骤的完整任务（执行序列或编辑时用）。
     * @return null 表示任务不存在
     */
    suspend fun getTaskWithActions(taskId: Int): TaskWithActions? {
        val task = db.taskDao().getTaskById(taskId) ?: return null
        val actions = db.actionDao().getActionsForTaskSync(taskId)
        return TaskWithActions(task, actions)
    }

    /**
     * 保存任务及其所有步骤（事务保证原子性）。
     * - task.id == 0：新增，Room 自动生成 id
     * - task.id > 0 ：更新现有任务
     * @return 任务的最终 id（Long 类型以兼容 Room 的 insert 返回值）
     */
    suspend fun saveTaskWithActions(
        task: TaskEntity,
        actions: List<ActionEntity>
    ): Long = db.withTransaction {
        val taskId: Long = if (task.id == 0) {
            db.taskDao().insertTask(task)
        } else {
            db.taskDao().updateTask(task)
            task.id.toLong()
        }

        // 先清空旧步骤，再按当前顺序重新插入
        db.actionDao().deleteActionsForTask(taskId.toInt())
        val actionsToInsert = actions.mapIndexed { index, action ->
            // id 重置为 0，让 Room 为每条新步骤生成新 id
            action.copy(id = 0, taskId = taskId.toInt(), stepIndex = index)
        }
        db.actionDao().insertActions(actionsToInsert)

        taskId
    }

    /** 删除任务（Room 级联删除关联的 actions）。 */
    suspend fun deleteTask(task: TaskEntity) {
        db.taskDao().deleteTask(task)
    }

    /** 切换单个任务的启用状态。 */
    suspend fun setTaskEnabled(taskId: Int, enabled: Boolean) {
        db.taskDao().setEnabled(taskId, enabled)
    }

    /** 获取所有已启用的任务（开机重新注册闹钟时用）。 */
    suspend fun getEnabledTasks(): List<TaskEntity> = db.taskDao().getEnabledTasks()
}
