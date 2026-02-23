package local.ai.server.util;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import local.ai.shared.Json;

public final class OpenAiCompatibleClient {
  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  private final String baseUrl;
  private final String apiKey;
  private final OkHttpClient http;

  public OpenAiCompatibleClient(String baseUrl, String apiKey) {
    this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    this.apiKey = apiKey == null ? "" : apiKey;
    this.http = new OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build();
  }

  public float[] embed(String model, String input, int dim) {
    try {
      String url = baseUrl + (baseUrl.endsWith("/v1") ? "" : "/v1") + "/embeddings";
      String bodyJson = Json.toJson(new EmbeddingRequest(model, input));
      Request req = new Request.Builder()
          .url(url)
          .addHeader("Authorization", "Bearer " + apiKey)
          .post(RequestBody.create(bodyJson, JSON))
          .build();

      try (Response res = http.newCall(req).execute()) {
        if (!res.isSuccessful() || res.body() == null) {
          return LocalEmbedding.embed(input, dim);
        }
        JsonNode root = Json.mapper().readTree(res.body().string());
        JsonNode data0 = root.path("data").path(0).path("embedding");
        if (!data0.isArray()) {
          return LocalEmbedding.embed(input, dim);
        }
        float[] out = new float[dim];
        int n = Math.min(dim, data0.size());
        for (int i = 0; i < n; i++) {
          out[i] = (float) data0.get(i).asDouble();
        }
        return out;
      }
    } catch (Exception e) {
      return LocalEmbedding.embed(input, dim);
    }
  }

  public String chat(String model, String message) {
    return chat(model, java.util.Collections.singletonList(new Message("user", message)));
  }

  public String chat(String model, java.util.List<Message> messages) {
    try {
      String url = baseUrl + (baseUrl.endsWith("/v1") ? "" : "/v1") + "/chat/completions";
      ChatRequest body = new ChatRequest(model, messages, null, false);
      String bodyJson = Json.toJson(body);
      
      Request req = new Request.Builder()
          .url(url)
          .addHeader("Authorization", "Bearer " + apiKey)
          .post(RequestBody.create(bodyJson, JSON))
          .build();

      try (Response res = http.newCall(req).execute()) {
        if (!res.isSuccessful()) {
          String err = res.body() != null ? res.body().string() : "";
          return "Chat Error: " + res.code() + " " + err;
        }
        if (res.body() == null) {
          return "Chat Error: Empty response";
        }
        JsonNode root = Json.mapper().readTree(res.body().string());
        return root.path("choices").path(0).path("message").path("content").asText();
      }
    } catch (Exception e) {
      return "Chat Exception: " + e.getMessage();
    }
  }

  public void chatStream(String model, java.util.List<Message> messages, java.util.List<String> stop, java.util.function.Consumer<String> onToken) {
    try {
      String url = baseUrl + (baseUrl.endsWith("/v1") ? "" : "/v1") + "/chat/completions";
      ChatRequest body = new ChatRequest(model, messages, stop, true);
      String bodyJson = Json.toJson(body);
      
      Request req = new Request.Builder()
          .url(url)
          .addHeader("Authorization", "Bearer " + apiKey)
          .addHeader("Accept", "text/event-stream")
          .post(RequestBody.create(bodyJson, JSON))
          .build();

      try (Response res = http.newCall(req).execute()) {
        if (!res.isSuccessful()) {
          onToken.accept("Chat Error: " + res.code());
          return;
        }
        if (res.body() == null) {
          return;
        }
        
        java.io.BufferedReader reader = new java.io.BufferedReader(res.body().charStream());
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.startsWith("data: ")) {
            String data = line.substring(6).trim();
            if ("[DONE]".equals(data)) break;
            try {
              JsonNode root = Json.mapper().readTree(data);
              String content = root.path("choices").path(0).path("delta").path("content").asText("");
              if (!content.isEmpty()) {
                onToken.accept(content);
              }
            } catch (Exception ignored) {}
          }
        }
      }
    } catch (Exception e) {
      onToken.accept("Chat Exception: " + e.getMessage());
    }
  }

  public static final class ChatRequest {
    public final String model;
    public final java.util.List<Message> messages;
    public final java.util.List<String> stop;
    public final boolean stream;



    public ChatRequest(String model, String message) {
      this(model, java.util.Collections.singletonList(new Message("user", message)), null, false);
    }

    public ChatRequest(String model, String message, boolean stream) {
      this(model, java.util.Collections.singletonList(new Message("user", message)), null, stream);
    }

    public ChatRequest(String model, java.util.List<Message> messages, java.util.List<String> stop, boolean stream) {
      this.model = model;
      this.messages = messages;
      this.stop = stop;
      this.stream = stream;
    }
  }

  public static final class Message {
    public final String role;
    public final Object content;

    public Message(String role, String content) {
      this.role = role;
      this.content = content;
    }

    public Message(String role, java.util.List<java.util.Map<String, Object>> content) {
      this.role = role;
      this.content = content;
    }
  }

  private static final class EmbeddingRequest {
    public final String model;
    public final String input;

    private EmbeddingRequest(String model, String input) {
      this.model = model;
      this.input = input;
    }
  }
}

