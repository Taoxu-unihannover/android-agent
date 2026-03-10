# Tool Contract

## 目的

本文件是整个项目的工具接口事实来源。所有 Cursor agent、人工开发者和测试脚本都必须围绕本文件定义的结构工作。

任何跨模块变更，先改本文件，再改实现。

## 通用模型

### ToolDefinition

```json
{
  "name": "calendar.add",
  "description": "Create a calendar event on device",
  "category": "API",
  "riskLevel": "L1",
  "capabilityGroup": "calendar",
  "requiresForeground": false,
  "requiresConfirmation": false,
  "cooldownMs": 0,
  "timeoutMs": 15000,
  "preconditions": [],
  "inputSchemaHint": "{}",
  "outputSchemaHint": "{}",
  "idempotencyPolicy": "PER_REQUEST"
}
```

### ToolRequest

```json
{
  "requestId": "uuid",
  "idempotencyKey": "uuid",
  "toolName": "calendar.add",
  "args": {},
  "sessionId": "main",
  "taskId": "task-123",
  "requestedAt": "2026-03-09T12:00:00Z"
}
```

### ToolResult

```json
{
  "requestId": "uuid",
  "toolName": "calendar.add",
  "status": "success",
  "message": "event created",
  "payload": {},
  "observations": [],
  "requiresUserAction": false,
  "retryable": false
}
```

## 风险等级

- `L0`: 只读
- `L1`: 低风险写
- `L2`: 中风险写
- `L3`: 高风险，本期不实现

## 第一批工具

| Tool | Purpose | Risk | Foreground | Confirmation |
| --- | --- | --- | --- | --- |
| `notifications.list` | 拉取通知列表 | `L0` | no | no |
| `notifications.action` | 打开、忽略、回复通知 | `L2` | no | yes |
| `calendar.events` | 查询日历 | `L0` | no | no |
| `calendar.add` | 新增日程 | `L1` | no | no |
| `contacts.search` | 搜索联系人 | `L0` | no | no |
| `sms.send` | 发送短信 | `L2` | no | yes |
| `device.status` | 查询设备状态 | `L0` | no | no |
| `camera.snap` | 拍照 | `L2` | yes | yes |
| `screen.capture` | 截图 | `L1` | yes | yes |
| `app.launch` | 启动指定 App | `L1` | no | no |
| `ui.inspect` | 读取当前界面结构 | `L0` | yes | no |
| `ui.act` | 点击、输入、滑动 | `L2` | yes | yes |

## 新增结构字段

- `category`: `SYSTEM` / `API` / `UI` / `MEDIA` / `COMMUNICATION`
- `capabilityGroup`: 归类能力组，便于风控和统计
- `timeoutMs`: 运行时默认超时提示
- `preconditions`: 例如前台要求、权限要求、服务启用要求
- `inputSchemaHint` / `outputSchemaHint`: 给 planner、review agent 和测试使用的轻量 schema hint
- `idempotencyPolicy`: `PER_REQUEST` / `PER_TASK` / `ONCE_UNTIL_SUCCESS`

## 请求约束

- 每个工具请求都必须带 `requestId` 与 `idempotencyKey`。
- 单个任务中，高风险工具一次只允许执行一步。
- 如果执行结果不确定，必须先返回 `observations`，由编排层决定是否重试。
- 不允许工具内部隐式再次调用别的工具，链式调用必须回到编排层做。

## 结果约束

- `status` 只能是 `success`、`blocked`、`failed`。
- `payload` 必须可序列化。
- `observations` 用于保存截图摘要、控件树摘要、系统回执等。
- `requiresUserAction=true` 时，编排层必须暂停任务并等待审批。

## Cursor 多 agent 规则

- `AndroidRuntimeAgent` 只实现 Android 端 handler，不改 schema。
- `OrchestrationAgent` 只消费 schema 并实现编排逻辑，不私自扩字段。
- `TestAgent` 依据本文件生成 mock request/result。
- `ReviewAgent` 以本文件为准审查接口漂移。
