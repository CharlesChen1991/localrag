package local.ai.server.etl;

import java.io.BufferedInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.Base64;
import local.ai.server.util.OpenAiCompatibleClient;
import local.ai.server.util.OpenAiCompatibleClient.Message;
import local.ai.server.config.AppConfig;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.probe.FFmpegStream;

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
    
    // Try to detect charset or just assume UTF-8 for now
    // In a real impl, we would use juniversalchardet or Tika
    try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(path))) {
      byte[] buf = new byte[(int) toRead];
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

  public static String extractVideoFrames(Path path, AppConfig config) {
     if (!config.llm.hasRemoteChat()) {
        return "[VIDEO_SKIPPED] No LLM configured for video analysis.\npath=" + path;
     }

     try {
       // 1. Get video duration and resolution using ffprobe
       FFprobe probe = new FFprobe("/opt/local/bin/ffprobe"); 
       FFmpegProbeResult result = probe.probe(path.toString());
       double duration = result.getFormat().duration;
       long size = result.getFormat().size;
       
       // 2. Extract key frames using ffmpeg
       int interval = 5;
       int maxFrames = 10;
       
       Path tempDir = Files.createTempDirectory("video_frames");
       try {
         FFmpeg ffmpeg = new FFmpeg("/opt/local/bin/ffmpeg");
         
         // Calculate number of frames based on duration, but cap at maxFrames
         int framesToExtract = Math.min(maxFrames, (int)(duration / interval) + 1);
         if (framesToExtract < 1) framesToExtract = 1;
         
         // We will extract frames at specific timestamps or just use a filter
         // A simple way is to use fps filter
         // ffmpeg -i input -vf fps=1/5,scale=512:-1 -vframes 10 out%d.jpg
         
         FFmpegBuilder builder = new FFmpegBuilder()
             .setInput(path.toString())
             .overrideOutputFiles(true)
             .addOutput(tempDir.resolve("frame_%03d.jpg").toString())
               .setVideoFilter("fps=1/" + interval + ",scale=512:-1")
               .setFrames(framesToExtract)
               .setVideoQuality(5) // 1-31, 31 is worst
               .done();
               
         net.bramp.ffmpeg.FFmpegExecutor executor = new net.bramp.ffmpeg.FFmpegExecutor(ffmpeg, probe);
         executor.createJob(builder).run();
         
         // 3. Read frames and send to LLM
         List<Path> frameFiles = Files.list(tempDir)
             .filter(p -> p.toString().endsWith(".jpg"))
             .sorted()
             .collect(Collectors.toList());
             
         if (frameFiles.isEmpty()) {
            return "[VIDEO_WARNING] No frames extracted.\npath=" + path;
         }
         
         OpenAiCompatibleClient client = new OpenAiCompatibleClient(config.llm.baseUrl, config.llm.apiKey);
         
         // Construct multimodal message
         // For DashScope qwen-vl-max/plus compatibility:
         // Content is list of {type: "text"|"image_url", ...}
         List<Map<String, Object>> contentParts = new ArrayList<>();
         
         contentParts.add(new HashMap<String, Object>() {{
           put("type", "text");
           put("text", "Please describe this video in detail based on these frames. Include information about the setting, people, actions, and any on-screen text.");
         }});
         
         for (Path framePath : frameFiles) {
            byte[] bytes = Files.readAllBytes(framePath);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String dataUrl = "data:image/jpeg;base64," + base64;
            
            contentParts.add(new HashMap<String, Object>() {{
              put("type", "image_url");
              put("image_url", new HashMap<String, String>() {{
                 put("url", dataUrl);
              }});
            }});
         }
         
         // Use a vision-capable model if configured, otherwise fallback to chatModel (which might fail if it's text-only)
         // For DashScope, usually "qwen-vl-max" or "qwen-vl-plus"
         String visionModel = "qwen-vl-max"; // Default fallback or use config
         if (config.llm.chatModel.contains("vl") || config.llm.chatModel.contains("vision")) {
            visionModel = config.llm.chatModel;
         }
         
         // Send request
         String description = client.chat(visionModel, Collections.singletonList(new Message("user", contentParts)));
         
         StringBuilder sb = new StringBuilder();
         sb.append("[VIDEO] ").append(path.getFileName()).append("\n");
         sb.append("Path: ").append(path.toAbsolutePath()).append("\n");
         sb.append("Duration: ").append(duration).append("s\n");
         sb.append("Analysis:\n").append(description).append("\n");
         
         return sb.toString();
         
       } finally {
         // Cleanup temp files
         try {
           Files.walk(tempDir)
               .sorted(Comparator.reverseOrder())
               .map(Path::toFile)
               .forEach(java.io.File::delete);
         } catch (Exception ignored) {}
       }

     } catch (Exception e) {
       System.err.println("Video extraction failed: " + e.getMessage());
       e.printStackTrace();
       return "[VIDEO_ERROR] " + path.getFileName() + "\n" + e.getMessage();
     }
  }
}

