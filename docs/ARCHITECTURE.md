# 系统架构与技术文档 (System Architecture & Technical Documentation)

## 1. 项目概述 (Project Overview)

本项目是一个本地优先的 AI 助手原型，专注于 **多模态 RAG (Retrieval-Augmented Generation)** 和 **智能 Agent** 能力。它能够在本地运行，对用户指定目录下的文件（文本、图片、视频）进行索引，并通过自然语言对话接口提供基于知识库的问答服务。

## 2. 技术栈 (Tech Stack)

### 后端 (Backend)
- **语言**: Java 11+
- **框架**: SparkJava (轻量级 HTTP Server)
- **构建工具**: Gradle
- **核心库**:
  - `milvus-sdk-java`: 向量数据库客户端
  - `okhttp`: HTTP 客户端 (用于 LLM API 调用)
  - `jackson`: JSON 处理
  - `sqlite-jdbc`: 元数据存储
  - `ffmpeg`: 视频处理 (通过 `net.bramp.ffmpeg` 封装调用)

### 前端 (Frontend)
- **框架**: React 18 + TypeScript
- **构建工具**: Vite
- **UI 库**: Tailwind CSS + Lucide React
- **路由**: React Router

### 数据存储 (Data Stores)
- **Metadata**: SQLite (存储文件元数据、Agent 配置、任务队列)
- **Vector Store**: Milvus (存储文本/图片/视频描述的向量 Embedding)
- **Search Engine**: Elasticsearch (存储文本分块，提供 BM25 关键词检索)

### AI 能力 (AI Capabilities)
- **LLM Provider**: 兼容 OpenAI 接口的模型服务 (默认配置为阿里云 DashScope Qwen-Plus / Qwen-VL)
- **Embedding**: 文本向量化 (如 text-embedding-v3 或同类模型)
- **Vision**: 多模态理解 (Qwen-VL) 用于图片和视频内容解析

---

## 3. 核心架构 (Core Architecture)

### 3.1 数据处理流水线 (ETL Pipeline)

系统通过 `MultiDirectoryWatcher` 监听指定目录的文件变化，触发 ETL 任务：

1.  **文件监听**: 实时捕获 `ENTRY_CREATE`, `ENTRY_MODIFY`, `ENTRY_DELETE` 事件。
2.  **任务队列**: 变更事件被写入 SQLite 的任务队列，确保不丢失。
3.  **Worker 处理**: 后台线程池消费任务队列。
    *   **文本处理**: 读取文件内容 -> 按段落/长度分块 (Chunking)。
    *   **图片处理**: 识别图片 -> 调用 Vision LLM 生成描述 -> 将描述作为文本分块。
    *   **视频处理**:
        1.  调用 `ffprobe` 获取元数据 (时长/分辨率)。
        2.  调用 `ffmpeg` 按策略抽帧 (如每 5 秒一帧)。
        3.  将关键帧组合，调用 Vision LLM (如 Qwen-VL) 生成详细视频内容描述。
        4.  将描述作为文本分块。
4.  **索引存储**:
    *   生成 Embedding 向量 -> 存入 Milvus。
    *   存储原始文本 -> 存入 Elasticsearch。
    *   更新 SQLite 文件元数据状态。

### 3.2 RAG 检索流程 (Retrieval Process)

当用户发起对话时：

1.  **Query Embedding**: 将用户问题转换为向量。
2.  **混合检索 (Hybrid Search)**:
    *   **向量检索**: 在 Milvus 中检索 Top-K 相似分块。
    *   **关键词检索**: (可选) 在 Elasticsearch 中进行 BM25 检索。
    *   **兜底策略**: 若向量库不可用，回退到 SQLite 的 `LIKE` 模糊查询。
3.  **上下文构建**: 将检索到的分块内容（文本、图片描述、视频描述）拼接为 Prompt 上下文。
4.  **LLM 生成**: 将 System Prompt + Context + User Query 发送给 LLM 生成回答。

### 3.3 Agent 运行时 (Agent Runtime)

采用 **ReAct (Reasoning + Acting)** 模式：

1.  **思考 (Thought)**: 模型根据用户输入和当前上下文，决定是否需要调用工具。
2.  **行动 (Action)**:
    *   `rag_search`: 调用 RAG 检索内部知识库。
    *   `weather_get`: 调用外部 API (如 wttr.in) 获取信息。
    *   其他自定义 Skills。
3.  **观察 (Observation)**: 执行工具并获取结果，反馈给模型。
4.  **最终回答 (Final Answer)**: 模型综合所有信息生成最终回复。

---

## 4. 关键特性实现细节

### 视频 RAG (Video RAG)
- **实现类**: `TextExtractors.java`
- **逻辑**:
  - 依赖本地安装的 `ffmpeg` 和 `ffprobe`。
  - 抽帧策略：`fps=1/5` (每 5 秒一帧)，上限 10 帧。
  - 多模态交互：构建包含 `image_url` (Base64) 的消息体，请求支持 Vision 的模型 (Qwen-VL-Max)。
  - 结果：生成的视频描述包含场景、人物、动作等细节，使视频内容可被文本检索。

### 技能扩展 (Skill System)
- **配置**: `config/skills.d/*.yml`
- **定义**: 包含 `name`, `description`, `input_schema`, `executor` (HTTP/Script)。
- **动态加载**: `YamlConfigLoader` 实时加载配置，无需重启即可生效。

---

## 5. 目录结构 (Directory Structure)

```
ai-assistant-prototype/
├── server/                 # 后端 Java 项目
│   ├── src/main/java/      # 源代码
│   │   ├── local/ai/server/
│   │   │   ├── config/     # 配置加载
│   │   │   ├── db/         # SQLite 操作
│   │   │   ├── etl/        # ETL 核心 (TextExtractors, EtlService)
│   │   │   ├── index/      # 索引服务 (Milvus, ES)
│   │   │   ├── web/        # HTTP API 路由
│   │   │   └── util/       # 工具类 (LLM Client, FFmpeg 封装)
│   └── build.gradle        # 后端构建配置
├── web-react/              # 前端 React 项目
│   ├── src/                # 前端源码
│   └── vite.config.ts      # Vite 配置
├── config/                 # 运行时配置文件
│   ├── app.yml             # 主配置 (API Key, DB 连接)
│   ├── skills.d/           # 技能定义
│   └── ...
└── docs/                   # 文档
```
