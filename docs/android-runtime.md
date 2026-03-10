# Android Runtime Boundaries

## 模块职责

### `apps/android-app`

Android 宿主层，负责：

- `Application` 与 `Activity`
- `ForegroundService`
- `NotificationListenerService`
- `AccessibilityService`
- Manifest、权限声明、系统组件注册

### `modules/android-tools`

Android 能力的端口定义，负责：

- 通知、日历、联系人、短信、相机、应用启动等工具接口
- 平台 handler 所需的统一输入输出模型

### `modules/automation-core`

负责 UI 观察与 UI 动作的抽象：

- `ui.inspect` 结果结构
- `ui.act` 动作结构
- 自动化执行接口

### `modules/shared-models`

所有跨模块 DTO 的唯一来源：

- Tool request/result
- Task state
- Approval state
- Observation payload

## 系统服务职责

### ForegroundService

- 持有长时任务生命周期
- 负责前台通知
- 承载后续与编排层的常驻连接

### NotificationListenerService

- 监听通知事件
- 将事件转换成 `Observation` 或 `ToolResult`
- 不直接做智能决策

### AccessibilityService

- 提供控件树读取
- 提供点击、输入、滚动等动作
- 不直接决定操作流程

## 关键约束

- 所有系统服务只产出观测结果或执行工具，不拥有业务编排权。
- Android 宿主层不直接推理下一步动作。
- 高风险动作仍由编排层和审批流控制。
