package local.ai.shared.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class DirectoryAddRequest {
  public final String path;

  @JsonCreator
  public DirectoryAddRequest(@JsonProperty("path") String path) {
    this.path = path;
  }
}

