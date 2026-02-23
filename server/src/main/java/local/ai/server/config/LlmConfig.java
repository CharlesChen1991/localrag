package local.ai.server.config;

import java.util.Map;

public final class LlmConfig {
  public final String baseUrl;
  public final String apiKey;
  public final String chatModel;
  public final String embeddingModel;

  private LlmConfig(String baseUrl, String apiKey, String chatModel, String embeddingModel) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.chatModel = chatModel;
    this.embeddingModel = embeddingModel;
  }

  public static LlmConfig from(Map<String, Object> raw) {
    String baseUrl = String.valueOf(raw.getOrDefault("baseUrl", ""));
    String apiKey = String.valueOf(raw.getOrDefault("apiKey", ""));
    String chatModel = String.valueOf(raw.getOrDefault("chatModel", ""));
    String embeddingModel = String.valueOf(raw.getOrDefault("embeddingModel", ""));
    return new LlmConfig(baseUrl, apiKey, chatModel, embeddingModel);
  }

  public boolean hasRemoteEmbedding() {
    return baseUrl != null && !baseUrl.isEmpty() && apiKey != null && !apiKey.isEmpty() && embeddingModel != null && !embeddingModel.isEmpty();
  }

  public boolean hasRemoteChat() {
    return baseUrl != null && !baseUrl.isEmpty() && apiKey != null && !apiKey.isEmpty() && chatModel != null && !chatModel.isEmpty();
  }
}

