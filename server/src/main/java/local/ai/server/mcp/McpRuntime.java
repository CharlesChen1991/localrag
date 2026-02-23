package local.ai.server.mcp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import local.ai.server.db.SqliteStore;

public final class McpRuntime {
  private final SqliteStore store;

  public McpRuntime(SqliteStore store) {
    this.store = store;
  }

  public List<Map<String, Object>> listTools() {
    List<Map<String, Object>> tools = new ArrayList<Map<String, Object>>();
    tools.add(tool("file.list", "列出目录下的文件", schemaDirPath()));
    tools.add(tool("file.read", "读取文件内容（限制大小）", schemaFilePath()));
    tools.add(tool("index.search", "在本地索引里按关键字搜索", schemaQuery()));
    return Collections.unmodifiableList(tools);
  }

  public Object call(String name, Map<String, Object> input) {
    if ("file.list".equals(name)) {
      return fileList(input);
    }
    if ("file.read".equals(name)) {
      return fileRead(input);
    }
    if ("index.search".equals(name)) {
      return indexSearch(input);
    }
    Map<String, Object> err = new LinkedHashMap<String, Object>();
    err.put("error", "unknown tool");
    err.put("tool", name);
    return err;
  }

  private Object fileList(Map<String, Object> input) {
    String p = String.valueOf(input.get("path"));
    if (p == null || p.isEmpty()) {
      Map<String, Object> res = new LinkedHashMap<String, Object>();
      res.put("items", Collections.emptyList());
      return res;
    }
    Path dir = Paths.get(p).toAbsolutePath().normalize();
    if (!Files.isDirectory(dir)) {
      Map<String, Object> res = new LinkedHashMap<String, Object>();
      res.put("items", Collections.emptyList());
      return res;
    }
    List<String> out = new ArrayList<String>();
    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
      for (Path x : ds) {
        out.add(x.getFileName().toString());
      }
    } catch (IOException ignored) {
    }
    Map<String, Object> res = new LinkedHashMap<String, Object>();
    res.put("path", dir.toString());
    res.put("items", out);
    return res;
  }

  private Object fileRead(Map<String, Object> input) {
    String p = String.valueOf(input.get("path"));
    long maxBytes = 200_000L;
    Object mb = input.get("maxBytes");
    if (mb instanceof Number) {
      maxBytes = Math.min(2_000_000L, Math.max(1_000L, ((Number) mb).longValue()));
    }
    if (p == null || p.isEmpty()) {
      Map<String, Object> res = new LinkedHashMap<String, Object>();
      res.put("content", "");
      return res;
    }
    Path file = Paths.get(p).toAbsolutePath().normalize();
    if (!Files.isRegularFile(file)) {
      Map<String, Object> res = new LinkedHashMap<String, Object>();
      res.put("content", "");
      return res;
    }
    try {
      byte[] bytes = Files.readAllBytes(file);
      int n = (int) Math.min(bytes.length, maxBytes);
      String s = new String(bytes, 0, n, StandardCharsets.UTF_8);
      Map<String, Object> res = new LinkedHashMap<String, Object>();
      res.put("path", file.toString());
      res.put("truncated", bytes.length > maxBytes);
      res.put("content", s);
      return res;
    } catch (IOException e) {
      Map<String, Object> res = new LinkedHashMap<String, Object>();
      res.put("content", "");
      return res;
    }
  }

  private Object indexSearch(Map<String, Object> input) {
    String q = String.valueOf(input.get("query"));
    if (q == null) {
      q = "";
    }
    List<SqliteStore.ChunkRow> rows = store.searchChunksLike(q, 5);
    List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
    for (SqliteStore.ChunkRow row : rows) {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("chunkId", row.chunkId);
      item.put("path", row.path);
      item.put("preview", row.content.length() > 200 ? row.content.substring(0, 200) : row.content);
      out.add(item);
    }
    Map<String, Object> res = new LinkedHashMap<String, Object>();
    res.put("query", q);
    res.put("items", out);
    return res;
  }

  private static Map<String, Object> tool(String name, String description, Map<String, Object> inputSchema) {
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("name", name);
    out.put("description", description);
    out.put("input_schema", inputSchema);
    return out;
  }

  private static Map<String, Object> schemaDirPath() {
    Map<String, Object> props = new LinkedHashMap<String, Object>();
    Map<String, Object> path = new LinkedHashMap<String, Object>();
    path.put("type", "string");
    props.put("path", path);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("type", "object");
    out.put("properties", props);
    out.put("required", Arrays.asList("path"));
    return out;
  }

  private static Map<String, Object> schemaFilePath() {
    Map<String, Object> props = new LinkedHashMap<String, Object>();
    Map<String, Object> path = new LinkedHashMap<String, Object>();
    path.put("type", "string");
    props.put("path", path);
    Map<String, Object> maxBytes = new LinkedHashMap<String, Object>();
    maxBytes.put("type", "number");
    props.put("maxBytes", maxBytes);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("type", "object");
    out.put("properties", props);
    out.put("required", Arrays.asList("path"));
    return out;
  }

  private static Map<String, Object> schemaQuery() {
    Map<String, Object> props = new LinkedHashMap<String, Object>();
    Map<String, Object> query = new LinkedHashMap<String, Object>();
    query.put("type", "string");
    props.put("query", query);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("type", "object");
    out.put("properties", props);
    out.put("required", Arrays.asList("query"));
    return out;
  }
}
