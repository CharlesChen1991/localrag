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
        }
      }
    } catch (Exception ignored) {
    }

    Path dataDir = configDir.resolve("data");
    Object dataDirValue = root.get("dataDir");
    if (dataDirValue instanceof String) {
      String expanded = expandVars((String) dataDirValue);
      Path dd = Paths.get(expanded);
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

  private static String expandVars(String value) {
    String v = value;
    v = v.replace("${user.home}", System.getProperty("user.home"));
    v = v.replace("${user.dir}", System.getProperty("user.dir"));
    return v;
  }
}
