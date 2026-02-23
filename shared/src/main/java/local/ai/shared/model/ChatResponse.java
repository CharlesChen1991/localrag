package local.ai.shared.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class ChatResponse {
  public final String sessionId;
  public final String answer;
  public final List<Citation> citations;

  @JsonCreator
  public ChatResponse(
      @JsonProperty("sessionId") String sessionId,
      @JsonProperty("answer") String answer,
      @JsonProperty("citations") List<Citation> citations
  ) {
    this.sessionId = sessionId;
    this.answer = answer;
    this.citations = citations;
  }

  public static final class Citation {
    public final String chunkId;
    public final String path;
    public final String startPos;
    public final String endPos;

    @JsonCreator
    public Citation(
        @JsonProperty("chunkId") String chunkId,
        @JsonProperty("path") String path,
        @JsonProperty("startPos") String startPos,
        @JsonProperty("endPos") String endPos
    ) {
      this.chunkId = chunkId;
      this.path = path;
      this.startPos = startPos;
      this.endPos = endPos;
    }
  }
}

