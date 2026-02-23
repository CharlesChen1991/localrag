package local.ai.server.config;

import java.util.Map;

public final class MilvusConfig {
  public final boolean enabled;
  public final String host;
  public final int port;
  public final String collection;
  public final int dim;

  private MilvusConfig(boolean enabled, String host, int port, String collection, int dim) {
    this.enabled = enabled;
    this.host = host;
    this.port = port;
    this.collection = collection;
    this.dim = dim;
  }

  public static MilvusConfig from(Map<String, Object> raw) {
    boolean enabled = Boolean.TRUE.equals(raw.get("enabled"));
    String host = String.valueOf(raw.getOrDefault("host", "127.0.0.1"));
    int port = asInt(raw.getOrDefault("port", 19530), 19530);
    String collection = String.valueOf(raw.getOrDefault("collection", "rag_chunks"));
    int dim = asInt(raw.getOrDefault("dim", 384), 384);
    return new MilvusConfig(enabled, host, port, collection, dim);
  }

  private static int asInt(Object value, int fallback) {
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (Exception e) {
      return fallback;
    }
  }
}

