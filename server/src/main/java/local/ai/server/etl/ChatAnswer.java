package local.ai.server.etl;

import java.util.ArrayList;
import java.util.List;
import local.ai.server.db.SqliteStore;
import local.ai.shared.model.ChatResponse;

public final class ChatAnswer {
  public final String answer;
  public final List<ChatResponse.Citation> citations;

  private ChatAnswer(String answer, List<ChatResponse.Citation> citations) {
    this.answer = answer;
    this.citations = citations;
  }

  public static ChatAnswer from(String message, List<SqliteStore.ChunkRow> recall, String answer) {
    StringBuilder sb = new StringBuilder();
    sb.append("**参考文档**：\n");
    List<ChatResponse.Citation> citations = new ArrayList<>();
    for (int i = 0; i < recall.size(); i++) {
      SqliteStore.ChunkRow row = recall.get(i);
      sb.append("- ").append(row.path).append("\n");
      citations.add(new ChatResponse.Citation(row.chunkId, row.path, "", ""));
    }
    sb.append("\n");
    if (recall.isEmpty()) {
      sb.append("（未找到相关文档）\n\n");
    }
    
    sb.append(answer);

    return new ChatAnswer(sb.toString(), citations);
  }
}

