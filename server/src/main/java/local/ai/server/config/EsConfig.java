package local.ai.server.config;

import java.util.Map;

public final class EsConfig {
  public final boolean enabled;
  public final String url;
  public final String username;
  public final String password;

  private EsConfig(boolean enabled, String url, String username, String password) {
    this.enabled = enabled;
    this.url = url;
    this.username = username;
    this.password = password;
  }

  public static EsConfig from(Map<String, Object> raw) {
    boolean enabled = Boolean.TRUE.equals(raw.get("enabled"));
    String url = String.valueOf(raw.getOrDefault("url", ""));
    String username = String.valueOf(raw.getOrDefault("username", ""));
    String password = String.valueOf(raw.getOrDefault("password", ""));
    return new EsConfig(enabled, url, username, password);
  }
}

