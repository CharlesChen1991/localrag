package local.ai.server.etl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executors;
import com.fasterxml.jackson.databind.JsonNode;
import local.ai.server.config.AppConfig;
import local.ai.server.config.YamlConfigLoader;
import local.ai.server.db.SqliteStore;
import local.ai.server.index.ElasticsearchSink;
import local.ai.server.index.MilvusVectorSink;
import local.ai.server.util.Hashing;
import local.ai.server.util.TextChunker;
import local.ai.server.util.OpenAiCompatibleClient;
import local.ai.server.util.OpenAiCompatibleClient.Message;
import local.ai.shared.Json;
import local.ai.shared.model.ChatResponse;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.MediaType;

public final class EtlService {
  private final AppConfig config;
  private final SqliteStore store;
  private final MilvusVectorSink milvus;
  private final ElasticsearchSink es;
  private final ExecutorService workers;
  private final EmbeddingService embedding;
  private final AtomicBoolean stopped = new AtomicBoolean(false);
  private final YamlConfigLoader yaml;
  private final OkHttpClient httpClient = new OkHttpClient();

  public EtlService(AppConfig config, SqliteStore store, MilvusVectorSink milvus, ElasticsearchSink es, YamlConfigLoader yaml) {
    this.config = config;
    this.store = store;
    this.milvus = milvus;
    this.es = es;
    this.yaml = yaml;
    this.workers = Executors.newFixedThreadPool(2);
    this.embedding = new EmbeddingService(config.llm, config.milvus.dim);

    for (int i = 0; i < 2; i++) {
      workers.submit(this::runWorker);
    }
  }

  public void submitUpsert(Path path) {
    store.enqueueJob("upsert", path);
  }

  public void submitDelete(Path path) {
    store.enqueueJob("delete", path);
  }

  private void runWorker() {
    while (!stopped.get()) {
      SqliteStore.JobRow job = store.claimNextJob();
      if (job == null) {
        try {
          Thread.sleep(200);
        } catch (InterruptedException e) {
          return;
        }
        continue;
      }
      boolean ok = false;
      String err = "";
      try {
        Path p = Paths.get(job.path);
        if ("delete".equals(job.type)) {
          delete(p);
        } else {
          upsert(p);
        }
        ok = true;
      } catch (Exception e) {
        err = String.valueOf(e.getMessage());
      }
      try {
        store.finishJob(job.jobId, ok, err);
      } catch (Exception ignored) {
      }
    }
  }

  private void upsert(Path path) {
    if (!Files.isRegularFile(path)) {
      return;
    }
    String ext = extLower(path);
    boolean isText = config.etl.textExt.contains(ext);
    boolean isImage = config.etl.imageExt.contains(ext);
    boolean isVideo = config.etl.videoExt.contains(ext);
    if (!isText && !isImage && !isVideo) {
      return;
    }

    String fileId = Hashing.sha256Hex(path.toAbsolutePath().normalize().toString());
    long size;
    long mtime;
    try {
      size = Files.size(path);
      mtime = Files.getLastModifiedTime(path).toMillis();
    } catch (Exception e) {
      return;
    }
    SqliteStore.FileMeta prev = store.getFileMeta(fileId);
    if (prev != null && prev.size == size && prev.mtime == mtime) {
      return;
    }
    List<String> chunks;
    if (isText) {
      String content = TextExtractors.readText(path, config.etl.maxTextBytes);
      chunks = TextChunker.chunkByParagraph(content, config.etl.chunkMaxChars);
    } else if (isImage) {
      chunks = Collections.singletonList("[IMAGE] " + path.getFileName() + "\npath=" + path.toAbsolutePath().normalize());
    } else {
      chunks = Collections.singletonList("[VIDEO] " + path.getFileName() + "\npath=" + path.toAbsolutePath().normalize());
    }

    store.upsertFileAndChunks(fileId, path, size, mtime, chunks);

    List<MilvusVectorSink.ChunkVector> vectors = embedding.embedAll(fileId, path, chunks);
    milvus.upsertFile(fileId, vectors);

    List<String> chunkIds = vectors.stream().map(v -> v.chunkId).collect(Collectors.toList());
    es.upsertFile(fileId, path.toAbsolutePath().normalize().toString(), chunks, chunkIds);
  }

  private void delete(Path path) {
    String p = path.toAbsolutePath().normalize().toString();
    String fileId = Hashing.sha256Hex(p);
    store.deleteByPath(path);
    milvus.deleteByFileId(fileId);
    es.deleteByFileId(fileId);
  }

  public ChatAnswer chat(String message, int recallTopK) {
    List<SqliteStore.ChunkRow> recall = ragRecall(message, recallTopK);
    
    StringBuilder prompt = new StringBuilder();
    prompt.append("请根据以下参考文档回答用户的问题。如果参考文档中没有答案，请根据你的知识回答。请使用与用户问题相同的语言（中文或英文）回答。\n\n");
    for (int i = 0; i < recall.size(); i++) {
      prompt.append("参考文档 [").append(i+1).append("]:\n").append(recall.get(i).content).append("\n\n");
    }
    prompt.append("用户问题: ").append(message).append("\n");
    prompt.append("回答:");
    
    String answer = "";
    if (config.llm.hasRemoteChat()) {
      local.ai.server.util.OpenAiCompatibleClient client = new local.ai.server.util.OpenAiCompatibleClient(config.llm.baseUrl, config.llm.apiKey);
      answer = client.chat(config.llm.chatModel, prompt.toString());
    } else {
      answer = "LLM not configured. Please configure llm.baseUrl, apiKey and chatModel in app.yml.";
    }

    return ChatAnswer.from(message, recall, answer);
  }

  public void chatStream(String agentId, String message, int recallTopK, java.util.function.Consumer<String> onToken, java.util.function.Consumer<ChatAnswer> onComplete) {
    if (agentId == null || agentId.isEmpty()) {
       legacyChatStream(message, recallTopK, onToken, onComplete);
       return;
    }
    
    try {
      SqliteStore.AgentDetailRow agent = store.getAgent(agentId);
      List<Message> history = new ArrayList<>();
      List<ChatResponse.Citation> allCitations = new ArrayList<>();
      
      StringBuilder toolsDesc = new StringBuilder();
      toolsDesc.append("rag_search: Search internal knowledge base. Input: {\"query\": \"string\"}\n");
      
      Map<String, Map<String, Object>> enabledSkills = new HashMap<>();
      for (String f : agent.skillFiles) {
        Map<String, Object> skill = yaml.getSkill(f);
        if (!skill.isEmpty()) {
           String name = (String) skill.getOrDefault("name", "");
           String desc = (String) skill.getOrDefault("description", "");
           if (!name.isEmpty()) {
             toolsDesc.append(name).append(": ").append(desc).append(". Input: JSON object matching schema.\n");
             enabledSkills.put(name, skill);
           }
        }
      }
      
      StringBuilder rulesDesc = new StringBuilder();
      for (String f : agent.systemRuleFiles) {
         Map<String, Object> rule = yaml.getSystemRule(f);
         if (!rule.isEmpty()) {
            rulesDesc.append("- ").append(rule.getOrDefault("name", "")).append(": ").append(rule.getOrDefault("content", "")).append("\n");
         }
      }

      String systemPrompt = "You are an AI assistant using the ReAct pattern.\n" +
          "You have access to the following tools:\n" +
          toolsDesc.toString() + "\n" +
          "You must follow these rules:\n" +
          rulesDesc.toString() + "\n" +
          "You MUST use a tool if the user asks a question that requires external knowledge (like weather, current events, or specific data). Do not answer directly.\n" +
          "Use the following format:\n" +
          "Question: the input question you must answer\n" +
          "Thought: you should always think about what to do\n" +
          "Action: the action to take, should be one of [rag_search" + (enabledSkills.isEmpty() ? "" : ", " + String.join(", ", enabledSkills.keySet())) + "]\n" +
          "Action Input: the input to the action\n" +
          "Observation: the result of the action\n" +
          "... (this Thought/Action/Action Input/Observation can repeat N times)\n" +
          "Thought: I now know the final answer\n" +
          "Final Answer: the final answer to the original input question\n\n" + 
          "Begin!";

      history.add(new Message("system", systemPrompt));
      history.add(new Message("user", "Question: " + message));

      OpenAiCompatibleClient client = new OpenAiCompatibleClient(config.llm.baseUrl, config.llm.apiKey);
      List<String> stop = Collections.singletonList("Observation:");
      
      StringBuilder finalAnswer = new StringBuilder();
      int maxSteps = 8;
      
      for (int i = 0; i < maxSteps; i++) {
         StringBuilder stepOutput = new StringBuilder();
         client.chatStream(config.llm.chatModel, history, stop, token -> {
            stepOutput.append(token);
            onToken.accept(token);
         });
         
         String out = stepOutput.toString();
         history.add(new Message("assistant", out));
         
         if (out.contains("Final Answer:")) {
            int idx = out.indexOf("Final Answer:");
            finalAnswer.append(out.substring(idx + 13));
            break;
         }
         
         String action = extractAction(out);
         String actionInput = extractActionInput(out);
         
         if (action != null && actionInput != null) {
            String observation = executeTool(action, actionInput, enabledSkills, recallTopK, allCitations);
            String obsMsg = "\nObservation: " + observation + "\n";
            onToken.accept(obsMsg);
            history.add(new Message("user", obsMsg));
         } else {
            // No explicit action found, assume end of turn or malformed.
            // If it didn't say Final Answer, we might be stuck. 
            // Let's assume it's done for now.
            break;
         }
      }
      
      // Convert citations to ChunkRow logic if needed, but here we just pass list
      // We need to map ChatResponse.Citation back to SqliteStore.ChunkRow logic or update ChatAnswer
      // ChatAnswer expects List<SqliteStore.ChunkRow>.
      // We can reconstruct empty ChunkRows with paths.
      List<SqliteStore.ChunkRow> dummyRows = new ArrayList<>();
      for (ChatResponse.Citation c : allCitations) {
         dummyRows.add(new SqliteStore.ChunkRow(c.chunkId, c.path, ""));
      }
      
      onComplete.accept(ChatAnswer.from(message, dummyRows, finalAnswer.toString()));
      
    } catch (Exception e) {
      e.printStackTrace();
      onToken.accept("Error: " + e.getMessage());
      onComplete.accept(ChatAnswer.from(message, Collections.emptyList(), "Error: " + e.getMessage()));
    }
  }

  private String extractAction(String text) {
    Matcher m = Pattern.compile("Action:\\s*(.*)", Pattern.MULTILINE).matcher(text);
    if (m.find()) {
      return m.group(1).trim();
    }
    return null;
  }

  private String extractActionInput(String text) {
    Matcher m = Pattern.compile("Action Input:\\s*(.*)", Pattern.DOTALL).matcher(text);
    if (m.find()) {
      return m.group(1).trim();
    }
    return null;
  }

  private String executeTool(String name, String input, Map<String, Map<String, Object>> skills, int topK, List<ChatResponse.Citation> citations) {
    if ("rag_search".equals(name)) {
      try {
        JsonNode node = Json.mapper().readTree(input);
        String q = node.path("query").asText();
        if (q.isEmpty()) q = input; // fallback if input is just string
        List<SqliteStore.ChunkRow> rows = ragRecall(q, topK);
        StringBuilder sb = new StringBuilder();
        for (SqliteStore.ChunkRow r : rows) {
          sb.append("Path: ").append(r.path).append("\nContent: ").append(r.content).append("\n\n");
          citations.add(new ChatResponse.Citation(r.chunkId, r.path, "", ""));
        }
        return sb.length() == 0 ? "No results found." : sb.toString();
      } catch (Exception e) {
        return "Error executing rag_search: " + e.getMessage();
      }
    }
    
    if (skills.containsKey(name)) {
       return executeSkill(skills.get(name), input);
    }
    
    return "Unknown tool: " + name;
  }

  private String executeSkill(Map<String, Object> skill, String input) {
    try {
      Map<String, Object> executor = (Map<String, Object>) skill.get("executor");
      if (executor == null) return "No executor defined for skill";
      
      String type = (String) executor.get("type");
      if ("http".equals(type)) {
         String url = (String) executor.get("url");
         String method = (String) executor.getOrDefault("method", "GET");
         
         // Parse input JSON to replace placeholders in URL
         try {
           JsonNode node = Json.mapper().readTree(input);
           if (node.isObject()) {
             java.util.Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
             while (fields.hasNext()) {
               Map.Entry<String, JsonNode> field = fields.next();
               String key = field.getKey();
               String val = field.getValue().asText();
               url = url.replace("{" + key + "}", java.net.URLEncoder.encode(val, "UTF-8"));
             }
           }
         } catch (Exception ignored) {}
         
         Request.Builder rb = new Request.Builder().url(url);
         if ("POST".equalsIgnoreCase(method)) {
            rb.post(RequestBody.create(input, MediaType.parse("application/json")));
         } else {
            rb.get();
         }
         
         try (Response res = httpClient.newCall(rb.build()).execute()) {
            return res.body() != null ? res.body().string() : "Empty response";
         }
      }
      return "Unknown executor type: " + type;
    } catch (Exception e) {
      return "Skill execution failed: " + e.getMessage();
    }
  }

  private void legacyChatStream(String message, int recallTopK, java.util.function.Consumer<String> onToken, java.util.function.Consumer<ChatAnswer> onComplete) {
    List<SqliteStore.ChunkRow> recall = ragRecall(message, recallTopK);
    
    StringBuilder prompt = new StringBuilder();
    prompt.append("请根据以下参考文档回答用户的问题。如果参考文档中没有答案，请根据你的知识回答。请使用与用户问题相同的语言（中文或英文）回答。\n\n");
    for (int i = 0; i < recall.size(); i++) {
      prompt.append("参考文档 [").append(i+1).append("]:\n").append(recall.get(i).content).append("\n\n");
    }
    prompt.append("用户问题: ").append(message).append("\n");
    prompt.append("回答:");
    
    final StringBuilder fullAnswer = new StringBuilder();
    
    // Stream citations first
    StringBuilder citationText = new StringBuilder();
    citationText.append("**参考文档**：\n");
    for (SqliteStore.ChunkRow row : recall) {
      citationText.append("- ").append(row.path).append("\n");
    }
    citationText.append("\n");
    if (recall.isEmpty()) {
      citationText.append("（未找到相关文档）\n\n");
    }
    onToken.accept(citationText.toString());
    
    if (config.llm.hasRemoteChat()) {
       local.ai.server.util.OpenAiCompatibleClient client = new local.ai.server.util.OpenAiCompatibleClient(config.llm.baseUrl, config.llm.apiKey);
       java.util.List<Message> msgs = java.util.Collections.singletonList(new Message("user", prompt.toString()));
       client.chatStream(config.llm.chatModel, msgs, null, token -> {
         fullAnswer.append(token);
         onToken.accept(token);
       });
    } else {
       String msg = "LLM not configured. Please configure llm.baseUrl, apiKey and chatModel in app.yml.";
       fullAnswer.append(msg);
       onToken.accept(msg);
    }
    
    onComplete.accept(ChatAnswer.from(message, recall, fullAnswer.toString()));
  }

  public List<SqliteStore.ChunkRow> ragRecall(String query, int topK) {
    int k = Math.max(1, topK);
    if (config.milvus.enabled) {
      try {
        float[] qv = embedding.embedQuery(query);
        List<MilvusVectorSink.SearchHit> hits = milvus.search(qv, k);
        List<String> ids = new ArrayList<>();
        for (MilvusVectorSink.SearchHit h : hits) {
          ids.add(h.chunkId);
        }
        List<SqliteStore.ChunkRow> rows = store.listChunksByIds(ids);
        if (!rows.isEmpty()) {
          return rows;
        }
      } catch (Exception ignored) {
      }
    }
    return store.searchChunksLike(query, k);
  }

  public void stop() {
    stopped.set(true);
    workers.shutdownNow();
    milvus.close();
    es.close();
  }

  private static String extLower(Path path) {
    String name = path.getFileName().toString();
    int idx = name.lastIndexOf('.');
    if (idx < 0 || idx == name.length() - 1) {
      return "";
    }
    return name.substring(idx + 1).toLowerCase();
  }
}
