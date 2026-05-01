# AutoToucher —— 详细文件结构与代码计划

## 完整文件树

```
auto_toucher/
├── settings.gradle.kts
├── build.gradle.kts                          # 根项目 Gradle
├── app/
│   ├── build.gradle.kts                      # 模块 Gradle（依赖声明）
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/
│       │   ├── xml/
│       │   │   └── accessibility_service_config.xml
│       │   └── values/
│       │       └── strings.xml
│       └── java/com/example/autotoucher/
│           ├── MainActivity.kt
│           ├── data/
│           │   ├── db/
│           │   │   ├── AppDatabase.kt
│           │   │   ├── TaskEntity.kt
│           │   │   ├── ActionEntity.kt
│           │   │   ├── TaskDao.kt
│           │   │   └── ActionDao.kt
│           │   ├── model/
│           │   │   ├── ActionType.kt
│           │   │   └── TaskWithActions.kt
│           │   └── repository/
│           │       └── TaskRepository.kt
│           ├── service/
│           │   ├── AutoAccessibilityService.kt
│           │   └── TaskExecutorService.kt
│           ├── scheduler/
│           │   ├── AlarmScheduler.kt
│           │   └── BootReceiver.kt
│           └── ui/
│               ├── theme/
│               │   ├── Color.kt
│               │   ├── Theme.kt
│               │   └── Type.kt
│               ├── screen/
│               │   ├── PermissionGuideScreen.kt
│               │   ├── TaskListScreen.kt
│               │   └── TaskEditScreen.kt
│               └── viewmodel/
│                   └── TaskViewModel.kt
```

---

## 一、构建配置

### `settings.gradle.kts`

```
rootProject.name = "AutoToucher"
include(":app")
```
插件管理使用 `pluginManagement {}` 声明 AGP 与 Kotlin Gradle Plugin 仓库。

---

### `build.gradle.kts`（根）

声明内容：
- AGP 版本：`8.x`（兼容 compileSdk 36）
- Kotlin 版本：`2.x`（支持 Compose 编译器扩展）
- 所有子模块共用的 `plugins {}` 块（不 apply）

---

### `app/build.gradle.kts`

| 配置项 | 值 |
|--------|----|
| `compileSdk` | 36 |
| `minSdk` | 26 |
| `targetSdk` | 36 |
| `buildToolsVersion` | `"37.0.0"` |
| `kotlinCompilerExtensionVersion` | 与 Compose BOM 配套 |

**依赖清单**：

```
// Compose BOM（统一版本）
implementation(platform("androidx.compose:compose-bom:2025.xx.xx"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.ui:ui-tooling-preview")
implementation("androidx.activity:activity-compose")
implementation("androidx.navigation:navigation-compose")

// Lifecycle / ViewModel
implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
implementation("androidx.lifecycle:lifecycle-runtime-ktx")

// Room
implementation("androidx.room:room-runtime")
implementation("androidx.room:room-ktx")
kapt("androidx.room:room-compiler")            // 或 ksp

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android")

// Core KTX
implementation("androidx.core:core-ktx")
```

---

## 二、AndroidManifest.xml

### 权限声明

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<!-- Android 12 精确闹钟 -->
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<!-- Android 13+ 精确闹钟（更强豁免） -->
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
```

### 组件声明

**MainActivity**：
```xml
<activity android:name=".MainActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

**AutoAccessibilityService**：
```xml
<service android:name=".service.AutoAccessibilityService"
    android:exported="true"
    android:label="@string/accessibility_service_label"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

**TaskExecutorService**（前台服务）：
```xml
<service android:name=".service.TaskExecutorService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="自动执行预设点击任务" />
</service>
```

**BootReceiver**：
```xml
<receiver android:name=".scheduler.BootReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

**AlarmReceiver**（触发执行的广播接收器，在 `scheduler/` 包内）：
```xml
<receiver android:name=".scheduler.AlarmReceiver"
    android:exported="false" />
```

---

## 三、资源文件

### `res/xml/accessibility_service_config.xml`

```xml
<accessibility-service
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault"
    android:canPerformGestures="true"
    android:description="@string/accessibility_service_description"
    android:notificationTimeout="100"
    android:packageNames="" />
```

> `canPerformGestures="true"` 是 `dispatchGesture()` 的前提条件。  
> `packageNames` 留空表示监听所有应用（执行手势时无需目标包名过滤）。

---

## 四、数据层

### `data/model/ActionType.kt`

```kotlin
enum class ActionType {
    TAP,        // 点击指定坐标
    BACK,       // 执行返回
    HOME,       // 回到桌面
    CLOSE_ALL   // 清除后台任务
}
```

---

### `data/db/TaskEntity.kt`

```kotlin
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,               // 任务名称，如"钉钉上班打卡"
    val triggerHour: Int,           // 触发小时（0-23）
    val triggerMinute: Int,         // 触发分钟（0-59）
    val delayMinSec: Int,           // 随机延迟最小秒数（默认 0）
    val delayMaxSec: Int,           // 随机延迟最大秒数（默认 5）
    val stepDelayMinSec: Int,       // 步骤间隔最小秒数（默认 4）
    val stepDelayMaxSec: Int,       // 步骤间隔最大秒数（默认 8）
    val enabled: Boolean = true     // 是否启用
)
```

---

### `data/db/ActionEntity.kt`

```kotlin
@Entity(
    tableName = "actions",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("taskId")]
)
data class ActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int,                // 所属任务 ID
    val stepIndex: Int,             // 步骤序号（0-based，决定执行顺序）
    val type: ActionType,           // 操作类型
    val x: Int = 0,                 // 像素坐标 X（TAP 时有效）
    val y: Int = 0,                 // 像素坐标 Y（TAP 时有效）
    val overrideDelayMinSec: Int?,  // 步骤级延迟覆盖最小值（null = 使用任务级）
    val overrideDelayMaxSec: Int?   // 步骤级延迟覆盖最大值（null = 使用任务级）
)
```

**Room TypeConverter**（在 `AppDatabase` 中注册）：`ActionType` ↔ `String`。

---

### `data/db/TaskDao.kt`

```kotlin
@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY triggerHour, triggerMinute")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): TaskEntity?

    @Query("SELECT * FROM tasks WHERE enabled = 1")
    suspend fun getEnabledTasks(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("UPDATE tasks SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Int, enabled: Boolean)
}
```

---

### `data/db/ActionDao.kt`

```kotlin
@Dao
interface ActionDao {
    @Query("SELECT * FROM actions WHERE taskId = :taskId ORDER BY stepIndex")
    fun getActionsForTask(taskId: Int): Flow<List<ActionEntity>>

    @Query("SELECT * FROM actions WHERE taskId = :taskId ORDER BY stepIndex")
    suspend fun getActionsForTaskSync(taskId: Int): List<ActionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActions(actions: List<ActionEntity>)

    @Query("DELETE FROM actions WHERE taskId = :taskId")
    suspend fun deleteActionsForTask(taskId: Int)
}
```

---

### `data/db/AppDatabase.kt`

```kotlin
@Database(
    entities = [TaskEntity::class, ActionEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun actionDao(): ActionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            // Double-checked locking 单例
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "autotoucher.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

// 内部类：ActionType <-> String 转换
class Converters {
    @TypeConverter fun fromActionType(type: ActionType): String = type.name
    @TypeConverter fun toActionType(value: String): ActionType = ActionType.valueOf(value)
}
```

---

### `data/model/TaskWithActions.kt`

```kotlin
data class TaskWithActions(
    val task: TaskEntity,
    val actions: List<ActionEntity>
)
```

---

### `data/repository/TaskRepository.kt`

```kotlin
class TaskRepository(private val db: AppDatabase) {

    // 观察所有任务（Flow，UI 订阅用）
    val allTasks: Flow<List<TaskEntity>> = db.taskDao().getAllTasks()

    // 获取带步骤的完整任务（执行时用）
    suspend fun getTaskWithActions(taskId: Int): TaskWithActions?

    // 保存任务 + 步骤（先删旧 actions 再插新，事务保证原子性）
    suspend fun saveTaskWithActions(task: TaskEntity, actions: List<ActionEntity>): Int
    // 返回值：任务 ID（新增时由 Room 生成）

    // 删除任务（级联删除 actions）
    suspend fun deleteTask(task: TaskEntity)

    // 切换启用状态
    suspend fun setTaskEnabled(taskId: Int, enabled: Boolean)

    // 获取所有启用的任务（BootReceiver 重新注册时用）
    suspend fun getEnabledTasks(): List<TaskEntity>
}
```

> Repository 通过 `Application` 级别的单例提供，ViewModel 通过构造注入获取。

---

## 五、服务层

### `service/AutoAccessibilityService.kt`

**职责**：作为全局手势注入入口，暴露单例引用给 `TaskExecutorService` 调用。

```kotlin
class AutoAccessibilityService : AccessibilityService() {

    // ---- 单例持有（进程内可见）----
    companion object {
        @Volatile
        var instance: AutoAccessibilityService? = null
            private set

        // 检查服务是否已激活
        fun isEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        instance = this
        // 配置：无需监听窗口事件，只做手势注入
        serviceInfo = serviceInfo.also {
            it.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            it.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            it.flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            it.notificationTimeout = 100
        }
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* 无需处理 */ }
    override fun onInterrupt() { /* 无需处理 */ }

    // ---- 公开 API ----

    /**
     * 注入单次点击手势
     * @param x 屏幕像素 X
     * @param y 屏幕像素 Y
     * @param callback 手势完成回调（在主线程回调）
     */
    fun tap(x: Float, y: Float, callback: GestureResultCallback)

    /**
     * 执行系统全局动作
     * 封装 performGlobalAction()，支持 BACK / HOME / RECENTS
     */
    fun globalAction(action: Int): Boolean

    /**
     * 异步点击并以挂起方式等待完成
     * 供协程调用，内部使用 suspendCoroutine 包装 GestureResultCallback
     */
    suspend fun tapSuspend(x: Float, y: Float)
}
```

---

### `service/TaskExecutorService.kt`

**职责**：前台服务，接收来自 `AlarmReceiver` 的 Intent，按顺序执行动作序列。

```kotlin
class TaskExecutorService : Service() {

    // 依赖
    private lateinit var repository: TaskRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "autotoucher_exec"

        fun buildIntent(context: Context, taskId: Int): Intent
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getIntExtra(EXTRA_TASK_ID, -1) ?: return START_NOT_STICKY
        startForeground(NOTIFICATION_ID, buildNotification())   // Android 8+ 要求
        serviceScope.launch {
            executeTask(taskId)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    // ---- 核心执行逻辑 ----

    private suspend fun executeTask(taskId: Int) {
        val taskWithActions = repository.getTaskWithActions(taskId) ?: return
        val task = taskWithActions.task

        // 1. 随机延迟（任务级别）
        val startDelay = Random.nextInt(task.delayMinSec, task.delayMaxSec + 1)
        delay(startDelay * 1000L)

        // 2. 逐步执行 actions
        val actions = taskWithActions.actions  // 已按 stepIndex 排序
        for ((index, action) in actions.withIndex()) {
            executeAction(action)

            // 步骤间等待（最后一步不等待）
            if (index < actions.lastIndex) {
                val minDelay = action.overrideDelayMinSec ?: task.stepDelayMinSec
                val maxDelay = action.overrideDelayMaxSec ?: task.stepDelayMaxSec
                val stepDelay = Random.nextInt(minDelay, maxDelay + 1)
                delay(stepDelay * 1000L)
            }
        }

        // 3. 重新注册下一天闹钟
        AlarmScheduler.schedule(applicationContext, task)
    }

    private suspend fun executeAction(action: ActionEntity) {
        val svc = AutoAccessibilityService.instance ?: return  // 服务未激活则跳过
        when (action.type) {
            ActionType.TAP      -> svc.tapSuspend(action.x.toFloat(), action.y.toFloat())
            ActionType.BACK     -> svc.globalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            ActionType.HOME     -> svc.globalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            ActionType.CLOSE_ALL -> executeCloseAll(svc)
        }
    }

    /**
     * CLOSE_ALL 实现：
     * 1. performGlobalAction(GLOBAL_ACTION_RECENTS) 打开最近任务
     * 2. delay 600ms 等待动画
     * 3. 通过无障碍节点树查找"清除全部"/"全部清除"按钮并点击
     *    （各厂商 ROM 按钮文字不同，使用正则兼容：包含"清除"或"clear"关键字）
     * 4. 若未找到节点，则执行 HOME 兜底
     */
    private suspend fun executeCloseAll(svc: AutoAccessibilityService)

    // ---- 通知构建 ----

    private fun buildNotification(): Notification {
        // 创建 NotificationChannel（Android 8+ 必须）
        // 显示文字："AutoToucher 执行中"
        // 点击通知跳转 MainActivity
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
```

---

## 六、调度层

### `scheduler/AlarmScheduler.kt`

```kotlin
object AlarmScheduler {

    private const val ACTION_EXECUTE = "com.example.autotoucher.ACTION_EXECUTE"

    /**
     * 为单个任务注册精确闹钟。
     * 计算规则：若今天的 [triggerHour:triggerMinute] 已过，则定为明天；否则定为今天。
     */
    fun schedule(context: Context, task: TaskEntity) {
        if (!task.enabled) return
        val triggerAt = calcNextTriggerMillis(task.triggerHour, task.triggerMinute)
        val pendingIntent = buildPendingIntent(context, task.id)
        val am = context.getSystemService(AlarmManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                // 降级：非精确闹钟（误差可能 >5 分钟）
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                // 同时通过 LiveData/BroadcastChannel 通知 UI 提示用户授权
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    /** 取消某任务的闹钟 */
    fun cancel(context: Context, taskId: Int)

    /** 取消所有任务闹钟（禁用全部时用） */
    fun cancelAll(context: Context, taskIds: List<Int>)

    private fun calcNextTriggerMillis(hour: Int, minute: Int): Long {
        // Calendar 计算今天目标时间的毫秒数
        // 若 <= System.currentTimeMillis()，加 24 小时
    }

    private fun buildPendingIntent(context: Context, taskId: Int): PendingIntent {
        // Intent 指向 AlarmReceiver，携带 taskId
        // flags = PendingIntent.FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
    }
}
```

---

### `scheduler/AlarmReceiver.kt`

```kotlin
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(TaskExecutorService.EXTRA_TASK_ID, -1)
        if (taskId == -1) return
        // 启动前台服务执行任务
        val serviceIntent = TaskExecutorService.buildIntent(context, taskId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
```

---

### `scheduler/BootReceiver.kt`

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // 在协程中读取所有启用任务并重新注册闹钟
        CoroutineScope(Dispatchers.IO).launch {
            val repo = TaskRepository(AppDatabase.getInstance(context))
            repo.getEnabledTasks().forEach { task ->
                AlarmScheduler.schedule(context, task)
            }
        }
    }
}
```

---

## 七、UI 层

### `ui/theme/`

**Color.kt**：定义 Material3 色彩方案（亮色/暗色）。  
**Type.kt**：定义字体排版规格。  
**Theme.kt**：组合 `MaterialTheme`，自动切换亮/暗色，应用于 `MainActivity`。

---

### `ui/viewmodel/TaskViewModel.kt`

```kotlin
class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TaskRepository(AppDatabase.getInstance(application))

    // 所有任务列表（UI 状态）
    val tasks: StateFlow<List<TaskEntity>> =
        repository.allTasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 当前编辑任务（null = 新建）
    private val _editingTask = MutableStateFlow<TaskWithActions?>(null)
    val editingTask: StateFlow<TaskWithActions?> = _editingTask.asStateFlow()

    // 权限状态
    val accessibilityEnabled: StateFlow<Boolean>    // 轮询 AutoAccessibilityService.isEnabled()
    val exactAlarmGranted: StateFlow<Boolean>       // 检测 AlarmManager.canScheduleExactAlarms()
    val notificationGranted: StateFlow<Boolean>     // Android 13+ 检测通知权限

    /** 加载任务进编辑态 */
    fun loadTaskForEdit(taskId: Int) {
        viewModelScope.launch {
            _editingTask.value = repository.getTaskWithActions(taskId)
        }
    }

    /** 新建任务 */
    fun newTask() { _editingTask.value = TaskWithActions(TaskEntity(...), emptyList()) }

    /** 保存任务（同时重新注册/取消闹钟） */
    fun saveTask(task: TaskEntity, actions: List<ActionEntity>) {
        viewModelScope.launch {
            val savedId = repository.saveTaskWithActions(task, actions)
            if (task.enabled) {
                AlarmScheduler.schedule(getApplication(), task.copy(id = savedId.toInt()))
            } else {
                AlarmScheduler.cancel(getApplication(), savedId.toInt())
            }
        }
    }

    /** 删除任务并取消闹钟 */
    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            AlarmScheduler.cancel(getApplication(), task.id)
            repository.deleteTask(task)
        }
    }

    /** 切换启用状态 */
    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch {
            val newEnabled = !task.enabled
            repository.setTaskEnabled(task.id, newEnabled)
            if (newEnabled) AlarmScheduler.schedule(getApplication(), task.copy(enabled = true))
            else AlarmScheduler.cancel(getApplication(), task.id)
        }
    }
}
```

---

### `ui/screen/PermissionGuideScreen.kt`

**触发时机**：`MainActivity` 在启动时检查权限，若有未授权项则导航至此页。

**UI 结构**（Compose）：
```
Column {
    标题："开启以下权限以使用 AutoToucher"

    PermissionItem(
        icon = 无障碍图标,
        title = "无障碍服务",
        desc = "用于注入点击手势",
        granted = accessibilityEnabled,
        onGrant = { 跳转系统无障碍设置页 }
    )

    PermissionItem(
        icon = 闹钟图标,
        title = "精确闹钟",
        desc = "确保任务准时触发（Android 12+）",
        granted = exactAlarmGranted,
        onGrant = { 跳转 ACTION_REQUEST_SCHEDULE_EXACT_ALARM }
    ) // 仅 API >= 31 时显示

    PermissionItem(
        icon = 通知图标,
        title = "通知权限",
        desc = "显示任务执行中通知（Android 13+）",
        granted = notificationGranted,
        onGrant = { requestPermission(POST_NOTIFICATIONS) }
    ) // 仅 API >= 33 时显示

    Button("已全部授权，进入应用") {
        // 导航至 TaskListScreen
    }
}
```

---

### `ui/screen/TaskListScreen.kt`

**UI 结构**：
```
Scaffold(
    topBar = { TopAppBar("AutoToucher") },
    floatingActionButton = { FAB("添加任务") { 导航到 TaskEditScreen(taskId = -1) } }
) {
    LazyColumn {
        items(tasks) { task ->
            TaskCard(
                task = task,
                onToggle = { viewModel.toggleTask(task) },
                onClick  = { 导航到 TaskEditScreen(taskId = task.id) },
                onDelete = { 弹确认 Dialog → viewModel.deleteTask(task) }
            )
        }
    }
}
```

**TaskCard 包含字段**：
- 任务名称（大字）
- 触发时间（格式 `HH:mm`）+ 随机延迟范围（小字）
- 步骤数量（如"6 个步骤"）
- Switch：启用/禁用（直接 toggleTask）
- 长按或右滑：删除

---

### `ui/screen/TaskEditScreen.kt`

**路由参数**：`taskId: Int`（-1 = 新建）

**UI 结构**：
```
Scaffold(
    topBar = { TopAppBar("编辑任务", 返回按钮, 保存按钮) }
) {
    Column {
        // ── 基础信息区 ──
        OutlinedTextField("任务名称")
        TimePickerRow("触发时间", hour, minute)
        DelayRangeRow("启动延迟（秒）", delayMin, delayMax)
        DelayRangeRow("步骤间隔（秒）", stepMin, stepMax)

        Divider()

        // ── 步骤列表区 ──
        Text("点击步骤")
        LazyColumn {
            itemsIndexed(actions) { index, action ->
                ActionRow(
                    index   = index,
                    action  = action,
                    onEdit  = { 弹出 ActionEditDialog },
                    onDelete = { actions.removeAt(index) },
                    onMoveUp / onMoveDown = { 互换 stepIndex }
                )
            }
        }
        Button("添加步骤") { 弹出 ActionEditDialog(action = null) }
    }
}
```

**ActionEditDialog**（AlertDialog）：
```
Column {
    // 操作类型选择（SegmentedButton 或 RadioGroup）
    ActionTypeSelector(selected = type, onSelect = { type = it })

    // 坐标输入（仅 TAP 时显示）
    AnimatedVisibility(visible = type == TAP) {
        Row {
            OutlinedTextField("X 坐标（px）", keyboardType = Number)
            OutlinedTextField("Y 坐标（px）", keyboardType = Number)
        }
        Text("当前设备分辨率：${screenWidth} × ${screenHeight}", style = caption)
    }

    // 步骤延迟覆盖（可选）
    CheckboxRow("覆盖此步骤的等待时间")
    AnimatedVisibility(visible = overrideEnabled) {
        DelayRangeRow("等待时间（秒）", overrideMin, overrideMax)
    }

    Row {
        TextButton("取消") { dismiss() }
        TextButton("确认") { onConfirm(action); dismiss() }
    }
}
```

---

### `MainActivity.kt`

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoToucherTheme {
                val navController = rememberNavController()
                val viewModel: TaskViewModel = viewModel()

                NavHost(navController, startDestination = "permissions") {
                    composable("permissions") {
                        PermissionGuideScreen(
                            viewModel = viewModel,
                            onAllGranted = { navController.navigate("tasks") {
                                popUpTo("permissions") { inclusive = true }
                            }}
                        )
                    }
                    composable("tasks") {
                        TaskListScreen(viewModel, navController)
                    }
                    composable("edit/{taskId}") { backStack ->
                        val taskId = backStack.arguments?.getString("taskId")?.toInt() ?: -1
                        TaskEditScreen(viewModel, taskId, navController)
                    }
                }
            }
        }
    }
}
```

**导航逻辑**：
- 启动时若所有必要权限已授权，直接跳过 `permissions` 页进入 `tasks`；
- `PermissionGuideScreen` 在 `onResume` 中重新检查权限状态（用户从设置页返回后刷新）。

---

## 八、关键依赖关系图

```
MainActivity
  └─ NavHost
       ├─ PermissionGuideScreen ──┐
       ├─ TaskListScreen          ├── TaskViewModel
       └─ TaskEditScreen ─────────┘
                                       │
                               TaskRepository
                                       │
                                  AppDatabase
                               (TaskDao / ActionDao)

AlarmReceiver ──► TaskExecutorService
                       │
               AutoAccessibilityService (单例引用)
                       │
               AccessibilityService API（系统）

BootReceiver ──► AlarmScheduler ──► AlarmManager（系统）
TaskViewModel ──► AlarmScheduler
```

---

## 九、各阶段交付清单

### Phase 1：项目脚手架 ✅
- [x] `settings.gradle.kts` / `build.gradle.kts` / `app/build.gradle.kts`
- [x] `AppDatabase` + `TaskEntity` + `ActionEntity` + DAO + `Converters`
- [x] `TaskRepository`（基础 CRUD）
- [x] `ui/theme/` 三件套
- [x] `MainActivity`（含 NavHost 空壳路由）
- [x] 验证：`./gradlew assembleDebug` 编译通过（在 Android Studio 中执行）

### Phase 2：核心服务
- [x] `res/xml/accessibility_service_config.xml`
- [x] `AutoAccessibilityService`（含 `tap`、`tapSuspend`、`globalAction`）
- [x] `TaskExecutorService`（含 `executeTask` 完整流程、通知创建）
- [x] `ActionType` 枚举
- [ ] 验证：手动 `adb shell am startservice` 触发，观察点击是否生效

### Phase 3：定时调度
- [x] `AlarmScheduler`（`schedule` / `cancel`）
- [x] `AlarmReceiver`
- [x] `BootReceiver`
- [x] `AndroidManifest.xml` 完整权限与组件声明
- [ ] 验证：设置 1 分钟后触发，观察自动执行

### Phase 4：完整 UI
- [x] `TaskViewModel`（含权限状态 Flow）
- [x] `PermissionGuideScreen`
- [x] `TaskListScreen` + `TaskCard`
- [x] `TaskEditScreen` + `ActionEditDialog`
- [ ] 验证：完整流程端到端测试（新建任务 → 添加步骤 → 启用 → 等待自动触发）

### Phase 5：兼容性与发布
- [ ] Android 8（API 26）：后台服务限制回归
- [ ] Android 12（API 31）：精确闹钟权限引导回归
- [ ] Android 13（API 33）：通知权限回归
- [ ] Android 14（API 34）：`foregroundServiceType` 声明回归
- [ ] Android 16（API 36）：完整功能回归
- [x] Release 签名配置（`signingConfigs` in `app/build.gradle.kts`）
- [ ] 生成 `release.apk` 并在目标设备安装验证
