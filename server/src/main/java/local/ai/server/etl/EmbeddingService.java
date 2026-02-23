package local.ai.server.etl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import local.ai.server.config.LlmConfig;
import local.ai.server.index.MilvusVectorSink;
import local.ai.server.util.LocalEmbedding;
import local.ai.server.util.OpenAiCompatibleClient;

public final class EmbeddingService {
  private final LlmConfig llm;
  private final int dim;
  private final OpenAiCompatibleClient client;

  public EmbeddingService(LlmConfig llm, int dim) {
    this.llm = llm;
    this.dim = dim;
    this.client = new OpenAiCompatibleClient(llm.baseUrl, llm.apiKey);
  }

  public List<MilvusVectorSink.ChunkVector> embedAll(String fileId, Path path, List<String> chunks) {
    List<MilvusVectorSink.ChunkVector> out = new ArrayList<>();
    for (int i = 0; i < chunks.size(); i++) {
      String chunkId = fileId + ":" + i;
      float[] vec;
      if (llm.hasRemoteEmbedding()) {
        vec = client.embed(llm.embeddingModel, chunks.get(i), dim);
      } else {
        vec = LocalEmbedding.embed(chunks.get(i), dim);
      }
      out.add(new MilvusVectorSink.ChunkVector(chunkId, fileId, path.toAbsolutePath().normalize().toString(), vec));
    }
    return out;
  }

  public float[] embedQuery(String text) {
    String t = text == null ? "" : text;
    if (llm.hasRemoteEmbedding()) {
      return client.embed(llm.embeddingModel, t, dim);
    }
    return LocalEmbedding.embed(t, dim);
  }
}
