# AI Assistant Prototype (Java + React + Local RAG)

这是一个基于 Java (SparkJava) 后端和 React 前端的本地 AI 助手原型，集成了多模态 RAG (Retrieval-Augmented Generation) 能力，支持文本、图片和视频内容的检索与问答。

## ✨ 核心特性

- **多模态 RAG**:
  - **文本**: 支持 Markdown, PDF, TXT 等格式的解析与分块。
  - **图片**: 利用 Vision LLM (如 Qwen-VL) 生成图片描述并建立索引。
  - **视频**: 利用 FFmpeg 自动抽帧，结合 Vision LLM 生成详细的视频内容描述，支持基于内容的视频检索。
- **智能 Agent**:
  - 基于 ReAct (Reasoning + Acting) 模式。
  - 支持自定义 Skills (工具调用) 和 Rules (系统提示词)。
  - 具备思考过程展示 (Thinking Process) 和流式响应。
- **本地优先架构**:
  - 元数据存储于 SQLite。
  - 向量存储支持 Milvus。
  - 关键词检索支持 Elasticsearch (BM25)。

详细技术架构请参考：[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

---

## 🚀 快速启动

### 1. 环境准备

确保本地已安装以下工具：

- **Java**: JDK 11 或更高版本。
- **Node.js**: v18+ (用于前端构建)。
- **FFmpeg**: 用于视频处理 (`ffmpeg` 和 `ffprobe` 需在 PATH 中或指定路径)。
- **Docker** (推荐): 用于启动 Milvus 和 Elasticsearch。

### 2. 启动依赖服务

使用 Docker Compose 启动向量数据库 (Milvus) 和搜索引擎 (Elasticsearch)：

```bash
# 启动 Milvus (参考官方文档)
wget https://github.com/milvus-io/milvus/releases/download/v2.3.7/milvus-standalone-docker-compose.yml -O docker-compose.yml
docker-compose up -d

# 启动 Elasticsearch (可选，用于关键词检索增强)
docker run -d --name elasticsearch -p 9200:9200 -e "discovery.type=single-node" elasticsearch:7.17.10
```

### 3. 配置应用

复制示例配置文件并设置 API Key：

```bash
cp config/app.yml.example config/app.yml
```

编辑 `config/app.yml` 或设置环境变量：

```bash
# 推荐方式：设置环境变量 (避免 Key 泄露)
export DASH_API_KEY="sk-xxxxxxxxxxxxxxxxxxxxxxxx"
```

*注意：本项目默认配置为使用阿里云 DashScope (Qwen-Plus / Qwen-VL)，你需要拥有有效的 API Key。*

### 4. 启动后端服务

在项目根目录下执行：

```bash
# 启动服务端 (默认端口 18081)
./gradlew :server:run --args='--port=18081 --home=./config'
```

服务启动后，将自动监听 `data/` 目录下的文件变化。

### 5. 启动前端界面

新开一个终端窗口：

```bash
cd web-react
npm install
npm run dev
```

访问浏览器：`http://localhost:5173` (或控制台输出的地址)。

---

## 📂 项目结构

```
.
├── server/                 # Java 后端源码
├── web-react/              # React 前端源码
├── config/                 # 配置文件目录
│   ├── app.yml             # 主配置
│   ├── skills.d/           # Agent 技能定义
│   └── rules.system.d/     # Agent 系统提示词
├── data/                   # 默认的数据监听目录 (放入文件即可被索引)
└── docs/                   # 详细文档
```

## 🛠️ 常见问题

- **视频无法解析？**
  - 确保系统安装了 `ffmpeg`。
  - 检查 `config/app.yml` 中 LLM 配置是否支持 Vision 模型 (如 `qwen-vl-max`)。
- **RAG 检索无结果？**
  - 检查 Milvus 服务是否正常运行。
  - 查看后端日志 `DEBUG: RAG search query...` 确认是否有召回。

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！
