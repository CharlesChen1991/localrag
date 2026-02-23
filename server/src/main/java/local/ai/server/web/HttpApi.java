package local.ai.server.web;

import static spark.Spark.*;

import com.fasterxml.jackson.databind.JsonNode;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.collection.HasCollectionParam;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import local.ai.server.config.AppConfig;
import local.ai.server.config.YamlFileManager;
import local.ai.server.config.YamlConfigLoader;
import local.ai.server.db.SqliteStore;
import local.ai.server.etl.ChatAnswer;
import local.ai.server.etl.EtlService;
import local.ai.server.mcp.McpRuntime;
import local.ai.server.watch.MultiDirectoryWatcher;
import local.ai.shared.Json;
import local.ai.shared.model.ChatRequest;
import local.ai.shared.model.ChatResponse;
import local.ai.shared.model.DirectoryAddRequest;
import local.ai.shared.model.HealthResponse;

public final class HttpApi {
  private final int port;
  private final AppConfig config;
  private final SqliteStore store;
  private final YamlConfigLoader yaml;
  private final YamlFileManager yamlFiles;
  private final McpRuntime mcp;
  private final MultiDirectoryWatcher watcher;
  private final EtlService etl;

  public HttpApi(int port, AppConfig config, SqliteStore store, YamlConfigLoader yaml, McpRuntime mcp, MultiDirectoryWatcher watcher, EtlService etl) {
    this.port = port;
    this.config = config;
    this.store = store;
    this.yaml = yaml;
    this.yamlFiles = new YamlFileManager(config.configDir);
    this.mcp = mcp;
    this.watcher = watcher;
    this.etl = etl;
  }

  public void start() {
    ipAddress("127.0.0.1");
    port(port);
    staticFiles.location("/web");

    exception(IllegalArgumentException.class, (e, req, res) -> {
      res.status(400);
      res.type("application/json");
      Map<String, Object> out = new LinkedHashMap<String, Object>();
      out.put("ok", false);
      out.put("error", String.valueOf(e.getMessage()));
      res.body(Json.toJson(out));
    });

    exception(Exception.class, (e, req, res) -> {
      res.status(500);
      res.type("application/json");
      Map<String, Object> out = new LinkedHashMap<String, Object>();
      out.put("ok", false);
      out.put("error", "internal");
      res.body(Json.toJson(out));
    });

    before((req, res) -> {
      res.header("Access-Control-Allow-Origin", "*");
      res.header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
      res.header("Access-Control-Allow-Headers", "Content-Type,Authorization");
    });

    options("/*", (req, res) -> "");

    get("/api/health", (req, res) -> {
      res.type("application/json");
      return Json.toJson(new HealthResponse("ok"));
    });

    get("/api/config", (req, res) -> {
      res.type("application/json");
      Map<String, Object> milvus = new LinkedHashMap<String, Object>();
      milvus.put("enabled", config.milvus.enabled);
      milvus.put("host", config.milvus.host);
      milvus.put("port", config.milvus.port);
      milvus.put("collection", config.milvus.collection);
      milvus.put("dim", config.milvus.dim);

      Map<String, Object> llm = new LinkedHashMap<String, Object>();
      llm.put("baseUrl", config.llm.baseUrl);
      llm.put("chatModel", config.llm.chatModel);
      llm.put("embeddingModel", config.llm.embeddingModel);

      Map<String, Object> es = new LinkedHashMap<String, Object>();
      es.put("enabled", config.es.enabled);
      es.put("url", config.es.url);
      es.put("username", config.es.username);

      Map<String, Object> etlCfg = new LinkedHashMap<String, Object>();
      etlCfg.put("maxTextBytes", config.etl.maxTextBytes);
      etlCfg.put("chunkMaxChars", config.etl.chunkMaxChars);

      Map<String, Object> out = new LinkedHashMap<String, Object>();
      out.put("homeDir", config.homeDir.toString());
      out.put("dataDir", config.dataDir.toString());
      out.put("milvus", milvus);
      out.put("es", es);
      out.put("llm", llm);
      out.put("etl", etlCfg);
      return Json.toJson(out);
    });

    get("/api/services/health", (req, res) -> {
      res.type("application/json");
      Map<String, Object> out = new LinkedHashMap<String, Object>();
      out.put("milvus", milvusHealth());
      out.put("elasticsearch", esHealth());
      return Json.toJson(out);
    });

    post("/api/config/reload", (req, res) -> {
      yaml.reload();
      res.type("application/json");
      Map<String, Object> out = new HashMap<String, Object>();
      out.put("ok", true);
      return Json.toJson(out);
    });

    get("/api/yaml/templates", (req, res) -> {
      res.type("application/json");
      return Json.toJson(yamlFiles.templates());
    });

    get("/api/yaml/:kind", (req, res) -> {
      res.type("application/json");
      String kind = req.params(":kind");
      return Json.toJson(yamlFiles.list(kind));
    });

    get("/api/yaml/:kind/:name", (req, res) -> {
      res.type("application/json");
      String kind = req.params(":kind");
      String name = req.params(":name");
      return Json.toJson(yamlFiles.read(kind, name));
    });

    put("/api/yaml/:kind/:name", (req, res) -> {
      res.type("application/json");
      String kind = req.params(":kind");
      String name = req.params(":name");
      JsonNode root = Json.mapper().readTree(req.body());
      String content = root.path("content").asText("");
      yamlFiles.write(kind, name, content);
      yaml.reload();
      Map<String, Object> out = new LinkedHashMap<String, Object>();
      out.put("ok", true);
      out.put("name", name);
      return Json.toJson(out);
    });

    delete("/api/yaml/:kind/:name", (req, res) -> {
      res.type("application/json");
      String kind = req.params(":kind");
      String name = req.params(":name");
      yamlFiles.delete(kind, name);
      yaml.reload();
      Map<String, Object> out = new LinkedHashMap<String, Object>();
      out.put("ok", true);
      return Json.toJson(out);
    });

    get("/api/skills", (req, res) -> {
      res.type("application/json");
      return Json.toJson(yaml.skills());
    });

    get("/api/rules/system", (req, res) -> {
      res.type("application/json");
      return Json.toJson(yaml.systemRules());
    });

    get("/api/rules/triggers", (req, res) -> {
      res.type("application/json");
      return Json.toJson(yaml.triggerRules());
    });

    get("/api/mcp", (req, res) -> {
      res.type("application/json");
      return Json.toJson(yaml.mcp());
    });

    get("/api/mcp/tools", (req, res) -> {
      res.type("application/json");
      return Json.toJson(mcp.listTools());
    });

    post("/api/mcp/call", (req, res) -> {
      JsonNode root = Json.mapper().readTree(req.body());
      String name = root.path("name").asText("");
      Map<String, Object> input = Json.mapper().convertValue(root.path("input"), Map.class);
      Object result = mcp.call(name, input);
      res.type("application/json");
      return Json.toJson(result);
    });

    get("/api/directories", (req, res) -> {
      res.type("application/json");
      List<String> dirs = new ArrayList<String>();
      for (Path p : store.listDirectories()) {
        dirs.add(p.toString());
      }
      return Json.toJson(dirs);
    });

    post("/api/directories", (req, res) -> {
      DirectoryAddRequest r = Json.mapper().readValue(req.body(), DirectoryAddRequest.class);
      Path p = Paths.get(r.path).toAbsolutePath().normalize();
      store.addDirectory(p);
      watcher.addRoot(p);
      res.type("application/json");
      Map<String, Object> out = new LinkedHashMap<String, Object>();
      out.put("ok", true);
      out.put("path", p.toString());
      return Json.toJson(out);
    });

    post("/api/chat", (req, res) -> {
      ChatRequest r = Json.mapper().readValue(req.body(), ChatRequest.class);
      String sessionId = r.sessionId == null || r.sessionId.isEmpty() ? UUID.randomUUID().toString() : r.sessionId;
      String userMsg = r.message == null ? "" : r.message;
      store.upsertSession(sessionId, titleFrom(userMsg));
      store.appendMessage(sessionId, "user", userMsg);

      ChatAnswer ans = etl.chat(userMsg, 5);
      store.appendMessage(sessionId, "assistant", ans.answer);
      ChatResponse resp = new ChatResponse(sessionId, ans.answer, ans.citations);
      res.type("application/json");
      return Json.toJson(resp);
    });

    post("/api/agents/:id/chat", (req, res) -> {
      String agentId = req.params(":id");
      System.err.println("DEBUG: API agentId=" + agentId);
      JsonNode root = Json.mapper().readTree(req.body());
      String sessionId = root.path("sessionId").asText("").trim();
      if (sessionId.isEmpty()) {
        sessionId = UUID.randomUUID().toString();
      }
      String userMsg = root.path("message").asText("");
      int topK = root.path("topK").asInt(5);
      boolean stream = root.path("stream").asBoolean(false);

      store.upsertSession(sessionId, agentId, titleFrom(userMsg));
      store.appendMessage(sessionId, "user", userMsg);

      if (stream) {
        res.type("text/event-stream");
        res.header("Cache-Control", "no-cache");
        res.header("Connection", "keep-alive");
        res.header("Content-Type", "text/event-stream; charset=UTF-8");
        
        final String sid = sessionId;
        
        try {
          res.raw().setCharacterEncoding("UTF-8");
          java.io.PrintWriter writer = res.raw().getWriter();
          // Send session ID first
          Map<String, Object> init = new HashMap<>();
          init.put("sessionId", sid);
          writer.write("data: " + Json.toJson(init) + "\n\n");
          writer.flush();
          
          etl.chatStream(agentId, userMsg, topK, token -> {
             try {
               Map<String, String> delta = new HashMap<>();
               delta.put("delta", token);
               writer.write("data: " + Json.toJson(delta) + "\n\n");
               writer.flush();
             } catch (Exception ignored) {}
          }, ans -> {
             store.appendMessage(sid, "assistant", ans.answer);
             try {
               if (ans.citations != null && !ans.citations.isEmpty()) {
                  Map<String, Object> cit = new HashMap<>();
                  cit.put("citations", ans.citations);
                  writer.write("data: " + Json.toJson(cit) + "\n\n");
               }
               writer.write("data: [DONE]\n\n");
               writer.flush();
             } catch (Exception ignored) {}
          });
        } catch (Exception e) {
          e.printStackTrace();
        }
        return "";
      } else {
        res.type("application/json");
        ChatAnswer ans = etl.chat(userMsg, topK);
        store.appendMessage(sessionId, "assistant", ans.answer);
        ChatResponse resp = new ChatResponse(sessionId, ans.answer, ans.citations);
        return Json.toJson(resp);
      }
    });

    get("/api/chat/sessions", (req, res) -> {
      res.type("application/json");
      return Json.toJson(store.listSessions(50));
    });

    get("/api/agents/:id/chat/sessions", (req, res) -> {
      res.type("application/json");
      String agentId = req.params(":id");
      return Json.toJson(store.listSessionsByAgent(agentId, 50));
    });

    get("/api/chat/sessions/:id", (req, res) -> {
      res.type("application/json");
      return Json.toJson(store.listMessages(req.params(":id"), 200));
    });

    get("/api/agents/:id/chat/sessions/:sid", (req, res) -> {
      res.type("application/json");
      return Json.toJson(store.listMessages(req.params(":sid"), 200));
    });

    delete("/api/agents/:id/chat/sessions/:sid", (req, res) -> {
      res.type("application/json");
      String agentId = req.params(":id");
      String sessionId = req.params(":sid");
      store.deleteSession(sessionId);
      Map<String, Object> out = new LinkedHashMap<String, Object>();
      out.put("ok", true);
      return Json.toJson(out);
    });

    post("/api/rag/search", (req, res) -> {
      res.type("application/json");
      JsonNode root = Json.mapper().readTree(req.body());
      String query = root.path("query").asText("");
      int topK = root.path("topK").asInt(8);
      List<SqliteStore.ChunkRow> rows = etl.ragRecall(query, topK);
      List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
      for (SqliteStore.ChunkRow r : rows) {
        Map<String, Object> it = new LinkedHashMap<String, Object>();
        it.put("chunkId", r.chunkId);
        it.put("path", r.path);
        String c = r.content == null ? "" : r.content;
        it.put("preview", c.length() > 400 ? c.substring(0, 400) + "..." : c);
        items.add(it);
      }
      Map<String, Object> out = new LinkedHashMap<String, Object>();
      out.put("items", items);
      out.put("topK", topK);
      out.put("milvusEnabled", config.milvus.enabled);
      out.put("esEnabled", config.es.enabled);
      return Json.toJson(out);
    });

    get("/api/jobs", (req, res) -> {
      res.type("application/json");
      return Json.toJson(store.listJobs(50));
    });

    get("/api/agents", (req, res) -> {
      res.type("application/json");
      return Json.toJson(store.listAgents(200));
    });

    post("/api/agents", (req, res) -> {
      res.type("application/json");
      JsonNode root = Json.mapper().readTree(req.body());
      String name = root.path("name").asText("").trim();
      if (name.isEmpty()) {
        throw new IllegalArgumentException("name required");
      }
      String description = root.path("description").asText("");
      List<String> tags = readStringList(root.path("tags"));
      List<String> skillFiles = readStringList(root.path("skillFiles"));
      List<String> systemRuleFiles = readStringList(root.path("systemRuleFiles"));
      List<String> triggerRuleFiles = readStringList(root.path("triggerRuleFiles"));
      return Json.toJson(store.createAgent(name, description, tags, skillFiles, systemRuleFiles, triggerRuleFiles));
    });

    get("/api/agents/:id", (req, res) -> {
      res.type("application/json");
      return Json.toJson(store.getAgent(req.params(":id")));
    });

    put("/api/agents/:id", (req, res) -> {
      res.type("application/json");
      JsonNode root = Json.mapper().readTree(req.body());
      String name = root.path("name").asText("").trim();
      if (name.isEmpty()) {
        throw new IllegalArgumentException("name required");
      }
      String description = root.path("description").asText("");
      List<String> tags = readStringList(root.path("tags"));
      List<String> skillFiles = readStringList(root.path("skillFiles"));
      List<String> systemRuleFiles = readStringList(root.path("systemRuleFiles"));
      List<String> triggerRuleFiles = readStringList(root.path("triggerRuleFiles"));
      store.updateAgent(req.params(":id"), name, description, tags, skillFiles, systemRuleFiles, triggerRuleFiles);
      Map<String, Object> out = new LinkedHashMap<String, Object>();
      out.put("ok", true);
      return Json.toJson(out);
    });

    delete("/api/agents/:id", (req, res) -> {
      res.type("application/json");
      store.deleteAgent(req.params(":id"));
      Map<String, Object> out = new LinkedHashMap<String, Object>();
      out.put("ok", true);
      return Json.toJson(out);
    });

    get("/agents", (req, res) -> {
      res.type("text/html");
      return readResource("/web/index.html");
    });

    get("/agents/*", (req, res) -> {
      res.type("text/html");
      return readResource("/web/index.html");
    });

    get("/local-dev", (req, res) -> {
      res.type("text/html");
      return readResource("/web/index.html");
    });

    get("/skills", (req, res) -> {
      res.type("text/html");
      return readResource("/web/index.html");
    });

    get("/skills/*", (req, res) -> {
      res.type("text/html");
      return readResource("/web/index.html");
    });

    get("/chat", (req, res) -> {
      res.type("text/html");
      return readResource("/web/index.html");
    });

    get("/chat/*", (req, res) -> {
      res.type("text/html");
      return readResource("/web/index.html");
    });

    get("/rag", (req, res) -> {
      res.type("text/html");
      return readResource("/web/index.html");
    });

    get("/rag/*", (req, res) -> {
      res.type("text/html");
      return readResource("/web/index.html");
    });
  }

  private static String titleFrom(String msg) {
    if (msg == null) {
      return "";
    }
    String s = msg.trim();
    if (s.isEmpty()) {
      return "";
    }
    return s.length() > 30 ? s.substring(0, 30) : s;
  }

  public void stop() {
    spark.Spark.stop();
    spark.Spark.awaitStop();
  }

  private Map<String, Object> milvusHealth() {
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("enabled", config.milvus.enabled);
    out.put("collection", config.milvus.collection);
    if (!config.milvus.enabled) {
      out.put("ok", false);
      out.put("detail", "disabled");
      return out;
    }
    MilvusServiceClient client = null;
    try {
      client = new MilvusServiceClient(ConnectParam.newBuilder().withHost(config.milvus.host).withPort(config.milvus.port).build());
      R<Boolean> has = client.hasCollection(HasCollectionParam.newBuilder().withCollectionName(config.milvus.collection).build());
      boolean ok = has.getStatus() == R.Status.Success.getCode();
      out.put("ok", ok);
      out.put("ready", ok && Boolean.TRUE.equals(has.getData()));
      if (!ok) {
        out.put("detail", has.getMessage());
      }
      return out;
    } catch (Exception e) {
      out.put("ok", false);
      out.put("detail", String.valueOf(e.getMessage()));
      return out;
    } finally {
      try {
        if (client != null) {
          client.close();
        }
      } catch (Exception ignored) {
      }
    }
  }

  private Map<String, Object> esHealth() {
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("enabled", config.es.enabled);
    out.put("index", "rag_chunks");
    if (!config.es.enabled) {
      out.put("ok", false);
      out.put("detail", "disabled");
      return out;
    }
    String base = config.es.url == null ? "" : config.es.url.trim();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    if (base.isEmpty()) {
      out.put("ok", false);
      out.put("detail", "missing url");
      return out;
    }
    try {
      int code = httpCode(base + "/_cluster/health");
      boolean ok = code >= 200 && code < 300;
      out.put("ok", ok);
      if (!ok) {
        out.put("detail", "HTTP " + code);
        return out;
      }
      int idx = httpCode(base + "/rag_chunks");
      out.put("indexReady", idx >= 200 && idx < 300);
      return out;
    } catch (Exception e) {
      out.put("ok", false);
      out.put("detail", String.valueOf(e.getMessage()));
      return out;
    }
  }

  private static int httpCode(String url) {
    HttpURLConnection conn = null;
    try {
      conn = (HttpURLConnection) new URL(url).openConnection();
      conn.setConnectTimeout(800);
      conn.setReadTimeout(800);
      conn.setRequestMethod("GET");
      return conn.getResponseCode();
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      try {
        if (conn != null) {
          conn.disconnect();
        }
      } catch (Exception ignored) {
      }
    }
  }

  private static List<String> readStringList(JsonNode node) {
    List<String> out = new ArrayList<String>();
    if (node == null || node.isNull()) {
      return out;
    }
    if (node.isArray()) {
      for (JsonNode x : node) {
        String s = x.asText("").trim();
        if (!s.isEmpty()) {
          out.add(s);
        }
      }
      return out;
    }
    String s = node.asText("").trim();
    if (!s.isEmpty()) {
      out.add(s);
    }
    return out;
  }

  private static String readResource(String path) {
    try (InputStream in = HttpApi.class.getResourceAsStream(path)) {
      if (in == null) {
        return "";
      }
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) >= 0) {
        bos.write(buf, 0, n);
      }
      return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      return "";
    }
  }
}
