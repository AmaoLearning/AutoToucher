package com.example.autotoucher.ui.screen

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.autotoucher.data.db.ActionEntity
import com.example.autotoucher.data.model.ActionType
import com.example.autotoucher.ui.viewmodel.TaskViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    viewModel: TaskViewModel,
    taskId: Int,
    navController: NavController
) {
    val editingTask by viewModel.editingTask.collectAsState()

    // ── 本地可编辑状态 ──────────────────────────────────────
    var taskName by rememberSaveable { mutableStateOf("") }
    var triggerHour by rememberSaveable { mutableStateOf("09") }
    var triggerMinute by rememberSaveable { mutableStateOf("00") }
    var delayMin by rememberSaveable { mutableStateOf("0") }
    var delayMax by rememberSaveable { mutableStateOf("5") }
    var stepDelayMin by rememberSaveable { mutableStateOf("4") }
    var stepDelayMax by rememberSaveable { mutableStateOf("8") }
    var taskEnabled by rememberSaveable { mutableStateOf(true) }
    val actions = remember { mutableStateListOf<ActionEntity>() }
    var initialized by rememberSaveable { mutableStateOf(false) }

    // 触发 ViewModel 加载
    LaunchedEffect(taskId) {
        if (taskId == -1) viewModel.newTask() else viewModel.loadTaskForEdit(taskId)
    }

    // 加载到本地状态（只初始化一次）
    LaunchedEffect(editingTask) {
        if (editingTask != null && !initialized) {
            val t = editingTask!!.task
            taskName = t.name
            triggerHour = "%02d".format(t.triggerHour)
            triggerMinute = "%02d".format(t.triggerMinute)
            delayMin = t.delayMinSec.toString()
            delayMax = t.delayMaxSec.toString()
            stepDelayMin = t.stepDelayMinSec.toString()
            stepDelayMax = t.stepDelayMaxSec.toString()
            taskEnabled = t.enabled
            actions.clear()
            actions.addAll(editingTask!!.actions)
            initialized = true
        }
    }

    // ActionEditDialog 状态
    var showActionDialog by remember { mutableStateOf(false) }
    var editingActionIndex by remember { mutableStateOf(-1) }  // -1 = 新增
    var dialogAction by remember { mutableStateOf<ActionEntity?>(null) }

    // ── 屏幕分辨率（用于坐标提示）──
    val context = LocalContext.current
    val (screenW, screenH) = remember {
        val wm = context.getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            @Suppress("DEPRECATION")
            val size = android.graphics.Point()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getSize(size)
            size.x to size.y
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (taskId == -1) "新建任务" else "编辑任务") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        // 解析并保存
                        val currentTask = editingTask?.task ?: return@TextButton
                        val hour = triggerHour.toIntOrNull()?.coerceIn(0, 23) ?: 9
                        val minute = triggerMinute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                        val dMin = delayMin.toIntOrNull()?.coerceAtLeast(0) ?: 0
                        val dMax = (delayMax.toIntOrNull()?.coerceAtLeast(dMin) ?: 5)
                        val sMin = stepDelayMin.toIntOrNull()?.coerceAtLeast(0) ?: 4
                        val sMax = (stepDelayMax.toIntOrNull()?.coerceAtLeast(sMin) ?: 8)
                        val task = currentTask.copy(
                            name = taskName.trim(),
                            triggerHour = hour,
                            triggerMinute = minute,
                            delayMinSec = dMin,
                            delayMaxSec = dMax,
                            stepDelayMinSec = sMin,
                            stepDelayMaxSec = sMax,
                            enabled = taskEnabled
                        )
                        viewModel.saveTask(task, actions.toList())
                        navController.popBackStack()
                    }) {
                        Text("保存", color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 基础信息 ─────────────────────────────────────

            OutlinedTextField(
                value = taskName,
                onValueChange = { taskName = it },
                label = { Text("任务名称") },
                placeholder = { Text("如：钉钉上班打卡") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 触发时间
            Text("触发时间", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = triggerHour,
                    onValueChange = { if (it.length <= 2) triggerHour = it },
                    label = { Text("时 (0-23)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Text(":", style = MaterialTheme.typography.headlineMedium)
                OutlinedTextField(
                    value = triggerMinute,
                    onValueChange = { if (it.length <= 2) triggerMinute = it },
                    label = { Text("分 (0-59)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // 启动延迟
            Text("启动随机延迟（秒）", style = MaterialTheme.typography.labelLarge)
            DelayRangeRow(
                minValue = delayMin,
                maxValue = delayMax,
                onMinChange = { delayMin = it },
                onMaxChange = { delayMax = it }
            )

            // 步骤间隔
            Text("步骤间隔（秒）", style = MaterialTheme.typography.labelLarge)
            DelayRangeRow(
                minValue = stepDelayMin,
                maxValue = stepDelayMax,
                onMinChange = { stepDelayMin = it },
                onMaxChange = { stepDelayMax = it }
            )

            HorizontalDivider()

            // ── 步骤列表 ─────────────────────────────────────

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("点击步骤 (${actions.size})", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    editingActionIndex = -1
                    dialogAction = ActionEntity(
                        taskId = editingTask?.task?.id ?: 0,
                        stepIndex = actions.size,
                        type = ActionType.TAP,
                        x = 0, y = 0,
                        overrideDelayMinSec = null,
                        overrideDelayMaxSec = null
                    )
                    showActionDialog = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加步骤")
                }
            }

            if (actions.isEmpty()) {
                Text(
                    "暂无步骤，点击「添加步骤」",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                actions.forEachIndexed { index, action ->
                    ActionRow(
                        index = index,
                        action = action,
                        total = actions.size,
                        onEdit = {
                            editingActionIndex = index
                            dialogAction = action
                            showActionDialog = true
                        },
                        onDelete = { actions.removeAt(index) },
                        onMoveUp = {
                            if (index > 0) {
                                val tmp = actions[index - 1]
                                actions[index - 1] = actions[index]
                                actions[index] = tmp
                            }
                        },
                        onMoveDown = {
                            if (index < actions.lastIndex) {
                                val tmp = actions[index + 1]
                                actions[index + 1] = actions[index]
                                actions[index] = tmp
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(72.dp))  // FAB 安全区
        }
    }

    // ── ActionEditDialog ──────────────────────────────────
    if (showActionDialog && dialogAction != null) {
        ActionEditDialog(
            initial = dialogAction!!,
            screenWidth = screenW,
            screenHeight = screenH,
            onConfirm = { updated ->
                if (editingActionIndex == -1) {
                    actions.add(updated)
                } else {
                    actions[editingActionIndex] = updated
                }
                showActionDialog = false
            },
            onDismiss = { showActionDialog = false }
        )
    }
}

// ── 步骤行 ──────────────────────────────────────────────────

@Composable
private fun ActionRow(
    index: Int,
    action: ActionEntity,
    total: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 步骤序号
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.primary
            )
            // 步骤描述
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = when (action.type) {
                        ActionType.TAP -> "点击  (${action.x}, ${action.y})"
                        ActionType.BACK -> "返回"
                        ActionType.HOME -> "回到桌面"
                        ActionType.CLOSE_ALL -> "清除后台"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (action.overrideDelayMinSec != null) {
                    Text(
                        "延迟 ${action.overrideDelayMinSec}~${action.overrideDelayMaxSec}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 上移
            IconButton(onClick = onMoveUp, enabled = index > 0) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移",
                    tint = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.outlineVariant)
            }
            // 下移
            IconButton(onClick = onMoveDown, enabled = index < total - 1) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移",
                    tint = if (index < total - 1) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.outlineVariant)
            }
            // 编辑
            TextButton(onClick = onEdit) { Text("编辑") }
            // 删除
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除步骤",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ── ActionEditDialog ─────────────────────────────────────────

@Composable
private fun ActionEditDialog(
    initial: ActionEntity,
    screenWidth: Int,
    screenHeight: Int,
    onConfirm: (ActionEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember { mutableStateOf(initial.type) }
    var xInput by remember { mutableStateOf(initial.x.toString()) }
    var yInput by remember { mutableStateOf(initial.y.toString()) }
    var overrideEnabled by remember {
        mutableStateOf(initial.overrideDelayMinSec != null)
    }
    var overrideMin by remember {
        mutableStateOf(initial.overrideDelayMinSec?.toString() ?: "4")
    }
    var overrideMax by remember {
        mutableStateOf(initial.overrideDelayMaxSec?.toString() ?: "8")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑步骤") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 操作类型选择
                Text("操作类型", style = MaterialTheme.typography.labelLarge)
                ActionType.entries.forEach { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedType == type,
                            onClick = { selectedType = type }
                        )
                        Text(
                            text = when (type) {
                                ActionType.TAP -> "点击坐标"
                                ActionType.BACK -> "返回"
                                ActionType.HOME -> "回到桌面"
                                ActionType.CLOSE_ALL -> "清除后台"
                            },
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                // 坐标输入（仅 TAP）
                AnimatedVisibility(visible = selectedType == ActionType.TAP) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = xInput,
                                onValueChange = { xInput = it },
                                label = { Text("X (px)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = yInput,
                                onValueChange = { yInput = it },
                                label = { Text("Y (px)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            text = "屏幕分辨率：${screenWidth} × ${screenHeight}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 步骤延迟覆盖
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = overrideEnabled,
                        onCheckedChange = { overrideEnabled = it }
                    )
                    Text("覆盖此步骤的等待时间", modifier = Modifier.padding(start = 4.dp))
                }
                AnimatedVisibility(visible = overrideEnabled) {
                    Column {
                        Text("等待时间（秒）", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        DelayRangeRow(
                            minValue = overrideMin,
                            maxValue = overrideMax,
                            onMinChange = { overrideMin = it },
                            onMaxChange = { overrideMax = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val x = xInput.toIntOrNull() ?: initial.x
                val y = yInput.toIntOrNull() ?: initial.y
                val oMin = if (overrideEnabled) overrideMin.toIntOrNull()?.coerceAtLeast(0) else null
                val oMax = if (overrideEnabled) {
                    val m = overrideMax.toIntOrNull()?.coerceAtLeast(0) ?: 8
                    if (oMin != null) m.coerceAtLeast(oMin) else m
                } else null
                onConfirm(initial.copy(type = selectedType, x = x, y = y,
                    overrideDelayMinSec = oMin, overrideDelayMaxSec = oMax))
            }) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ── 延迟范围输入行（复用组件）──────────────────────────────

@Composable
private fun DelayRangeRow(
    minValue: String,
    maxValue: String,
    onMinChange: (String) -> Unit,
    onMaxChange: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = minValue,
            onValueChange = { if (it.length <= 4) onMinChange(it) },
            label = { Text("最小") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Text("~", style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = maxValue,
            onValueChange = { if (it.length <= 4) onMaxChange(it) },
            label = { Text("最大") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
}
