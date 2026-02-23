package local.ai.server.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TextChunker {
  private TextChunker() {}

  public static List<String> chunkByParagraph(String text, int maxChars) {
    if (text == null || text.isEmpty()) {
      return Collections.emptyList();
    }
    String[] parts = text.split("\\n\\s*\\n");
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    for (String p : parts) {
      String trimmed = p.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      if (cur.length() == 0) {
        cur.append(trimmed);
        continue;
      }
      if (cur.length() + 2 + trimmed.length() <= maxChars) {
        cur.append("\n\n").append(trimmed);
      } else {
        out.add(cur.toString());
        cur.setLength(0);
        if (trimmed.length() <= maxChars) {
          cur.append(trimmed);
        } else {
          int i = 0;
          while (i < trimmed.length()) {
            int end = Math.min(i + maxChars, trimmed.length());
            out.add(trimmed.substring(i, end));
            i = end;
          }
        }
      }
    }
    if (cur.length() > 0) {
      out.add(cur.toString());
    }
    return out;
  }
}
