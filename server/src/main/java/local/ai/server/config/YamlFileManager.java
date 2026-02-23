package local.ai.server.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

public final class YamlFileManager {
  private final Path homeDir;

  public YamlFileManager(Path homeDir) {
    this.homeDir = homeDir;
  }

  public List<Map<String, Object>> list(String kind) {
    Path dir = dirFor(kind);
    if (!Files.isDirectory(dir)) {
      return new ArrayList<>();
    }
    List<Map<String, Object>> out = new ArrayList<>();
    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.yml")) {
      for (Path p : ds) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", p.getFileName().toString());
        try {
          row.put("mtime", Files.getLastModifiedTime(p).toMillis());
          row.put("size", Files.size(p));
        } catch (Exception ignored) {
          row.put("mtime", 0L);
          row.put("size", 0L);
        }
        out.add(row);
      }
    } catch (Exception ignored) {
    }
    return out;
  }

  public Map<String, Object> read(String kind, String name) {
    Path file = fileFor(kind, name);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("name", file.getFileName().toString());
    try {
      byte[] bytes = Files.readAllBytes(file);
      out.put("content", new String(bytes, StandardCharsets.UTF_8));
      out.put("mtime", Files.getLastModifiedTime(file).toMillis());
      out.put("size", Files.size(file));
    } catch (Exception e) {
      out.put("content", "");
      out.put("mtime", 0L);
      out.put("size", 0L);
      out.put("missing", true);
    }
    return out;
  }

  public void write(String kind, String name, String content) {
    if (content == null) {
      content = "";
    }
    validateYaml(content);
    Path dir = dirFor(kind);
    try {
      Files.createDirectories(dir);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    Path file = fileFor(kind, name);
    Path tmp = dir.resolve(file.getFileName().toString() + ".tmp");
    try {
      Files.write(tmp, content.getBytes(StandardCharsets.UTF_8));
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (Exception e) {
      try {
        Files.deleteIfExists(tmp);
      } catch (Exception ignored) {
      }
      throw new RuntimeException(e);
    }
  }

  public void delete(String kind, String name) {
    Path file = fileFor(kind, name);
    try {
      Files.deleteIfExists(file);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public Map<String, String> templates() {
    Map<String, String> out = new LinkedHashMap<>();
    out.put("skills", "name: example_tool\ndescription: \"\"\ninput_schema:\n  type: object\n  properties:\n    query:\n      type: string\nexecutor:\n  type: http\n  method: POST\n  url: http://127.0.0.1:9999/api\n");
    out.put("rules-system", "name: system_default\ncontent: |\n  你是一个本地AI助手。\n  遵守安全边界，仅访问用户已授权的目录。\n");
    out.put("rules-triggers", "name: trigger_example\nwhen:\n  type: contains\n  value: \"重建索引\"\nthen:\n  type: call_tool\n  name: index.rebuild\n  input: {}\n");
    out.put("mcp", "name: example_mcp_server\ntransport: http\nurl: http://127.0.0.1:8787\n");
    return out;
  }

  private Path dirFor(String kind) {
    if ("skills".equals(kind)) {
      return homeDir.resolve("skills.d");
    }
    if ("rules-system".equals(kind)) {
      return homeDir.resolve("rules.system.d");
    }
    if ("rules-triggers".equals(kind)) {
      return homeDir.resolve("rules.triggers.d");
    }
    if ("mcp".equals(kind)) {
      return homeDir.resolve("mcp.d");
    }
    throw new IllegalArgumentException("unknown kind");
  }

  private Path fileFor(String kind, String name) {
    if (name == null) {
      throw new IllegalArgumentException("name required");
    }
    String n = name.trim();
    if (!n.endsWith(".yml")) {
      n = n + ".yml";
    }
    if (!n.matches("[A-Za-z0-9._-]+\\.yml")) {
      throw new IllegalArgumentException("invalid name");
    }
    Path dir = dirFor(kind);
    Path p = dir.resolve(n).toAbsolutePath().normalize();
    if (!p.startsWith(dir.toAbsolutePath().normalize())) {
      throw new IllegalArgumentException("invalid path");
    }
    return p;
  }

  private static void validateYaml(String content) {
    try {
      new Yaml().load(content);
    } catch (Exception e) {
      throw new IllegalArgumentException("invalid yaml");
    }
  }
}
