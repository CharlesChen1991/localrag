package local.ai.shared.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class ChatRequest {
  public final String sessionId;
  public final String message;

  @JsonCreator
  public ChatRequest(
      @JsonProperty("sessionId") String sessionId,
      @JsonProperty("message") String message
  ) {
    this.sessionId = sessionId;
    this.message = message;
  }
}

