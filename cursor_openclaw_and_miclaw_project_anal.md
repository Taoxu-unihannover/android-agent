# Openclaw and miclaw project analysis
_Exported on 3/10/2026 at 22:49:26 GMT+8 from Cursor (2.5.20)_

---

**User**

1. 充分分析openclaw的框架和底层结构设计，输出详细的项目结构图，项目报告
2. 参考分析miclaw是怎么实现对手机的控制的，参考链接https://www.guancha.cn/economy/2026_03_06_809060.shtml，
https://zhuanlan.zhihu.com/p/2013274428377682147
3. 如果我需要再在安卓手机实现这些功能改怎么设计项目框架，如何在手机上实现类似miclaw的功能，实现对手机的智能控制
4. 如果两个人两周内需要通过vibe coding来实现，该具体怎么分工、怎么协作

---

**Cursor**

我会先确认工作区里 `openclaw` 的实际代码结构，再并行抓取你给的两篇 `miclaw` 相关文章内容；拿到这两部分上下文后，我会整理成一份中文项目报告，覆盖架构分析、安卓实现方案，以及两人两周的落地分工。

我已经确认工作区里确实有完整的 `openclaw` 仓库。下一步会把代码结构和 `phone-control` 相关扩展分开分析，同时补抓 `miclaw` 的公开资料，重点提炼“系统级工具 + 推理执行循环 + 本地上下文/记忆 + 权限确认”这几层。

我已经拿到 `openclaw` 的主框架结论，发现它本身就已经带有 `Android/iOS` 客户端、`node.invoke` 设备调用、配对鉴权和子智能体机制。现在会继续把关键源码和安卓文档读透，避免只停留在“概念类比”。

外部资料这块，第一篇新闻已经能确认 `miclaw` 的四层能力模型；第二篇知乎原文抓取超时，我会改用公开检索补齐交叉信息，并在报告里明确哪些结论来自公开报道、哪些是基于 `openclaw` 对照后的工程推断。

我已经能确认一件关键事：`openclaw` 不是单纯“聊天机器人”，而是“网关控制平面 + 插件生态 + 节点设备执行层 + 移动端 Operator/Node 双角色客户端”的平台。接下来我补一层 README/关键文档语义，把项目结构图和安卓方案写得更完整、可执行。

## 一、`openclaw` 框架与底层结构分析

### 1. 项目定位
`openclaw` 本质上不是“单个聊天机器人”，而是一个**以 Gateway 为控制平面**的 AI Agent 平台。它把这几类能力统一起来了：

- 多渠道消息入口：WhatsApp、Telegram、Slack、Discord、iMessage、WebChat 等
- Agent 运行时：会话、工具调用、流式响应、子智能体
- 设备节点：macOS、iOS、Android 作为可配对、可调用的执行节点
- 插件生态：渠道、工具、Provider、服务、CLI、Context Engine 都可扩展
- UI 与控制面：Web UI、Canvas/A2UI、移动端 operator/node 双角色

一句话总结：**OpenClaw 的核心设计是“统一控制平面 + 插件注册机制 + 节点执行层 + 多终端接入”。**

---

### 2. 项目结构图

```text
openclaw/
├── package.json                 # 项目总入口、脚本、导出、依赖
├── openclaw.mjs                # 生产 CLI 薄入口
├── src/                        # 核心 TypeScript 运行时
│   ├── entry.ts                # 进程启动入口
│   ├── cli/                    # CLI 命令面
│   ├── gateway/                # Gateway 服务、协议、方法分发
│   ├── agents/                 # Agent 工具、会话、子智能体、执行器
│   ├── plugins/                # 插件发现、装载、注册表、运行时
│   ├── channels/               # 渠道插件协议与适配层
│   ├── context-engine/         # 上下文引擎注册与切换
│   ├── memory/                 # 记忆与索引管理
│   ├── config/                 # 配置 schema、路径、合并逻辑
│   ├── infra/                  # 配对、出站投递、系统底层能力
│   ├── web/                    # WebChat / inbox / auto-reply
│   └── utils/                  # 公共工具
├── extensions/                 # 一等插件集合
│   ├── phone-control/
│   ├── device-pair/
│   ├── memory-core/
│   ├── memory-lancedb/
│   ├── slack/
│   ├── telegram/
│   ├── whatsapp/
│   └── ...                     # 30+ 扩展
├── apps/                       # 原生客户端
│   ├── android/
│   ├── ios/
│   ├── macos/
│   └── shared/OpenClawKit/     # iOS/macOS 共享能力
├── ui/                         # Control UI / Web 前端
├── docs/                       # 官方文档
├── test/                       # 测试基座
├── scripts/                    # 构建、发布、打包、协议生成
├── vendor/                     # A2UI 等 vendor 规格/依赖
└── skills/                     # skill 内容与相关测试
```

---

### 3. 核心架构图

```mermaid
flowchart TB
    U[用户输入<br/>聊天/语音/Web/UI] --> C[Channel Adapter]
    C --> G[Gateway Control Plane]
    G --> S[Session / Routing / Auth]
    S --> A[Agent Runtime]
    A --> T[Tool Registry]
    T --> P[Plugins]
    T --> N[Node Invoke]
    T --> M[Memory / Context Engine]
    N --> D[Android/iOS/macOS Node]
    P --> X[Channel / Provider / Service / CLI]
    A --> O[Streaming Response / Delivery]
    O --> C
```

---

### 4. 底层设计结论

#### 4.1 Gateway 是中枢，不是附属模块
`openclaw` 把网关当成整个系统的控制平面：

- 接收所有客户端、渠道、节点请求
- 统一做鉴权、会话管理、事件分发、工具调用
- 节点控制不是 Agent 直接碰设备，而是通过 `node.invoke` 走 Gateway

这意味着它天然适合做“多端统一调度”。

#### 4.2 插件机制是第一设计原则
插件 API 支持注册：

- `registerTool`
- `registerHook`
- `registerChannel`
- `registerGatewayMethod`
- `registerCli`
- `registerService`
- `registerProvider`
- `registerCommand`
- `registerContextEngine`

这说明它不是“功能堆叠”，而是**注册表驱动的平台内核**。

#### 4.3 渠道与设备节点都是 Adapter
OpenClaw 没把 Telegram、Slack、Android 写死在核心里，而是都做成“适配器”：

- 渠道适配器：负责消息收发、线程、状态、认证
- 节点适配器：负责能力声明、命令执行、结果回传

优点是扩展性强，缺点是层级较深、调试链路较长。

#### 4.4 Android 端已经具备“手机智能体雏形”
`apps/android` 不是单纯聊天壳，它已经有：

- operator 连接：聊天、历史、语音
- node 连接：设备能力暴露与执行
- 命令注册表：`camera`、`notifications`、`contacts`、`calendar`、`sms`、`location`、`motion`、`canvas`
- 前后台限制处理
- TLS 指纹信任与自动重连
- 前台服务维持连接

这说明 `openclaw` 已经部分回答了“手机怎么成为 Agent 身体”的问题。

#### 4.5 风险控制做得比一般 demo 深
`phone-control` 插件不是“实现控制手机”，而是**临时解锁高风险手机命令**，比如：

- `camera.snap`
- `camera.clip`
- `screen.record`
- `calendar.add`
- `contacts.add`
- `sms.send`

这类设计很像真实产品而不是 hack demo：**能力存在，但默认收敛，按需临时放开**。

---

### 5. OpenClaw 的短板
如果目标是“像 miclaw 一样直接深控安卓系统”，OpenClaw 还差几层：

- Android 端目前更像 companion node，不是完整本地 Agent Host
- 没看到成熟的 Android Accessibility UI 自动化链路
- 没看到完整的 FCM 唤醒/恢复执行闭环
- 没有系统级特权，因此无法达到 OEM 级权限深度
- 很多能力仍依赖 Gateway 主机，不是纯手机自治

所以它更像“移动端 Agent 平台骨架”，不是完整手机 OS Agent。

---

## 二、参考 `miclaw` 的手机控制实现思路

### 1. 基于公开资料可确认的 `miclaw` 能力层
你给的新闻和公开二手资料，能稳定提炼出这 4 层：

1. 系统底层能力  
把手机系统能力封装成 50+ 工具，交给大模型做推理-执行循环。

2. 个人上下文理解  
不是只看当前一句话，而是把短信、日历、天气、设备状态、历史习惯拼起来判断。

3. 生态互联  
能调米家 IoT，支持 MCP 和第三方 SDK，把外部能力翻译成模型可调用的工具。

4. 自进化  
支持子智能体、脚本、记忆、自定义工具接入。

### 2. 我对 `miclaw` 实现方式的工程推断
如果要真正做到报道里那种效果，底层大概率是这套结构：

```text
系统应用 / 高权限宿主
+ 本地事件总线
+ 工具注册中心
+ Planner-Executor 循环
+ 本地记忆与压缩
+ 风险分级确认
+ 生态服务桥接层
```

也就是：

- **系统应用身份**：这是关键。没有系统签名/ROM级合作，很多系统级能力做不到。
- **工具层**：把“读短信、查日历、设闹钟、查天气、打开乘车码”都做成标准工具。
- **推理执行循环**：LLM 不直接“操作系统”，而是不断决定“下一步调用哪个工具”。
- **上下文引擎**：把通知、时间、地点、设备、日程、家庭 IoT 状态整合成任务上下文。
- **三级记忆**：短期轮次记忆、中期任务摘要、长期用户偏好。
- **风险控制**：支付、转账、下单等强制用户确认。
- **开放接入**：MCP/SDK 让手机不只是会控制自己，也能控制周边生态。

### 3. 和 `openclaw` 的映射关系
`miclaw` 与 `openclaw` 在工程思想上其实很接近：

- `miclaw` 的系统工具层，对应 `openclaw` 的 `node.invoke + nodes tool`
- `miclaw` 的多层记忆，对应 `openclaw` 的 `session + memory + context-engine`
- `miclaw` 的生态接入，对应 `openclaw` 的 `plugins + MCP/SDK 风格扩展`
- `miclaw` 的自进化/子智能体，对应 `openclaw` 的 `subagents`
- `miclaw` 更强的地方，在于它更可能是 **OEM / system app** 路线

这也是你后面要做安卓版时最重要的判断点。

---

## 三、如果要在安卓实现类似 `miclaw`，建议怎么设计

### 1. 先判断目标边界
安卓上“像 miclaw”分三档：

#### A. 普通三方 App 可实现
能做：

- 通知读取与通知动作
- 日历、联系人、短信发送
- 语音输入输出
- 截图/录屏
- 相机拍摄
- App 启动、Intent 跳转
- 通过 Accessibility 做点击、滑动、输入
- 本地数据库记忆
- MCP / HTTP / WebSocket 外部工具接入

#### B. 企业设备 / Device Owner 可实现
在 A 的基础上可加强：

- 更稳定的后台保活
- 部分策略管理
- 更深的设备控制
- 更适合特定设备群

#### C. OEM / 系统应用 才能实现
这是接近 `miclaw` 的级别：

- 更深系统设置控制
- 更稳后台唤醒
- 更少权限弹窗摩擦
- 更强跨系统组件编排

**如果你不是 OEM 合作方，建议目标先定在 A 档。**

---

### 2. 推荐项目框架

```mermaid
flowchart TB
    UI[Android App UI<br/>Chat/Voice/Settings] --> ORCH[Agent Orchestrator]
    ORCH --> PLAN[Planner / Tool Selector]
    ORCH --> MEM[Memory Engine]
    ORCH --> POLICY[Permission & Risk Policy]
    PLAN --> TOOLBUS[Tool Bus]
    TOOLBUS --> SYS[System Tools]
    TOOLBUS --> AUTO[Accessibility Automation]
    TOOLBUS --> MEDIA[Camera / Screenshot / MediaProjection]
    TOOLBUS --> PIM[Contacts / Calendar / SMS / Notifications]
    TOOLBUS --> EXT[MCP / SDK / IoT Connectors]
    SYS --> RESULT[Execution Result / Observation]
    AUTO --> RESULT
    MEDIA --> RESULT
    PIM --> RESULT
    EXT --> RESULT
    RESULT --> ORCH
```

---

### 3. 建议的模块划分

#### 3.1 App 层
- `app-ui`: 聊天、语音、权限、设置、任务回放
- `agent-core`: Prompt、工具选择、计划执行、任务状态机
- `memory-core`: SQLite/Room + 向量索引 + 摘要压缩
- `tool-runtime`: 工具注册、schema、执行器、超时、重试
- `policy-engine`: 权限矩阵、风险等级、确认流
- `event-ingest`: 通知、短信、日历变化、位置、设备状态
- `automation`: AccessibilityService、截图、OCR、UI 观察
- `connectors`: MCP、IoT、第三方 SDK、Web API

#### 3.2 Android 关键系统组件
- `ForegroundService`：长连接、持续任务
- `AccessibilityService`：点击、滑动、输入、控件树
- `NotificationListenerService`：监听通知与回复动作
- `MediaProjection`：截图/录屏
- `BroadcastReceiver`：系统事件
- `WorkManager`：低实时后台任务
- `Room`：本地记忆与任务日志
- `CameraX`：拍照/录像
- `SpeechRecognizer + TTS`：语音交互

---

### 4. 我建议的“像 miclaw”能力优先级
两周内不要一开始追求“全系统接管”，应先做闭环最强的 5 个场景：

1. 通知理解并执行  
例：收到购票成功通知后，提取时间地点，写入日历，提前提醒。

2. 日程助理  
例：根据日历和天气，给出出发建议，并一键打开导航。

3. 通讯录 + 短信动作  
例：识别联系人，自动生成短信草稿，用户确认后发送。

4. 手机状态感知  
例：电量低、定位变化、会议中、耳机连接状态触发不同策略。

5. UI 自动化 MVP  
例：打开某 App，搜索目标，完成简单点击流。

---

### 5. 执行引擎怎么做
建议用 `Planner -> Executor -> Observer -> Replan` 循环：

```text
用户目标
-> 计划拆解
-> 选工具
-> 执行一步
-> 读取结果/截图/控件树
-> 判断是否完成
-> 未完成则继续下一步
```

这就是手机智能控制的核心，不是“大模型一句话直接搞定”。

---

### 6. 权限与安全设计
一定要从第一天就设计好：

- `L0` 只读：查通知、查日历、读状态
- `L1` 低风险写入：加提醒、写日历、生成草稿
- `L2` 中风险：发短信、回复通知、启动 App、Accessibility 点击
- `L3` 高风险：支付、转账、删除内容、系统设置变更

规则：

- `L0/L1` 可自动执行
- `L2` 默认二次确认
- `L3` 必须强确认且留审计日志
- 所有任务都要有回放记录和撤销思路

---

### 7. 结合 `openclaw` 的最佳路线
如果你要尽快做出来，不建议从零设计全部控制平面。建议：

- 复用 `openclaw` 的思路，而不是直接硬搬全仓
- 保留 `Gateway + session + node.invoke + subagent + tool schema` 这套骨架
- Android 端重做成本地执行层
- 短期先远程模型推理，手机负责感知和执行
- 中期再考虑端侧模型或混合推理

**两周 MVP 最现实的架构是：远程控制平面 + 安卓本地执行节点。**

---

## 四、两个人两周用 vibe coding，怎么分工和协作

### 1. 最合理的分工

#### 角色 A：Android Runtime / Device Control
负责：

- Android 项目骨架
- 权限流
- `ForegroundService`
- `AccessibilityService`
- `NotificationListenerService`
- `MediaProjection`
- `Contacts/Calendar/SMS/Camera`
- 工具执行器实现
- 本地事件采集

#### 角色 B：Agent / Orchestration / Backend
负责：

- 工具 schema 设计
- Planner/Executor 循环
- Prompt 与记忆压缩
- Gateway 或轻量控制平面
- MCP / SDK 接入
- 风险策略与确认流
- 会话、日志、任务回放
- Demo 场景编排

这个拆法最稳，因为两人边界清晰：**一个管“手机能做什么”，一个管“AI 何时怎么调用”。**

---

### 2. 两周里程碑

#### 第 1 周
1. 明确 5 个 demo 场景，不再扩需求。
2. 定义工具协议：`name / params / riskLevel / resultSchema / requiresForeground`。
3. Android 先跑通 4 类工具：通知、日历、短信、相机/截图。
4. 后端先跑通任务循环：输入目标 -> 选工具 -> 执行 -> 回传 -> 继续。
5. 第 5 天打通第一个闭环 demo。

#### 第 2 周
1. 接入 Accessibility MVP。
2. 加本地记忆与任务摘要。
3. 加二次确认与审计日志。
4. 打磨 3 个稳定演示流程。
5. 最后 2 天只做联调、回归、录屏、修 bug。

---

### 3. 协作方式
建议从第一天就固定这 5 份共享文档：

- `tool-contract.md`
- `permission-matrix.md`
- `demo-scenarios.md`
- `risk-policy.md`
- `integration-checklist.md`

每天协作节奏：

1. 上午先对齐接口，不先写代码。
2. 中午前各自让 AI 生成模块草稿。
3. 下午只合并“可运行可验证”的代码。
4. 晚上跑一次端到端 demo。
5. 第二天只基于昨天失败点继续。

---

### 4. vibe coding 的关键规则
否则两周内一定失控：

- 不要让 AI 同时发明架构和业务。
- 先锁接口，再让 AI 填实现。
- 每个工具必须有固定 JSON schema。
- 每个场景必须有“成功判定条件”。
- 不要超过 5 个核心场景。
- 第 1 周不做“自进化”“自动学会新工具”。
- 第 2 周也不要碰“本地大模型部署优化”，除非前面都稳了。

---

## 五、最后给你的结论

如果你的目标是“做一个安卓上的 miclaw 类产品”，最现实路线不是完全照抄 `openclaw`，也不是直接追 OEM 级系统权限，而是：

- **借鉴 `openclaw` 的控制平面、工具注册、节点调用、子智能体和安全边界设计**
- **借鉴 `miclaw` 的系统工具化、上下文理解、推理执行循环、风险确认、生态桥接思路**
- **在安卓上先做一个 A 档能力的 MVP：通知 + 日历 + 短信 + 相机/截图 + Accessibility 自动化**
- **两人两周只做 3 到 5 个高价值闭环，不做“大而全”**

如果你愿意，我下一步可以直接继续给你一份更落地的内容：

1. `安卓 miclaw MVP` 的完整目录结构  
2. 每个模块的 Kotlin/服务/权限清单  
3. 两周开发排期表  
4. 第一版工具协议 JSON 设计稿

---

**User**

愿意，继续，设计的方案要考虑cursor的多agent合作的模式

---

**Cursor**

我已经有足够上下文了，接下来会把方案收敛成一份可执行计划，重点补上你要求的 `Cursor 多 agent 协作模式`、`安卓项目目录设计`、`两人两周排期` 和 `MVP 边界`。

---

**User**

Implement the plan as specified, it is attached for your reference. Do NOT edit the plan file itself.

To-do's from the plan have already been created. Do not create them again. Mark them as in_progress as you work, starting with the first one. Don't stop until you have completed all the to-dos.

---

**Cursor**

我会按计划从第一个待办开始推进，先确认工作区当前可承载的新项目位置，再落地 `MVP 边界/场景/工具协议` 这些基础文件，随后搭 Android 骨架和多 agent 协作文档。

我准备直接落地一个新的 `android-agent` 骨架：先把 `docs/` 里的边界、场景、风险、Cursor 多 agent 流程固定下来，再补可运行的 Gradle/Android 模块和最小 Kotlin 编排代码，让这套方案不只是文档。

我先把“冻结范围”和“接口事实来源”落成文件，这样后面不管是人协作还是 Cursor 多 agent 并行，都不会在中途把协议和边界改乱。

范围、核心场景、工具契约和风险策略已经落成文件了。下一步我会把这些设计真正变成项目骨架，包括 Gradle 模块、共享 DTO、编排循环和 Android 宿主入口。

基础模块已经搭出来了。现在我在补 Android 宿主入口和系统服务骨架，把“文档里的模块边界”真正落成 `app + services + runtime composition`。

骨架已经成形：现在补一次最基本的自检，看看新建文件有没有明显的 IDE 诊断问题，并尽量做一轮轻量编译验证。

本地没有全局 `gradle`，我会借用现有仓库里的 `gradlew` 再试一次项目装载，这样能多验证一层而不改动任何计划文件。

已按方案把 `android-agent` 骨架落地出来，没有改动计划文件本身，所有待办也都标记完成了。

这次主要新增了三部分。第一部分是项目与文档骨架：`android-agent/README.md`、`docs/demo-scenarios.md`、`docs/tool-contract.md`、`docs/risk-policy.md`、`docs/android-runtime.md`、`docs/orchestration-loop.md`、`docs/multi-agent-workflow.md`、`docs/two-week-schedule.md`。这些文件把 MVP 边界、4 个 demo 场景、工具协议、风险分级、Cursor 多 agent 协作方式和两周节奏固定下来了。

第二部分是多模块工程骨架：我创建了 `settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`，并拆出了 `modules/shared-models`、`modules/tool-runtime`、`modules/policy-engine`、`modules/memory-core`、`modules/agent-core`、`modules/android-tools`、`modules/automation-core`、`modules/integration-bridge`。其中已经有最小可用的共享 DTO、工具注册/分发、风险策略和 `Plan -> Execute -> Observe -> Replan` 编排骨架，例如 `modules/agent-core/src/main/kotlin/ai/miclaw/mvp/agent/AgentOrchestrator.kt` 和 `modules/tool-runtime/src/main/kotlin/ai/miclaw/mvp/tools/ToolRuntime.kt`。

第三部分是 Android 宿主入口：新增了 `apps/android-app/build.gradle.kts`、`apps/android-app/src/main/AndroidManifest.xml`、`MainActivity`、`AgentForegroundService`、`MiclawNotificationListenerService`、`MiclawAccessibilityService`，以及 `MvpRuntime`。目前 `MvpRuntime` 里已经把第一批 12 个工具注册成统一定义，便于后面把 stub handler 替换成真实实现。

验证上，`ReadLints` 对新项目没有报错。我也尝试做 Gradle 装载检查，但当前环境缺少 `gradle`，借用现有 `gradlew` 后又因为没有 `JAVA_HOME` 和 `java` 无法继续，所以这次没法完成实际编译验证。

如果你继续推进，我下一步建议直接做第一条真实闭环：把 `notifications.list`、`calendar.add` 和审批流从 stub 接成可运行实现。

---

**User**

@chat-history/android-agent_code_handoff_for_exec_agent.md 参考这份建议文档，优化当前项目框架

---

**Cursor**

我先对照你这份 `@chat-history/android-agent_code_handoff_for_exec_agent.md`，找出它和当前 `android-agent` 骨架之间的结构差异，然后按高价值项优化项目框架，而不是机械照抄整份文档。

我已经对照完交接文档，差异点很明确：当前骨架最该补的是 `ToolDefinition/Observation` 模型厚度、`tool-runtime` 的执行上下文与前置校验、`recipe/approval` 最小骨架，以及把 `MvpRuntime` 从“纯 stub 列表”升级成可装配框架。接下来我会围绕这些点做结构优化，不会去发散做大而全。

我会把这次优化集中在“框架升级”而不是“硬塞更多功能”上：补厚工具/观察模型、加入 `recipe-core` 和 `approval-core`、增强 `tool-runtime` 的执行上下文与前置检查，再把 `MvpRuntime` 拆成可装配的 handlers。这样后面接真实 Android 能力时不会推倒重来。

我在改 `AgentOrchestrator`，会让它从“只看 goal 的字符串路由”升级成“recipe-first + observation-aware + approval-aware”的执行骨架，这样后续接真实 Android handler 才有意义。

我现在在把 `MvpRuntime` 从集中写死的 stub 列表，拆成“工具目录 + Android 端具体 handler + 运行时装配”。这一步完成后，项目结构会更接近交接文档里要求的可扩展框架。

我顺手把运行时上下文也补一层，让它能感知当前权限和服务状态，而不是一直拿固定的假上下文；这能直接提升后面审批和前置校验的价值。

框架层面的改造已经基本打完，我现在做一轮静态自检，重点看新模型改动有没有把现有文件接口打断，以及 Android 宿主那几处新增 handler / service 有没有明显诊断问题。

我按你那份交接文档，把 `android-agent` 从“纯 skeleton”往“可扩展框架”推进了一层，重点是补结构，不是盲目加功能。

这次最核心的变化有四块。第一，工具与任务模型变厚了：`modules/shared-models/src/main/kotlin/ai/miclaw/mvp/models/Contracts.kt` 现在补了 `ToolCategory`、`ToolPrecondition`、`ToolIdempotencyPolicy`、`PendingApproval`、workflow step 状态，以及带 `source/confidence/timestamp` 的 `Observation`。这让后面做 precondition、approval、recipe、review 都有稳定的数据基础。

第二，运行时框架不再只是裸注册表了。`modules/tool-runtime/src/main/kotlin/ai/miclaw/mvp/tools/ToolRuntime.kt` 新增了 `ToolExecutionContext`、`ToolInterceptor` 和前置条件校验，已经能在 dispatch 前处理前台要求、权限要求、服务启用要求和 connector ready 这类约束。`modules/agent-core/src/main/kotlin/ai/miclaw/mvp/agent/AgentOrchestrator.kt` 也升级成了 `observation-aware + recipe-first + approval-aware` 的骨架，不再只看 goal 字符串。

第三，我把你文档里提到的两个关键扩展位真的加进工程了：`modules/approval-core` 和 `modules/recipe-core`。前者有 `ApprovalCoordinator` 和 `InMemoryApprovalStore`，后者有 `Recipe`、`RecipeStep`、`RecipeRegistry`，并且已经在 `MvpRuntime` 里接上了最小 recipe，例如“打开 app 后检查页面”。

第四，`apps/android-app` 这边不再是单文件硬编码 stub。`apps/android-app/src/main/java/ai/miclaw/mvp/app/runtime/AndroidToolHandlers.kt` 里已经拆出真实的 handler 装配和工具目录，`device.status`、`app.launch`、`notifications.list`、`calendar.add`、`contacts.search`、`ui.inspect` 都有真实实现或真实数据通路；`MiclawNotificationListenerService` 会往 `NotificationEventStore` 写通知摘要，`MiclawAccessibilityService` 会把当前界面树写进 `AccessibilitySnapshotStore`，`ui.inspect` 已经能消费这条观察链路。

另外我同步更新了文档侧的契约说明，尤其是 `docs/tool-contract.md` 和 `README.md`，让当前代码结构和文档保持一致。

验证方面，`ReadLints` 对 `android-agent` 没有报 IDE 诊断错误。编译我这边还是没法跑，因为当前机器缺少 `JAVA_HOME/java`，之前已经确认过这点，所以这次仍然没做实际 Gradle 构建。

如果你要继续，我建议下一步直接做两件事里的一个：
1. 把 `MvpRuntime` 的默认执行上下文接进真正的权限/服务状态刷新，并加一个最小 demo 入口去跑 `open app -> ui.inspect`。
2. 继续把第二批工具里最关键的 `ui.act` 和 `sms.send` 接成真实实现。

---

**User**

git提交保存当前代码

---

**Cursor**


初始化 `android-agent` 的 git 仓库并提交当前代码。



提交已完成。

在 `android-agent` 目录下初始化了 git 仓库，并创建了首次提交：

- **提交 ID**：`508202b`
- **变更**：43 个文件，+2545 行
- **内容**：MVP 框架骨架、交接文档优化、6 个真实工具实现、approval/recipe 模块等

当前仓库的 `user.email` 和 `user.name` 为本地配置（`dev@android-agent.local` / `Android Agent Dev`）。如需改为你的身份，可执行：

```bash
cd /home/001-phoneagent/android-agent
git config user.email "your@email.com"
git config user.name "Your Name"
git commit --amend --reset-author --no-edit
```

