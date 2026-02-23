package local.ai.server.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class EtlConfig {
  public final long maxTextBytes;
  public final int chunkMaxChars;
  public final List<String> textExt;
  public final List<String> imageExt;
  public final List<String> videoExt;

  private EtlConfig(long maxTextBytes, int chunkMaxChars, List<String> textExt, List<String> imageExt, List<String> videoExt) {
    this.maxTextBytes = maxTextBytes;
    this.chunkMaxChars = chunkMaxChars;
    this.textExt = textExt;
    this.imageExt = imageExt;
    this.videoExt = videoExt;
  }

  @SuppressWarnings("unchecked")
  public static EtlConfig from(Map<String, Object> raw) {
    long maxTextBytes = asLong(raw.getOrDefault("maxTextBytes", 2_000_000L), 2_000_000L);
    int chunkMaxChars = asInt(raw.getOrDefault("chunkMaxChars", 1200), 1200);

    List<String> textExt = (List<String>) raw.get("textExt");
    if (textExt == null || textExt.isEmpty()) {
      textExt = Arrays.asList("txt", "md", "java", "py", "js", "ts", "json", "yaml", "yml", "html", "css", "csv");
    }
    List<String> imageExt = (List<String>) raw.get("imageExt");
    if (imageExt == null || imageExt.isEmpty()) {
      imageExt = Arrays.asList("png", "jpg", "jpeg", "webp", "gif");
    }
    List<String> videoExt = (List<String>) raw.get("videoExt");
    if (videoExt == null || videoExt.isEmpty()) {
      videoExt = Arrays.asList("mp4", "mov", "mkv", "avi");
    }

    textExt = Collections.unmodifiableList(textExt);
    imageExt = Collections.unmodifiableList(imageExt);
    videoExt = Collections.unmodifiableList(videoExt);
    return new EtlConfig(maxTextBytes, chunkMaxChars, textExt, imageExt, videoExt);
  }

  private static long asLong(Object value, long fallback) {
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (Exception e) {
      return fallback;
    }
  }

  private static int asInt(Object value, int fallback) {
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (Exception e) {
      return fallback;
    }
  }
}
