# AutoToucher —— Android 自动点击调度 APP 开发计划

## 1. 项目概述

**项目名称**：AutoToucher  
**开发语言**：Kotlin  
**UI 框架**：Jetpack Compose  
**目标平台**：Android 8.0（API 26）~ Android 16（API 36）  
**典型用例**：每天定时自动完成预设任务（点击图标 → 进入功能页 → 执行操作 → 返回直到退出）

---

## 2. 编译环境

| 项目 | 版本 |
|------|------|
| 开发 IDE | VS Code（源码编写）+ Android Studio（编译/签名） |
| compileSdk | 36（Android 16 "Baklava"） |
| minSdk | 26（Android 8.0） |
| targetSdk | 36 |
| Build-Tools | 37.0.0（首选），备选 36.1.0 / 36.0.0 / 35.0.0 |
| NDK | 21.4.7075529（仅原生层需要时引用） |
| CMake | 3.22.1（仅 C/C++ 模块需要时引用） |
| JAVA | `$env:JAVA_HOME = "D:\Projects\Tools\Android\Android Studio\jbr"` |

> 本项目为纯 Kotlin/Compose 工程，**无需** NDK / CMake，保留备查。

---

## 3. 核心功能需求

### 3.1 定时任务调度

- 用户可添加多个**每日定时任务**，每个任务拥有独立的触发时间（时:分）。
- 每个任务支持设置**随机延迟范围** `[min, max]`（单位：秒，默认 `[0, 5]`），实际触发时间 = 预设时间 + 随机延迟，使点击行为更像人工操作。
- 任务在设备重启后自动恢复（需处理 `BOOT_COMPLETED` 广播）。

### 3.2 点击序列配置

- 每个任务包含一个**有序点击列表**，支持任意条目数。
- 每条点击项包含：
  - **坐标**：屏幕像素坐标 `(x, y)`（以左上角为原点）；
  - **操作类型**：`TAP`（单击）| `BACK`（返回）| `HOME`（回桌面）| `CLOSE_ALL`（清除所有后台）。
- 相邻两次点击之间等待**随机间隔**，范围由任务级别统一配置（默认 `[4, 8]` 秒，可按条目覆盖）。

> **典型操作序列示例（4 点击 + 2 返回）**：
>
> | 步骤 | 操作 | 坐标 / 动作 | 说明 |
> |------|------|-------------|------|
> | 1 | TAP | (x1, y1) | 点击桌面应用图标 |
> | 2 | TAP | (x2, y2) | 点击功能入口 |
> | 3 | TAP | (x3, y3) | 点击执行按钮 |
> | 4 | TAP | (x4, y4) | 点击确认完成 |
> | 5 | BACK | — | 返回主界面（等待动画） |
> | 6 | BACK | — | 返回桌面 |

### 3.3 退出 / 返回操作

- **BACK**：模拟返回键（或全面屏右滑手势），可连续执行多次；
- **HOME**：模拟 Home 键，直接返回桌面；
- **CLOSE_ALL**：调用无障碍手势或 AccessibilityService 全局动作关闭所有后台应用（`GLOBAL_ACTION_RECENTS` → 清除全部），节省电量。

---

## 4. 技术方案

### 4.1 跨应用点击实现

Android 禁止普通应用直接操作其他应用界面，唯一合法途径为：

**AccessibilityService（无障碍服务）**

- 使用 `AccessibilityService.dispatchGesture()` 注入点击手势（API 24+，满足 minSdk 26）；
- 使用 `AccessibilityService.performGlobalAction()` 执行返回 / Home / 最近任务等全局动作；
- 用户需在系统设置中手动开启该服务，APP 引导用户完成此授权。

### 4.2 后台保活与定时触发

| 系统版本 | 方案 |
|----------|------|
| Android 8–11 | `AlarmManager.setExactAndAllowWhileIdle()` + `ForegroundService` |
| Android 12+ | `AlarmManager.setExactAndAllowWhileIdle()` 需 `SCHEDULE_EXACT_ALARM` 权限；Android 13 起需运行时申请 |
| 设备重启 | 监听 `BOOT_COMPLETED` 广播，重新注册 AlarmManager 任务 |

- 任务触发后启动 **Foreground Service**，在通知栏显示"AutoToucher 执行中"，完成后自动停止，符合 Android 8+ 后台限制。

### 4.3 数据持久化

使用 **Room Database** 存储任务配置：
- `TaskEntity`：任务 ID、名称、触发时间、随机延迟范围、启用状态；
- `ActionEntity`：所属任务 ID、步骤序号、操作类型、坐标 x/y、步骤级延迟范围（可选覆盖）。

### 4.4 权限清单

```xml
<!-- 精确闹钟（Android 12+） -->
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<!-- Android 13+ 运行时申请 -->
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />

<!-- 前台服务 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<!-- 开机自启 -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- 通知（Android 13+） -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- 无障碍服务通过 service 声明，无需额外 uses-permission -->
```

---

## 5. 项目结构

```
auto_toucher/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/example/autotoucher/
│   │   │   ├── data/
│   │   │   │   ├── db/              # Room 数据库、DAO、Entity
│   │   │   │   └── repository/      # TaskRepository
│   │   │   ├── service/
│   │   │   │   ├── AutoAccessibilityService.kt   # 无障碍服务，执行手势注入
│   │   │   │   └── TaskExecutorService.kt        # 前台服务，编排点击序列
│   │   │   ├── scheduler/
│   │   │   │   ├── AlarmScheduler.kt             # 注册/取消 AlarmManager
│   │   │   │   └── BootReceiver.kt               # 开机重新注册
│   │   │   ├── ui/
│   │   │   │   ├── screen/
│   │   │   │   │   ├── TaskListScreen.kt         # 任务列表页
│   │   │   │   │   ├── TaskEditScreen.kt         # 任务编辑页（时间/延迟/步骤）
│   │   │   │   │   └── PermissionGuideScreen.kt  # 权限引导页（无障碍/精确闹钟）
│   │   │   │   ├── viewmodel/
│   │   │   │   │   └── TaskViewModel.kt
│   │   │   │   └── theme/                        # Compose 主题
│   │   │   └── MainActivity.kt
│   │   └── res/
│   │       └── xml/
│   │           └── accessibility_service_config.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── task.json                  # 原始需求记录
└── task.md                    # 本文档
```

---

## 6. 关键模块说明

### 6.1 AutoAccessibilityService

```kotlin
// 核心能力
dispatchGesture(
    GestureDescription.Builder()
        .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
        .build(),
    callback, null
)
performGlobalAction(GLOBAL_ACTION_BACK)   // 返回
performGlobalAction(GLOBAL_ACTION_HOME)   // 回桌面
performGlobalAction(GLOBAL_ACTION_RECENTS) // 最近任务（配合清除全部）
```

### 6.2 TaskExecutorService（执行编排）

伪代码流程：
```
收到 AlarmManager 广播
  → 随机延迟 [min, max] 秒
  → 启动 Foreground Service
  → 遍历 ActionList：
      for action in actions:
          if action.type == TAP:
              AccessibilityService.dispatchGesture(x, y)
          elif action.type == BACK:
              AccessibilityService.performGlobalAction(BACK)
          elif action.type == HOME:
              AccessibilityService.performGlobalAction(HOME)
          elif action.type == CLOSE_ALL:
              AccessibilityService.performGlobalAction(RECENTS)
              // 等待界面加载后点击"清除全部"
          等待随机间隔 [stepMin, stepMax] 秒
  → 停止 Foreground Service
```

### 6.3 AlarmScheduler

- 任务保存时立即计算下次触发的 `epochMillis`（今天或明天的指定时间），注册精确闹钟；
- 闹钟触发后，服务执行完毕重新注册下一天的闹钟，实现每日循环；
- Android 12+ 精确闹钟权限缺失时，降级为 `setAndAllowWhileIdle()` 并在 UI 提示用户授权。

---

## 7. UI 功能清单

| 页面 | 功能 |
|------|------|
| 权限引导页 | 检测并引导开启无障碍服务、精确闹钟权限（Android 12+）、通知权限（Android 13+）；未授权时任务无法启用 |
| 任务列表页 | 显示所有任务卡片（名称、触发时间、启用开关）；支持新增、删除、启用/禁用 |
| 任务编辑页 | 设置任务名称、触发时间（时间选择器）、全局随机延迟范围；管理有序动作列表（增删改排序） |
| 动作编辑弹窗 | 选择操作类型（TAP/BACK/HOME/CLOSE_ALL）；TAP 时输入坐标 (x, y)；可选覆盖本步骤的等待时间范围 |

---

## 8. 兼容性与注意事项

| 问题 | 处理方式 |
|------|----------|
| Android 8 后台服务限制 | 所有后台执行均通过 Foreground Service，启动时立即 `startForeground()` |
| Android 10 后台启动限制 | 任务由 AlarmManager 触发，属于系统豁免场景，可直接启动 Service |
| Android 12 精确闹钟权限 | 检测 `canScheduleExactAlarms()`，不足时引导用户到系统设置授权 |
| Android 13 通知权限 | 运行时申请 `POST_NOTIFICATIONS`，否则前台服务通知无法显示导致 crash |
| Android 14 前台服务类型 | `TaskExecutorService` 声明 `foregroundServiceType="specialUse"`，并在 manifest 说明用途 |
| 全面屏返回手势差异 | 优先使用 `GLOBAL_ACTION_BACK`（系统级，兼容所有手势导航模式），不依赖屏幕边缘坐标模拟 |
| 屏幕分辨率适配 | 坐标为绝对像素值，用户需按自己设备实际分辨率填写；UI 提示当前设备分辨率供参考 |

---

## 9. 开发阶段划分

| 阶段 | 内容 | 产出 |
|------|------|------|
| Phase 1 | 项目脚手架：Gradle 配置、Room、Compose 主题、基础导航 | 可编译空壳 APK |
| Phase 2 | 核心服务：AccessibilityService 手势注入、Foreground Service 执行编排 | 手动触发点击验证可行性 |
| Phase 3 | 定时调度：AlarmManager + BootReceiver + 权限检测引导 | 定时自动触发 |
| Phase 4 | UI 完善：任务列表、编辑页、动作弹窗、权限引导页 | 完整可用 APP |
| Phase 5 | 兼容性测试：Android 8 / 12 / 13 / 14 / 16 各版本回归；签名打包 | 发布 APK |
