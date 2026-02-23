package local.ai.server.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

public final class YamlConfigLoader {
  private final Path configDir;

  private volatile Map<String, Map<String, Object>> skills = Collections.emptyMap();
  private volatile Map<String, Map<String, Object>> systemRules = Collections.emptyMap();
  private volatile Map<String, Map<String, Object>> triggerRules = Collections.emptyMap();
  private volatile Map<String, Map<String, Object>> mcp = Collections.emptyMap();

  public YamlConfigLoader(Path configDir) {
    this.configDir = configDir;
  }

  public void reload() {
    this.skills = readAll(configDir.resolve("skills.d"));
    this.systemRules = readAll(configDir.resolve("rules.system.d"));
    this.triggerRules = readAll(configDir.resolve("rules.triggers.d"));
    this.mcp = readAll(configDir.resolve("mcp.d"));
  }

  public List<Map<String, Object>> skills() {
    return new ArrayList<>(skills.values());
  }

  public Map<String, Object> getSkill(String fileName) {
    return skills.getOrDefault(fileName, Collections.emptyMap());
  }

  public List<Map<String, Object>> systemRules() {
    return new ArrayList<>(systemRules.values());
  }

  public Map<String, Object> getSystemRule(String fileName) {
    return systemRules.getOrDefault(fileName, Collections.emptyMap());
  }

  public List<Map<String, Object>> triggerRules() {
    return new ArrayList<>(triggerRules.values());
  }

  public Map<String, Object> getTriggerRule(String fileName) {
    return triggerRules.getOrDefault(fileName, Collections.emptyMap());
  }

  public List<Map<String, Object>> mcp() {
    return new ArrayList<>(mcp.values());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Map<String, Object>> readAll(Path dir) {
    if (!Files.isDirectory(dir)) {
      return Collections.emptyMap();
    }

    Map<String, Map<String, Object>> out = new java.util.LinkedHashMap<>();
    Yaml yaml = new Yaml();
    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.yml")) {
      for (Path p : ds) {
        Object loaded;
        try {
          loaded = yaml.load(Files.newBufferedReader(p));
        } catch (IOException e) {
          continue;
        }
        if (loaded instanceof Map) {
          Map<String, Object> m = (Map<String, Object>) loaded;
          String fn = p.getFileName().toString();
          m.put("fileName", fn);
          out.put(fn, m);
        }
      }
    } catch (IOException ignored) {
    }
    return Collections.unmodifiableMap(out);
  }
}
