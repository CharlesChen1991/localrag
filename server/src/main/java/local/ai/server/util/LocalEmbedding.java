package local.ai.server.util;

public final class LocalEmbedding {
  private LocalEmbedding() {}

  public static float[] embed(String text, int dim) {
    float[] v = new float[dim];
    if (text == null) {
      return v;
    }
    String s = text;
    int n = s.length();
    for (int i = 0; i < n; i++) {
      int h = 146959810;
      for (int k = 0; k < 3; k++) {
        int idx = i + k;
        if (idx >= n) {
          break;
        }
        h ^= s.charAt(idx);
        h *= 16777619;
      }
      int slot = (h & 0x7fffffff) % dim;
      v[slot] += 1.0f;
    }
    float norm = 0f;
    for (float x : v) {
      norm += x * x;
    }
    norm = (float) Math.sqrt(norm);
    if (norm > 0f) {
      for (int i = 0; i < v.length; i++) {
        v[i] /= norm;
      }
    }
    return v;
  }
}

