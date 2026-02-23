package local.ai.server;

import java.nio.file.Path;
import local.ai.server.config.AppConfig;
import local.ai.server.config.YamlConfigLoader;
import local.ai.server.db.SqliteStore;
import local.ai.server.etl.EtlService;
import local.ai.server.index.ElasticsearchSink;
import local.ai.server.index.MilvusVectorSink;
import local.ai.server.mcp.McpRuntime;
import local.ai.server.watch.MultiDirectoryWatcher;
import local.ai.server.web.HttpApi;

public final class ServerRuntime {
  private final SqliteStore store;
  private final MultiDirectoryWatcher watcher;
  private final EtlService etl;
  private final HttpApi http;
  private final YamlConfigLoader yaml;
  private final McpRuntime mcp;

  private ServerRuntime(SqliteStore store, MultiDirectoryWatcher watcher, EtlService etl, HttpApi http, YamlConfigLoader yaml, McpRuntime mcp) {
    this.store = store;
    this.watcher = watcher;
    this.etl = etl;
    this.http = http;
    this.yaml = yaml;
    this.mcp = mcp;
  }

  public static ServerRuntime start(int port, Path home) {
    Path resolvedHome = home.toAbsolutePath().normalize();
    AppConfig config = AppConfig.load(resolvedHome);

    SqliteStore store = new SqliteStore(config.dataDir.resolve("app.db"));
    store.init();

    YamlConfigLoader yaml = new YamlConfigLoader(config.configDir);
    yaml.reload();

    MilvusVectorSink milvus = new MilvusVectorSink(config.milvus);
    ElasticsearchSink es = new ElasticsearchSink(config.es);
    EtlService etl = new EtlService(config, store, milvus, es, yaml);

    MultiDirectoryWatcher watcher = new MultiDirectoryWatcher(etl);
    store.listDirectories().forEach(watcher::addRoot);

    McpRuntime mcp = new McpRuntime(store);
    HttpApi http = new HttpApi(port, config, store, yaml, mcp, watcher, etl);
    http.start();
    watcher.start();

    return new ServerRuntime(store, watcher, etl, http, yaml, mcp);
  }

  public void stop() {
    try {
      watcher.stop();
    } catch (Exception ignored) {
    }
    try {
      http.stop();
    } catch (Exception ignored) {
    }
    try {
      etl.stop();
    } catch (Exception ignored) {
    }
    try {
      store.close();
    } catch (Exception ignored) {
    }
  }
}
