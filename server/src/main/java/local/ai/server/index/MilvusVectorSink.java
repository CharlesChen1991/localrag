package local.ai.server.index;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.DataType;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.collection.ReleaseCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.IndexType;
import io.milvus.grpc.SearchResults;
import io.milvus.response.SearchResultsWrapper;
import io.milvus.param.MetricType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import local.ai.server.config.MilvusConfig;

public final class MilvusVectorSink implements AutoCloseable {
  private final MilvusConfig config;
  private volatile MilvusServiceClient client;
  private volatile boolean ready;

  public MilvusVectorSink(MilvusConfig config) {
    this.config = config;
    if (config.enabled) {
      try {
        this.client = new MilvusServiceClient(ConnectParam.newBuilder().withHost(config.host).withPort(config.port).build());
        ensureCollection();
        this.ready = true;
      } catch (Exception e) {
        this.ready = false;
      }
    }
  }

  public void upsertFile(String fileId, List<ChunkVector> vectors) {
    if (!config.enabled || !ready || vectors.isEmpty()) {
      return;
    }
    deleteByFileId(fileId);
    insert(vectors);
  }

  public void deleteByFileId(String fileId) {
    if (!config.enabled || !ready) {
      return;
    }
    String expr = "file_id == \"" + escape(fileId) + "\"";
    client.delete(DeleteParam.newBuilder().withCollectionName(config.collection).withExpr(expr).build());
  }

  public List<SearchHit> search(float[] vector, int topK) {
    if (!config.enabled || !ready || vector == null || vector.length == 0) {
      return new ArrayList<>();
    }
    try {
      List<List<Float>> targets = new ArrayList<>();
      List<Float> row = new ArrayList<>(vector.length);
      for (float x : vector) {
        row.add(x);
      }
      targets.add(row);

      SearchParam param = SearchParam.newBuilder()
          .withCollectionName(config.collection)
          .withMetricType(MetricType.L2)
          .withTopK(Math.max(1, topK))
          .withVectors(targets)
          .withVectorFieldName("embedding")
          .withOutFields(Arrays.asList("path"))
          .withParams("{\"nprobe\":10}")
          .build();

      R<SearchResults> resp = client.search(param);
      if (resp.getStatus() != R.Status.Success.getCode()) {
        return new ArrayList<>();
      }
      SearchResultsWrapper wrapper = new SearchResultsWrapper(resp.getData().getResults());
      List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
      List<?> paths = (List<?>) wrapper.getFieldData("path", 0);

      List<SearchHit> out = new ArrayList<>();
      for (int i = 0; i < scores.size(); i++) {
        SearchResultsWrapper.IDScore s = scores.get(i);
        String id = s.getStrID();
        if (id == null || id.isEmpty()) {
          id = String.valueOf(s.getLongID());
        }
        String path = i < paths.size() ? String.valueOf(paths.get(i)) : "";
        out.add(new SearchHit(id, path, s.getScore()));
      }
      return out;
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  private void insert(List<ChunkVector> vectors) {
    List<String> chunkIds = new ArrayList<>();
    List<String> fileIds = new ArrayList<>();
    List<String> paths = new ArrayList<>();
    List<List<Float>> embed = new ArrayList<>();
    for (ChunkVector v : vectors) {
      chunkIds.add(v.chunkId);
      fileIds.add(v.fileId);
      paths.add(v.path);
      List<Float> row = new ArrayList<>(v.vector.length);
      for (float x : v.vector) {
        row.add(x);
      }
      embed.add(row);
    }

    List<InsertParam.Field> fields = new ArrayList<>();
    fields.add(new InsertParam.Field("chunk_id", chunkIds));
    fields.add(new InsertParam.Field("file_id", fileIds));
    fields.add(new InsertParam.Field("path", paths));
    fields.add(new InsertParam.Field("embedding", embed));

    R<MutationResult> res = client.insert(InsertParam.newBuilder().withCollectionName(config.collection).withFields(fields).build());
    if (res.getStatus() != R.Status.Success.getCode()) {
      ready = false;
    }
  }

  private void ensureCollection() {
    R<Boolean> has = client.hasCollection(HasCollectionParam.newBuilder().withCollectionName(config.collection).build());
    if (has.getStatus() == R.Status.Success.getCode() && Boolean.TRUE.equals(has.getData())) {
      client.releaseCollection(ReleaseCollectionParam.newBuilder().withCollectionName(config.collection).build());
      R<RpcStatus> indexR = client.createIndex(CreateIndexParam.newBuilder()
          .withCollectionName(config.collection)
          .withFieldName("embedding")
          .withIndexType(IndexType.IVF_FLAT)
          .withMetricType(MetricType.L2)
          .withExtraParam("{\"nlist\":1024}")
          .withSyncMode(Boolean.TRUE)
          .build());
      if (indexR.getStatus() != R.Status.Success.getCode()) {
        System.err.println("Milvus createIndex failed: " + indexR.getMessage());
      }
      R<RpcStatus> loadR = client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(config.collection).build());
      if (loadR.getStatus() != R.Status.Success.getCode()) {
        System.err.println("Milvus loadCollection failed: " + loadR.getMessage());
      }
      return;
    }

    List<FieldType> fields = new ArrayList<>();
    fields.add(FieldType.newBuilder().withName("chunk_id").withDataType(DataType.VarChar).withMaxLength(256).withPrimaryKey(true).withAutoID(false).build());
    fields.add(FieldType.newBuilder().withName("file_id").withDataType(DataType.VarChar).withMaxLength(256).build());
    fields.add(FieldType.newBuilder().withName("path").withDataType(DataType.VarChar).withMaxLength(1024).build());
    fields.add(FieldType.newBuilder().withName("embedding").withDataType(DataType.FloatVector).withDimension(config.dim).build());

    R<RpcStatus> created = client.createCollection(CreateCollectionParam.newBuilder().withCollectionName(config.collection).withDescription("ai assistant prototype").withFieldTypes(fields).build());
    if (created.getStatus() != R.Status.Success.getCode()) {
      throw new RuntimeException(created.getMessage());
    }

    R<RpcStatus> indexR = client.createIndex(CreateIndexParam.newBuilder()
        .withCollectionName(config.collection)
        .withFieldName("embedding")
        .withIndexType(IndexType.IVF_FLAT)
        .withMetricType(MetricType.L2)
        .withExtraParam("{\"nlist\":1024}")
        .withSyncMode(Boolean.TRUE)
        .build());
    if (indexR.getStatus() != R.Status.Success.getCode()) {
      throw new RuntimeException(indexR.getMessage());
    }

    client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(config.collection).build());
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  @Override
  public void close() {
    try {
      if (client != null) {
        client.close();
      }
    } catch (Exception ignored) {
    }
  }

  public static final class ChunkVector {
    public final String chunkId;
    public final String fileId;
    public final String path;
    public final float[] vector;

    public ChunkVector(String chunkId, String fileId, String path, float[] vector) {
      this.chunkId = chunkId;
      this.fileId = fileId;
      this.path = path;
      this.vector = vector;
    }
  }

  public static final class SearchHit {
    public final String chunkId;
    public final String path;
    public final float score;

    public SearchHit(String chunkId, String path, float score) {
      this.chunkId = chunkId;
      this.path = path;
      this.score = score;
    }
  }
}
