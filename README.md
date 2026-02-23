# AI Assistant Prototype (Java Desktop + Local HTTP + Web UI)

## 运行方式

在项目根目录执行：

```bash
cd ai-assistant-prototype
gradle :desktop:run
```

默认会启动本地服务在 `http://127.0.0.1:18080/` 并打开桌面窗口（内置 WebView）。

如果你只想用浏览器打开 Web UI，也可以只启动服务端，然后访问本地地址即可。

## 配置在哪里

- 配置根目录（home）默认是 `ai-assistant-prototype/config`。
- 桌面端：通过 JVM System Property `assistant.home` 指定 home。
- 服务端：通过启动参数 `--home=` 指定 home。

更完整的说明见：

- [docs/Configuration.md](file:///Users/charles/Documents/trae_projects/comercial/ai-assistant-prototype/docs/Configuration.md)
- [docs/WebUI_Functional_Design.md](file:///Users/charles/Documents/trae_projects/comercial/ai-assistant-prototype/docs/WebUI_Functional_Design.md)

## 配置

- 配置目录默认是 `ai-assistant-prototype/config`。
- 你也可以通过 JVM 参数指定：

```bash
gradle :desktop:run -Dassistant.home=/abs/path/to/config
```

只启动服务端时：

```bash
gradle :server:run --args='--port=18081 --home=/abs/path/to/config'
```

### Milvus

编辑 `config/app.yml`：

- `milvus.enabled: true`
- `milvus.host` / `milvus.port`

启动后，对监听目录下的文本文件进行新增/修改，会写入 Milvus collection `rag_chunks`。

## 当前原型能力说明

- 多目录递归监听：支持（新增目录后立即开始监听）。
- ETL：文本真实抽取与分块；图片/视频当前写入占位 chunk（后续可接入 vision/ffmpeg）。
- 写入：Milvus 支持；ES 可选配置已预留（写入与融合召回后续补齐）。
- YAML：`skills.d` / `rules.*.d` / `mcp.d` 可加载并在 UI 中查看。
- 对话：当前为占位实现（使用 SQLite LIKE 召回）；Milvus + ES 融合召回与远程 LLM 生成后续补齐。

## MCP（占位框架）

- `GET /api/mcp/tools`：列出内置 tools（file.list/file.read/index.search）
- `POST /api/mcp/call`：调用内置 tool（JSON: `{ "name": "file.list", "input": {"path": "/tmp"} }`）
