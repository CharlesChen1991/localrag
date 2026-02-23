package local.ai.server.etl;

import java.io.BufferedInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TextExtractors {
  private TextExtractors() {}

  public static String readText(Path path, long maxBytes) {
    long size;
    try {
      size = Files.size(path);
    } catch (Exception e) {
      return "";
    }

    long toRead = Math.min(size, Math.max(0L, maxBytes));
    if (toRead == 0L) {
      return "";
    }
    byte[] buf = new byte[(int) toRead];
    try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(path))) {
      int read = in.read(buf);
      if (read <= 0) {
        return "";
      }
      Charset cs = StandardCharsets.UTF_8;
      String text = new String(buf, 0, read, cs);
      if (size > maxBytes) {
        text = text + "\n\n[TRUNCATED] file_size=" + size + " max_bytes=" + maxBytes;
      }
      return text;
    } catch (Exception e) {
      return "";
    }
  }
}

