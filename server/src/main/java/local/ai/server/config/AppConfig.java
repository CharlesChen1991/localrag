package local.ai.server.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

public final class AppConfig {
  public final Path homeDir;
  public final Path dataDir;
  public final Path configDir;
  public final MilvusConfig milvus;
  public final EsConfig es;
  public final LlmConfig llm;
  public final EtlConfig etl;

  private AppConfig(Path homeDir, Path dataDir, Path configDir, MilvusConfig milvus, EsConfig es, LlmConfig llm, EtlConfig etl) {
    this.homeDir = homeDir;
    this.dataDir = dataDir;
    this.configDir = configDir;
    this.milvus = milvus;
    this.es = es;
    this.llm = llm;
    this.etl = etl;
  }

  @SuppressWarnings("unchecked")
  public static AppConfig load(Path homeDir) {
    Path configDir = homeDir.resolve(".").toAbsolutePath().normalize();
    Path appYml = configDir.resolve("app.yml");
    Map<String, Object> root = Collections.emptyMap();
    try {
      if (Files.exists(appYml)) {
        Object loaded = new Yaml().load(Files.newBufferedReader(appYml));
        if (loaded instanceof Map) {
          root = (Map<String, Object>) loaded;
          expandMap(root);
        }
      }
    } catch (Exception ignored) {
    }

    Path dataDir = configDir.resolve("data");
    Object dataDirValue = root.get("dataDir");
    if (dataDirValue instanceof String) {
      Path dd = Paths.get((String) dataDirValue);
      dataDir = dd.isAbsolute() ? dd : configDir.resolve(dd).normalize();
    }

    Map<String, Object> milvusRaw = (Map<String, Object>) root.getOrDefault("milvus", new HashMap<String, Object>());
    MilvusConfig milvus = MilvusConfig.from(milvusRaw);

    Map<String, Object> esRaw = (Map<String, Object>) root.getOrDefault("es", new HashMap<String, Object>());
    EsConfig es = EsConfig.from(esRaw);

    Map<String, Object> llmRaw = (Map<String, Object>) root.getOrDefault("llm", new HashMap<String, Object>());
    LlmConfig llm = LlmConfig.from(llmRaw);

    Map<String, Object> etlRaw = (Map<String, Object>) root.getOrDefault("etl", new HashMap<String, Object>());
    EtlConfig etl = EtlConfig.from(etlRaw);

    return new AppConfig(configDir, dataDir.toAbsolutePath().normalize(), configDir, milvus, es, llm, etl);
  }

  @SuppressWarnings("unchecked")
  private static void expandMap(Map<String, Object> map) {
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      Object val = entry.getValue();
      if (val instanceof String) {
        entry.setValue(expandVars((String) val));
      } else if (val instanceof Map) {
        expandMap((Map<String, Object>) val);
      } else if (val instanceof java.util.List) {
        java.util.List<Object> list = (java.util.List<Object>) val;
        for (int i = 0; i < list.size(); i++) {
          Object item = list.get(i);
          if (item instanceof String) {
            list.set(i, expandVars((String) item));
          } else if (item instanceof Map) {
            expandMap((Map<String, Object>) item);
          }
        }
      }
    }
  }

  private static String expandVars(String value) {
    if (value == null) return null;
    StringBuilder sb = new StringBuilder();
    int cursor = 0;
    while (cursor < value.length()) {
      int start = value.indexOf("${", cursor);
      if (start == -1) {
        sb.append(value.substring(cursor));
        break;
      }
      sb.append(value.substring(cursor, start));
      int end = value.indexOf("}", start);
      if (end == -1) {
        sb.append(value.substring(start));
        break;
      }
      String key = value.substring(start + 2, end);
      String replacement;
      if ("user.home".equals(key)) {
        replacement = System.getProperty("user.home");
      } else if ("user.dir".equals(key)) {
        replacement = System.getProperty("user.dir");
      } else {
        replacement = System.getenv(key);
        if (replacement == null) {
          replacement = System.getProperty(key); // Also check system properties
        }
        if (replacement == null) {
           // Keep original if not found, or empty string?
           // Usually keep original so user sees the placeholder or error later.
           // But here let's keep original for now.
           replacement = "${" + key + "}";
        }
      }
      sb.append(replacement);
      cursor = end + 1;
    }
    return sb.toString();
  }
}
