package com.example.autotoucher.data.model

import com.example.autotoucher.data.db.ActionEntity
import com.example.autotoucher.data.db.TaskEntity

data class TaskWithActions(
    val task: TaskEntity,
    val actions: List<ActionEntity>
)
