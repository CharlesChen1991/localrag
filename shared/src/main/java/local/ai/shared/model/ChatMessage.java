package local.ai.shared.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class ChatMessage {
  public final String role;
  public final String content;
  public final long createdAt;

  @JsonCreator
  public ChatMessage(
      @JsonProperty("role") String role,
      @JsonProperty("content") String content,
      @JsonProperty("createdAt") long createdAt
  ) {
    this.role = role;
    this.content = content;
    this.createdAt = createdAt;
  }
}

