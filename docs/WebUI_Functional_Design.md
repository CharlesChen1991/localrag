# Web UI 功能设计（原型版）

本设计用于指导当前原型的 Web UI 迭代，目标是让“目录索引 + RAG 对话 + Agent 管理 + 配置管理”形成闭环。

## 1. 设计原则

- 单机单用户优先：不做复杂登录权限，默认仅本地回环访问。
- 数据可追溯：所有对话、检索证据（citations）、索引任务都可在 UI 查看。
- 配置可理解：把“连接信息/模型选择/目录/skills/rules/mcp”可视化，不要求用户读代码。
- 原型先通流程：先把页面与接口形态固定，再逐步把占位能力替换为真实实现。

## 2. 信息架构（导航）

建议左侧侧边栏（或顶部 Tab）主导航：

- `Agent 对话`
- `Agent 管理`
  - `Agent 列表`
  - `新增/编辑 Agent`
- `RAG 资源`
  - `目录与索引`
  - `检索调试（RAG 查看）`
- `Skills / Rules / MCP`
  - `Skills`
  - `System Rules`
  - `Trigger Rules`
  - `MCP Servers`
- `设置`

右上角常驻：

- `服务状态`（端口、home/dataDir、Milvus/ES/LLM 连接健康）
- `配置重载`（reload YAML）

## 3. 页面设计

### 3.1 Agent 对话页

用途：用户与选定 Agent 对话，支持引用与会话历史。

布局：

- 左栏：会话列表
  - 搜索框（按标题/内容）
  - 会话项（标题、更新时间）
  - `新建会话` 按钮
- 中间：对话消息流
  - 消息气泡（user/assistant）
  - assistant 消息下方：`引用(citations)` 折叠面板
- 右栏：对话参数（与 Agent 绑定）
  - 召回开关：Milvus/ES
  - topK、融合策略（RRF/加权）
  - 过滤条件（目录、文件类型、时间）
  - 模型选择（chat/embedding/rerank）

核心交互：

- 发送消息：调用 `/api/chat`（原型已存在）
- 选择会话：调用 `/api/chat/sessions/:id`
- 新建会话：前端生成新 sessionId 或由后端返回

首期验收标准（原型）：

- 能新建会话、查看历史、发送消息
- 能展示 citations（即使占位）

### 3.2 Agent 列表页

用途：管理多个 Agent（不同系统规则、不同技能集合、不同模型与 RAG 参数）。

数据结构建议：

- `agent_id`
- `name`
- `description`
- `model_profile`：chat/embedding/rerank/vision/audio
- `rag_profile`：topK、融合策略、过滤默认值
- `skills_ref`：选择启用哪些 skills
- `rules_ref`：选择启用哪些 system/trigger rules

页面功能：

- 列表展示：名称、模型、启用的 skills 数量、最后修改时间
- 操作：`创建`/`编辑`/`复制`/`删除`
- 快捷入口：进入 `Agent 对话页` 并自动选中该 Agent

### 3.3 新增/编辑 Agent 页

用途：以表单方式创建/配置 Agent。

建议分区：

1) 基本信息：name/description
2) 模型：
   - chatModel
   - embeddingModel
   - rerankModel（可选）
   - vision/audio（可选）
3) RAG：
   - Milvus 开关
   - ES 开关
   - topK
   - 融合策略（RRF/加权）
   - 默认过滤（目录、类型）
4) Skills：从 `skills.d` 加载的 skills 列表中勾选
5) Rules：
   - system rules 多选
   - trigger rules 多选

首期可以先把 Agent 存储到 SQLite（`agents` 表）并在 chat 调用时带上 agent 配置。

### 3.4 目录与索引页（RAG 资源）

用途：管理监听目录、查看索引状态、触发重建。

布局：

- 目录列表
  - 展示：路径、状态（监听中/失败）、最近一次变更时间、文件数（可选）
  - 操作：新增目录、移除目录、立即全量扫描、暂停/恢复监听
- 索引状态
  - 队列长度、最近任务、失败任务
  - Milvus/ES 写入统计

与当前原型对齐：

- 目录新增：`POST /api/directories`
- 目录列表：`GET /api/directories`

后续需要补的接口（设计预留）：

- `POST /api/index/rebuild`（按目录/按文件）
- `GET /api/index/jobs`（任务列表）
- `POST /api/index/pause` / `resume`

### 3.5 检索调试（RAG 查看）页

用途：可视化一次 RAG 检索过程，便于调参与排查。

主要能力：

- 输入 query，选择 agent/profile
- 展示：
  - Milvus 召回结果列表（score、chunk_id、path、片段预览）
  - ES 召回结果列表（score、chunk_id、path、片段预览）
  - 融合后结果（RRF 分数、最终排序）
  - 最终拼接的 prompt（可折叠）
- 一键把某个结果复制到对话上下文（用于人工验证）

后续接口预留：

- `POST /api/rag/debug` 返回上述结构化数据

### 3.6 Skills / Rules / MCP 页

用途：让用户知道“现在系统加载了什么”，并提供基本的热加载能力。

#### Skills 页

- 列表展示：name/description/输入 schema
- 支持查看 YAML 原文（只读）

接口：`GET /api/skills`

#### System Rules 页

- 列表展示：规则名/适用范围/内容
- 支持查看 YAML 原文（只读）

接口：`GET /api/rules/system`

#### Trigger Rules 页

- 展示 if/then 条件与动作
- 支持查看 YAML 原文（只读）

接口：`GET /api/rules/triggers`

#### MCP Servers 页

- 展示 `mcp.d` 配置项
- 展示已注册的 tools（当前原型：`GET /api/mcp/tools`）
- 支持测试调用（当前原型：`POST /api/mcp/call`）

### 3.7 设置页

用途：集中展示服务信息与连接配置。

分区：

- 基础信息：homeDir/dataDir/port/版本
- Milvus：enabled/host/port/collection/dim + 健康检查
- ES：enabled/url/user + 健康检查（现阶段只展示）
- LLM：baseUrl/chatModel/embeddingModel + 连接测试
- ETL：maxTextBytes/chunkMaxChars
- 操作：`重载 YAML`、`导出诊断信息`（日志路径、DB 路径）

接口：

- `GET /api/config`
- `POST /api/config/reload`

注意：`app.yml` 的修改需要重启服务才生效（原型当前如此）。

## 4. Desktop 形态建议（解决“桌面形态有点问题”）

当前桌面端使用 JavaFX `WebView` 内嵌页面，常见问题：

- WebView 兼容性/字体渲染问题
- 受限于 WebKit 版本导致部分前端能力不可用
- 对开发调试不友好

建议下一版桌面形态：

- 桌面端只做 `托盘 + 服务生命周期管理`：启动/停止/重启服务、打开配置目录、打开日志目录。
- Web UI 默认用系统浏览器打开（更稳定），桌面端仅提供“打开 Web UI”按钮。
- 保留一个开关：`useEmbeddedWebView`（仅在需要时启用内嵌）。

## 5. 与当前原型的差距清单

- ES 写入/召回融合：未实现
- Milvus 检索 + 融合 + 引用：未实现（chat 仍为 SQLite LIKE 占位）
- Agent 管理：未实现（仅单一 chat 入口）
- RAG Debug：未实现
- Skills/Rules 仅展示 YAML：未做到真正参与对话编排
- MCP 目前为“HTTP 占位框架”：需替换为标准 MCP 协议栈

