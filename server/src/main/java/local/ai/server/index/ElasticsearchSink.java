package local.ai.server.index;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import local.ai.server.config.EsConfig;
import local.ai.shared.Json;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.MediaType;
import okhttp3.Credentials;

public final class ElasticsearchSink implements AutoCloseable {
  private final EsConfig config;
  private final OkHttpClient http;
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
  private volatile boolean ready;

  public ElasticsearchSink(EsConfig config) {
    this.config = config;
    this.http = new OkHttpClient();
    if (config.enabled && !config.url.isEmpty()) {
      ensureIndex();
      this.ready = true;
    }
  }

  public void upsertFile(String fileId, String path, List<String> chunks, List<String> chunkIds) {
    if (!config.enabled || !ready) return;
    
    deleteByFileId(fileId);
    
    if (chunks.isEmpty()) return;

    StringBuilder bulk = new StringBuilder();
    for (int i = 0; i < chunks.size(); i++) {
      String chunkId = chunkIds.get(i);
      String content = chunks.get(i);
      
      Map<String, Object> meta = new HashMap<>();
      meta.put("_index", "rag_chunks");
      meta.put("_id", chunkId);
      
      Map<String, Object> action = new HashMap<>();
      action.put("index", meta);
      
      Map<String, Object> doc = new HashMap<>();
      doc.put("chunk_id", chunkId);
      doc.put("file_id", fileId);
      doc.put("path", path);
      doc.put("content", content);
      
      bulk.append(Json.toJson(action)).append("\n");
      bulk.append(Json.toJson(doc)).append("\n");
    }
    
    String url = url("/_bulk");
    Request.Builder req = new Request.Builder()
        .url(url)
        .post(RequestBody.create(bulk.toString(), JSON));
        
    auth(req);
    
    try (Response res = http.newCall(req.build()).execute()) {
      // ignore response for now, maybe log error
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void deleteByFileId(String fileId) {
    if (!config.enabled || !ready) return;
    
    String query = "{\"query\": {\"term\": {\"file_id\": \"" + fileId + "\"}}}";
    String url = url("/rag_chunks/_delete_by_query");
    Request.Builder req = new Request.Builder()
        .url(url)
        .post(RequestBody.create(query, JSON));
    
    auth(req);
    
    try (Response res = http.newCall(req.build()).execute()) {
      // ignore
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void ensureIndex() {
    if (!exists("rag_chunks")) {
      createIndex();
    }
  }

  private boolean exists(String index) {
    String url = url("/" + index);
    Request.Builder req = new Request.Builder().url(url).head();
    auth(req);
    try (Response res = http.newCall(req.build()).execute()) {
      return res.isSuccessful();
    } catch (IOException e) {
      return false;
    }
  }

  private void createIndex() {
    String mapping = "{\n" +
        "  \"mappings\": {\n" +
        "    \"properties\": {\n" +
        "      \"chunk_id\": { \"type\": \"keyword\" },\n" +
        "      \"file_id\": { \"type\": \"keyword\" },\n" +
        "      \"path\": { \"type\": \"keyword\" },\n" +
        "      \"content\": { \"type\": \"text\", \"analyzer\": \"standard\" }\n" +
        "    }\n" +
        "  }\n" +
        "}";
    
    String url = url("/rag_chunks");
    Request.Builder req = new Request.Builder()
        .url(url)
        .put(RequestBody.create(mapping, JSON));
    auth(req);
    
    try (Response res = http.newCall(req.build()).execute()) {
      if (!res.isSuccessful()) {
        System.err.println("Failed to create ES index: " + res.code() + " " + res.message());
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private String url(String path) {
    String base = config.url;
    if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
    return base + path;
  }

  private void auth(Request.Builder req) {
    if (config.username != null && !config.username.isEmpty()) {
      req.header("Authorization", Credentials.basic(config.username, config.password));
    }
  }

  @Override
  public void close() {
    // OkHttpClient usually doesn't need explicit close, but we can shutdown dispatcher if needed
    http.dispatcher().executorService().shutdown();
  }
}
