# Risk Policy

## 风险分层

### L0

只读能力，不改变系统状态。

示例：

- `notifications.list`
- `calendar.events`
- `contacts.search`
- `device.status`
- `ui.inspect`

策略：

- 可自动执行
- 允许批量读取
- 需要写日志

### L1

低风险写能力，影响可逆或可接受。

示例：

- `calendar.add`
- `app.launch`
- `screen.capture`

策略：

- 默认可自动执行
- 必须保留审计日志
- 若命中敏感上下文，可升级为确认

### L2

中风险写能力，可能对外发出信息或直接操作界面。

示例：

- `sms.send`
- `notifications.action`
- `camera.snap`
- `ui.act`

策略：

- 默认要求确认
- 每次只执行一步
- 审批超时后任务暂停

### L3

高风险能力，本期不接入。

示例：

- 支付
- 删除关键数据
- 修改核心系统设置
- 登录敏感账号流程

策略：

- 本期禁止注册

## 临时解锁机制

借鉴 `openclaw/extensions/phone-control/index.ts` 的思路，本项目不采用永久白名单，而是采用短时授权：

- 解锁对象是“某个风险能力组”
- 解锁有时限
- 到期自动恢复
- 每次解锁都要写入审计日志

建议分组：

- `capture`: `camera.snap`, `screen.capture`
- `messaging`: `sms.send`, `notifications.action`
- `automation`: `ui.act`

## 审批流

审批请求字段至少包括：

- `approvalId`
- `taskId`
- `toolName`
- `riskLevel`
- `summary`
- `expiresAt`

审批结果仅允许：

- `allow-once`
- `deny`
- `timeout`

## 回退策略

- 高风险工具被拒绝后，任务进入 `blocked`。
- 编排层可以给出替代建议，但不能绕过审批自动换工具继续写操作。
- 同一 `idempotencyKey` 不可重复触发同一高风险动作。
