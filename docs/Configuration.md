# 配置说明（ai-assistant-prototype）

本项目的配置分为两类：

- `app.yml`：服务端运行配置（Milvus/ES/LLM/ETL/数据目录等）
- `*.d/*.yml`：可热加载的 YAML（skills/rules/mcp）

## 1. 配置根目录（home）

配置根目录简称 `home`，目录结构约定如下：

```
<home>/
  app.yml
  skills.d/
    *.yml
  rules.system.d/
    *.yml
  rules.triggers.d/
    *.yml
  mcp.d/
    *.yml
  data/
    app.db
```

默认 `home`：`ai-assistant-prototype/config`。

### 1.1 桌面端如何指定 home

桌面端读取 JVM System Property：`assistant.home`。

示例：

```bash
cd ai-assistant-prototype
gradle :desktop:run -Dassistant.home=/abs/path/to/config
```

对应代码：[DesktopApp.java](file:///Users/charles/Documents/trae_projects/comercial/ai-assistant-prototype/desktop/src/main/java/local/ai/desktop/DesktopApp.java#L18-L23)

### 1.2 服务端如何指定 home

服务端支持启动参数：

- `--port=<port>`
- `--home=<path>`

示例：

```bash
cd ai-assistant-prototype
gradle :server:run --args='--port=18081 --home=/abs/path/to/config'
```

对应代码：[ServerMain.java](file:///Users/charles/Documents/trae_projects/comercial/ai-assistant-prototype/server/src/main/java/local/ai/server/ServerMain.java#L8-L19)

## 2. app.yml 说明

文件位置：`<home>/app.yml`。

示例（最小可用）：

```yaml
dataDir: "./data"

milvus:
  enabled: false
  host: "127.0.0.1"
  port: 19530
  collection: "rag_chunks"
  dim: 384

es:
  enabled: false
  url: ""
  username: ""
  password: ""

llm:
  baseUrl: ""
  apiKey: ""
  chatModel: ""
  embeddingModel: ""

etl:
  maxTextBytes: 2000000
  chunkMaxChars: 1200
```

配置加载逻辑见：[AppConfig.java](file:///Users/charles/Documents/trae_projects/comercial/ai-assistant-prototype/server/src/main/java/local/ai/server/config/AppConfig.java#L30-L66)

### 2.1 dataDir

- 用途：SQLite 与其他本地数据输出目录。
- 默认：`<home>/data`。
- 若你配置为相对路径（例如 `./data`），会以 `<home>` 为基准。

当前 SQLite 路径：`<dataDir>/app.db`（见 [ServerRuntime.java](file:///Users/charles/Documents/trae_projects/comercial/ai-assistant-prototype/server/src/main/java/local/ai/server/ServerRuntime.java#L32-L35)）。

### 2.2 Milvus

用途：向量索引写入。

- `milvus.enabled`: `true/false`，默认 `false`
- `milvus.host`/`milvus.port`: 连接地址
- `milvus.collection`: collection 名，默认 `rag_chunks`
- `milvus.dim`: 向量维度（必须与 embedding 输出一致）

当 `milvus.enabled: true` 时：

- 会在服务启动时尝试连接并确保 collection 存在（不存在则创建）
- 文件增量 ETL 会执行：按 `file_id` 删除旧 chunk，再 insert 新 chunk

实现见：[MilvusVectorSink.java](file:///Users/charles/Documents/trae_projects/comercial/ai-assistant-prototype/server/src/main/java/local/ai/server/index/MilvusVectorSink.java#L24-L99)

### 2.3 Elasticsearch（ES）

用途：文本检索（BM25）与过滤。

当前状态：仅支持读取与展示配置，**尚未实现 ES client 连接、写入/删除与召回融合**。

- `es.enabled`: `true/false`
- `es.url`: 例如 `http://127.0.0.1:9200`
- `es.username`/`es.password`: 账号密码（可选）

对应配置类：[EsConfig.java](file:///Users/charles/Documents/trae_projects/comercial/ai-assistant-prototype/server/src/main/java/local/ai/server/config/EsConfig.java)

### 2.4 LLM / vLLM（OpenAI-compatible）

用途：embedding（当前已接入）与 chat（当前未接入，chat 仍为占位）。

- `llm.baseUrl`: 例如 `http://your-gateway/v1`
- `llm.apiKey`: API Key
- `llm.embeddingModel`: embedding 模型名
- `llm.chatModel`: chat 模型名

当前 embedding 行为：

- 若 `baseUrl/apiKey/embeddingModel` 配齐，则调用远程 `/v1/embeddings`。
- 否则使用本地 `LocalEmbedding` 生成伪向量（仅用于打通流程，不代表效果）。

实现见：[EmbeddingService.java](file:///Users/charles/Documents/trae_projects/comercial/ai-assistant-prototype/server/src/main/java/local/ai/server/etl/EmbeddingService.java#L16-L35)、[LlmConfig.java](file:///Users/charles/Documents/trae_projects/comercial/ai-assistant-prototype/server/src/main/java/local/ai/server/config/LlmConfig.java#L26-L32)

### 2.5 ETL

- `etl.maxTextBytes`: 单文件最大读取字节数（超出会跳过或截断）
- `etl.chunkMaxChars`: 文本 chunk 的最大字符数（当前按段落切分后再按长度限制）

## 3. skills / rules / mcp 的 YAML 目录

这些目录位于 `<home>` 下，服务端启动时会加载一次，且支持 `POST /api/config/reload` 热加载。

- `skills.d/*.yml`: skills（工具定义）
- `rules.system.d/*.yml`: 系统提示词/约束类规则
- `rules.triggers.d/*.yml`: 触发式规则
- `mcp.d/*.yml`: MCP 外部 server 配置

加载逻辑见：[YamlConfigLoader.java](file:///Users/charles/Documents/trae_projects/comercial/ai-assistant-prototype/server/src/main/java/local/ai/server/config/YamlConfigLoader.java#L25-L30)

注意：`/api/config/reload` 只 reload 以上 YAML，不会 reload `app.yml`（重载 `app.yml` 需要重启服务）。

## 4. 监听目录配置在哪里

监听目录列表存储在 SQLite（`<dataDir>/app.db`）。服务启动时会从 DB 加载目录并开始监听：

- API 查看：`GET /api/directories`
- API 添加：`POST /api/directories`，body 示例：`{"path":"/abs/path/to/watch"}`

实现见：[HttpApi.java](file:///Users/charles/Documents/trae_projects/comercial/ai-assistant-prototype/server/src/main/java/local/ai/server/web/HttpApi.java#L139-L158)

## 5. 本地状态（Checkpoint/Savepoint 风格）

为了做到“下次打开可以继续”，当前原型把关键运行状态持久化到 SQLite（`<dataDir>/app.db`），思路类似 Flink 的 checkpoint：

- `jobs`：持久化 ETL 任务队列（upsert/delete），进程异常退出后也能恢复继续跑。
- `files(size,mtime)`：记录上次索引的文件元信息，用于跳过未变更文件（避免每次启动全量重建）。

当前行为：

- Watcher 产生事件后不会直接跑 ETL，而是写入 `jobs`。
- ETL worker 会循环 claim `pending` job 执行，成功标记 `done`，失败标记 `failed`（记录 error）。
- 启动时会把 `running` 状态的 job 复位为 `pending` 以便继续执行。

相关实现：

- DB 表与任务 claim： [SqliteStore.java](file:///Users/charles/Documents/trae_projects/comercial/ai-assistant-prototype/server/src/main/java/local/ai/server/db/SqliteStore.java)
- ETL worker loop： [EtlService.java](file:///Users/charles/Documents/trae_projects/comercial/ai-assistant-prototype/server/src/main/java/local/ai/server/etl/EtlService.java)
