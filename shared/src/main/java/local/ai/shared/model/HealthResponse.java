package local.ai.shared.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class HealthResponse {
  public final String status;

  @JsonCreator
  public HealthResponse(@JsonProperty("status") String status) {
    this.status = status;
  }
}

