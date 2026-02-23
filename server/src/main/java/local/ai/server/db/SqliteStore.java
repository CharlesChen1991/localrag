package local.ai.server.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import local.ai.shared.Json;

public final class SqliteStore implements AutoCloseable {
  private final Path dbFile;
  private Connection conn;

  public SqliteStore(Path dbFile) {
    this.dbFile = dbFile;
  }

  public void init() {
    try {
      Path parent = dbFile.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    try {
      conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
      try (Statement st = conn.createStatement()) {
        st.execute("PRAGMA journal_mode=WAL");
        st.execute("CREATE TABLE IF NOT EXISTS directories (id INTEGER PRIMARY KEY AUTOINCREMENT, path TEXT UNIQUE NOT NULL)");
        st.execute("CREATE TABLE IF NOT EXISTS app_state (k TEXT PRIMARY KEY, v TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        st.execute("CREATE TABLE IF NOT EXISTS jobs (job_id TEXT PRIMARY KEY, job_key TEXT UNIQUE NOT NULL, type TEXT NOT NULL, path TEXT NOT NULL, status TEXT NOT NULL, attempts INTEGER NOT NULL, last_error TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        st.execute("CREATE TABLE IF NOT EXISTS files (file_id TEXT PRIMARY KEY, path TEXT NOT NULL, size INTEGER NOT NULL DEFAULT 0, mtime INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)");
        st.execute("CREATE TABLE IF NOT EXISTS chunks (chunk_id TEXT PRIMARY KEY, file_id TEXT NOT NULL, path TEXT NOT NULL, chunk_index INTEGER NOT NULL, content TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        st.execute("CREATE TABLE IF NOT EXISTS chat_session (session_id TEXT PRIMARY KEY, title TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        st.execute("CREATE TABLE IF NOT EXISTS chat_message (message_id TEXT PRIMARY KEY, session_id TEXT NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, created_at INTEGER NOT NULL)");
        st.execute("CREATE TABLE IF NOT EXISTS agents (agent_id TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT, tags_json TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        st.execute("CREATE TABLE IF NOT EXISTS agent_skill (agent_id TEXT NOT NULL, skill_file TEXT NOT NULL, PRIMARY KEY(agent_id, skill_file))");
        st.execute("CREATE TABLE IF NOT EXISTS agent_rule (agent_id TEXT NOT NULL, kind TEXT NOT NULL, rule_file TEXT NOT NULL, PRIMARY KEY(agent_id, kind, rule_file))");
      }
      migrate();
      resetRunningJobs();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void migrate() {
    Map<String, Boolean> sessionCols = tableColumns("chat_session");
    if (!sessionCols.containsKey("agent_id")) {
      exec("ALTER TABLE chat_session ADD COLUMN agent_id TEXT NOT NULL DEFAULT ''");
    }
    Map<String, Boolean> fileCols = tableColumns("files");
    if (!fileCols.containsKey("size")) {
      exec("ALTER TABLE files ADD COLUMN size INTEGER NOT NULL DEFAULT 0");
    }
    if (!fileCols.containsKey("mtime")) {
      exec("ALTER TABLE files ADD COLUMN mtime INTEGER NOT NULL DEFAULT 0");
    }
  }

  private Map<String, Boolean> tableColumns(String table) {
    Map<String, Boolean> out = new LinkedHashMap<>();
    try (PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(" + table + ")")) {
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.put(rs.getString("name"), true);
        }
      }
    } catch (Exception ignored) {
    }
    return out;
  }

  private void exec(String sql) {
    try (Statement st = conn.createStatement()) {
      st.execute(sql);
    } catch (Exception ignored) {
    }
  }

  public synchronized void resetRunningJobs() {
    long now = System.currentTimeMillis();
    try (PreparedStatement ps = conn.prepareStatement("UPDATE jobs SET status = 'pending', updated_at = ? WHERE status = 'running'")) {
      ps.setLong(1, now);
      ps.executeUpdate();
    } catch (Exception ignored) {
    }
  }

  public synchronized void enqueueJob(String type, Path path) {
    String p = path.toAbsolutePath().normalize().toString();
    String jobKey = type + ":" + p;
    long now = System.currentTimeMillis();
    try (PreparedStatement upd = conn.prepareStatement("UPDATE jobs SET status = 'pending', updated_at = ?, last_error = '' WHERE job_key = ?")) {
      upd.setLong(1, now);
      upd.setString(2, jobKey);
      int n = upd.executeUpdate();
      if (n > 0) {
        return;
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    String jobId = UUID.randomUUID().toString();
    try (PreparedStatement ins = conn.prepareStatement(
        "INSERT INTO jobs(job_id, job_key, type, path, status, attempts, last_error, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)")) {
      ins.setString(1, jobId);
      ins.setString(2, jobKey);
      ins.setString(3, type);
      ins.setString(4, p);
      ins.setString(5, "pending");
      ins.setInt(6, 0);
      ins.setString(7, "");
      ins.setLong(8, now);
      ins.setLong(9, now);
      ins.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public synchronized JobRow claimNextJob() {
    long now = System.currentTimeMillis();
    try {
      conn.setAutoCommit(false);
      JobRow row = null;
      try (PreparedStatement ps = conn.prepareStatement(
          "SELECT job_id, type, path, attempts FROM jobs WHERE status = 'pending' ORDER BY created_at ASC LIMIT 1")) {
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            String jobId = rs.getString(1);
            String type = rs.getString(2);
            String path = rs.getString(3);
            int attempts = rs.getInt(4);
            try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE jobs SET status = 'running', attempts = ?, updated_at = ? WHERE job_id = ? AND status = 'pending'")) {
              upd.setInt(1, attempts + 1);
              upd.setLong(2, now);
              upd.setString(3, jobId);
              int n = upd.executeUpdate();
              if (n > 0) {
                row = new JobRow(jobId, type, path, attempts + 1);
              }
            }
          }
        }
      }
      conn.commit();
      conn.setAutoCommit(true);
      return row;
    } catch (Exception e) {
      try {
        conn.rollback();
      } catch (Exception ignored) {
      }
      try {
        conn.setAutoCommit(true);
      } catch (Exception ignored) {
      }
      throw new RuntimeException(e);
    }
  }

  public synchronized void finishJob(String jobId, boolean ok, String error) {
    long now = System.currentTimeMillis();
    String status = ok ? "done" : "failed";
    String err = error == null ? "" : error;
    try (PreparedStatement ps = conn.prepareStatement(
        "UPDATE jobs SET status = ?, last_error = ?, updated_at = ? WHERE job_id = ?")) {
      ps.setString(1, status);
      ps.setString(2, err);
      ps.setLong(3, now);
      ps.setString(4, jobId);
      ps.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public synchronized List<JobStatusRow> listJobs(int limit) {
    List<JobStatusRow> out = new ArrayList<>();
    try (PreparedStatement ps = conn.prepareStatement(
        "SELECT job_id, type, path, status, attempts, last_error, created_at, updated_at FROM jobs ORDER BY updated_at DESC LIMIT ?")) {
      ps.setInt(1, limit);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(new JobStatusRow(
              rs.getString(1),
              rs.getString(2),
              rs.getString(3),
              rs.getString(4),
              rs.getInt(5),
              rs.getString(6),
              rs.getLong(7),
              rs.getLong(8)
          ));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return out;
  }

  public synchronized AgentSummaryRow createAgent(String name, String description, List<String> tags, List<String> skillFiles, List<String> systemRuleFiles, List<String> triggerRuleFiles) {
    String agentId = UUID.randomUUID().toString();
    long now = System.currentTimeMillis();
    String tagsJson = toJsonArray(tags);
    try (PreparedStatement ps = conn.prepareStatement(
        "INSERT INTO agents(agent_id, name, description, tags_json, created_at, updated_at) VALUES (?,?,?,?,?,?)")) {
      ps.setString(1, agentId);
      ps.setString(2, name);
      ps.setString(3, description == null ? "" : description);
      ps.setString(4, tagsJson);
      ps.setLong(5, now);
      ps.setLong(6, now);
      ps.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    replaceAgentBindings(agentId, skillFiles, systemRuleFiles, triggerRuleFiles);
    return getAgentSummary(agentId);
  }

  public synchronized void updateAgent(String agentId, String name, String description, List<String> tags, List<String> skillFiles, List<String> systemRuleFiles, List<String> triggerRuleFiles) {
    long now = System.currentTimeMillis();
    String tagsJson = toJsonArray(tags);
    try (PreparedStatement ps = conn.prepareStatement(
        "UPDATE agents SET name = ?, description = ?, tags_json = ?, updated_at = ? WHERE agent_id = ?")) {
      ps.setString(1, name);
      ps.setString(2, description == null ? "" : description);
      ps.setString(3, tagsJson);
      ps.setLong(4, now);
      ps.setString(5, agentId);
      int n = ps.executeUpdate();
      if (n == 0) {
        throw new IllegalArgumentException("agent not found");
      }
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    replaceAgentBindings(agentId, skillFiles, systemRuleFiles, triggerRuleFiles);
  }

  public synchronized void deleteAgent(String agentId) {
    try (PreparedStatement ps1 = conn.prepareStatement("DELETE FROM agent_skill WHERE agent_id = ?")) {
      ps1.setString(1, agentId);
      ps1.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    try (PreparedStatement ps2 = conn.prepareStatement("DELETE FROM agent_rule WHERE agent_id = ?")) {
      ps2.setString(1, agentId);
      ps2.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM agents WHERE agent_id = ?")) {
      ps.setString(1, agentId);
      ps.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void replaceAgentBindings(String agentId, List<String> skillFiles, List<String> systemRuleFiles, List<String> triggerRuleFiles) {
    if (skillFiles == null) {
      skillFiles = new ArrayList<>();
    }
    if (systemRuleFiles == null) {
      systemRuleFiles = new ArrayList<>();
    }
    if (triggerRuleFiles == null) {
      triggerRuleFiles = new ArrayList<>();
    }

    try (PreparedStatement del = conn.prepareStatement("DELETE FROM agent_skill WHERE agent_id = ?")) {
      del.setString(1, agentId);
      del.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    try (PreparedStatement ins = conn.prepareStatement("INSERT OR IGNORE INTO agent_skill(agent_id, skill_file) VALUES (?,?)")) {
      for (String f : skillFiles) {
        if (f == null || f.trim().isEmpty()) {
          continue;
        }
        ins.setString(1, agentId);
        ins.setString(2, f.trim());
        ins.addBatch();
      }
      ins.executeBatch();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    try (PreparedStatement del = conn.prepareStatement("DELETE FROM agent_rule WHERE agent_id = ?")) {
      del.setString(1, agentId);
      del.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    try (PreparedStatement ins = conn.prepareStatement("INSERT OR IGNORE INTO agent_rule(agent_id, kind, rule_file) VALUES (?,?,?)")) {
      for (String f : systemRuleFiles) {
        if (f == null || f.trim().isEmpty()) {
          continue;
        }
        ins.setString(1, agentId);
        ins.setString(2, "system");
        ins.setString(3, f.trim());
        ins.addBatch();
      }
      for (String f : triggerRuleFiles) {
        if (f == null || f.trim().isEmpty()) {
          continue;
        }
        ins.setString(1, agentId);
        ins.setString(2, "trigger");
        ins.setString(3, f.trim());
        ins.addBatch();
      }
      ins.executeBatch();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public synchronized List<AgentSummaryRow> listAgents(int limit) {
    List<AgentSummaryRow> out = new ArrayList<>();
    try (PreparedStatement ps = conn.prepareStatement(
        "SELECT agent_id, name, description, tags_json, created_at, updated_at FROM agents ORDER BY updated_at DESC LIMIT ?")) {
      ps.setInt(1, limit);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          String agentId = rs.getString(1);
          int skillCount = count("SELECT COUNT(*) FROM agent_skill WHERE agent_id = ?", agentId);
          int ruleCount = count("SELECT COUNT(*) FROM agent_rule WHERE agent_id = ?", agentId);
          out.add(new AgentSummaryRow(
              agentId,
              rs.getString(2),
              rs.getString(3),
              parseJsonArray(rs.getString(4)),
              skillCount,
              ruleCount,
              rs.getLong(5),
              rs.getLong(6)
          ));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return out;
  }

  public synchronized AgentDetailRow getAgent(String agentId) {
    try (PreparedStatement ps = conn.prepareStatement(
        "SELECT agent_id, name, description, tags_json, created_at, updated_at FROM agents WHERE agent_id = ?")) {
      ps.setString(1, agentId);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          throw new IllegalArgumentException("agent not found");
        }
        List<String> skillFiles = listStrings("SELECT skill_file FROM agent_skill WHERE agent_id = ? ORDER BY skill_file ASC", agentId);
        List<String> systemRuleFiles = listStrings(
            "SELECT rule_file FROM agent_rule WHERE agent_id = ? AND kind = 'system' ORDER BY rule_file ASC", agentId);
        List<String> triggerRuleFiles = listStrings(
            "SELECT rule_file FROM agent_rule WHERE agent_id = ? AND kind = 'trigger' ORDER BY rule_file ASC", agentId);
        return new AgentDetailRow(
            rs.getString(1),
            rs.getString(2),
            rs.getString(3),
            parseJsonArray(rs.getString(4)),
            skillFiles,
            systemRuleFiles,
            triggerRuleFiles,
            rs.getLong(5),
            rs.getLong(6)
        );
      }
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public synchronized AgentSummaryRow getAgentSummary(String agentId) {
    try (PreparedStatement ps = conn.prepareStatement(
        "SELECT agent_id, name, description, tags_json, created_at, updated_at FROM agents WHERE agent_id = ?")) {
      ps.setString(1, agentId);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          throw new IllegalArgumentException("agent not found");
        }
        int skillCount = count("SELECT COUNT(*) FROM agent_skill WHERE agent_id = ?", agentId);
        int ruleCount = count("SELECT COUNT(*) FROM agent_rule WHERE agent_id = ?", agentId);
        return new AgentSummaryRow(
            rs.getString(1),
            rs.getString(2),
            rs.getString(3),
            parseJsonArray(rs.getString(4)),
            skillCount,
            ruleCount,
            rs.getLong(5),
            rs.getLong(6)
        );
      }
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private int count(String sql, String agentId) {
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, agentId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return rs.getInt(1);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return 0;
  }

  private List<String> listStrings(String sql, String agentId) {
    List<String> out = new ArrayList<>();
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, agentId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(rs.getString(1));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return out;
  }

  private static String toJsonArray(List<String> tags) {
    if (tags == null) {
      tags = new ArrayList<>();
    }
    List<String> clean = new ArrayList<>();
    for (String t : tags) {
      if (t == null) {
        continue;
      }
      String s = t.trim();
      if (!s.isEmpty()) {
        clean.add(s);
      }
    }
    return Json.toJson(clean);
  }

  @SuppressWarnings("unchecked")
  private static List<String> parseJsonArray(String json) {
    try {
      Object v = Json.mapper().readValue(json == null ? "[]" : json, List.class);
      if (v instanceof List) {
        List<Object> raw = (List<Object>) v;
        List<String> out = new ArrayList<>();
        for (Object o : raw) {
          if (o != null) {
            out.add(String.valueOf(o));
          }
        }
        return out;
      }
    } catch (Exception ignored) {
    }
    return new ArrayList<>();
  }

  public synchronized FileMeta getFileMeta(String fileId) {
    try (PreparedStatement ps = conn.prepareStatement("SELECT size, mtime FROM files WHERE file_id = ?")) {
      ps.setString(1, fileId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return new FileMeta(rs.getLong(1), rs.getLong(2));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return null;
  }

  public synchronized void addDirectory(Path path) {
    try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO directories(path) VALUES (?)")) {
      ps.setString(1, path.toAbsolutePath().normalize().toString());
      ps.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public synchronized List<Path> listDirectories() {
    List<Path> out = new ArrayList<>();
    try (Statement st = conn.createStatement()) {
      try (ResultSet rs = st.executeQuery("SELECT path FROM directories ORDER BY id ASC")) {
        while (rs.next()) {
          out.add(Paths.get(rs.getString(1)));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return out;
  }

  public synchronized void upsertFileAndChunks(String fileId, Path path, long size, long mtime, List<String> chunks) {
    long now = System.currentTimeMillis();
    try (PreparedStatement del = conn.prepareStatement("DELETE FROM chunks WHERE file_id = ?")) {
      del.setString(1, fileId);
      del.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    try (PreparedStatement up = conn.prepareStatement("INSERT OR REPLACE INTO files(file_id, path, size, mtime, updated_at) VALUES (?,?,?,?,?)")) {
      up.setString(1, fileId);
      up.setString(2, path.toAbsolutePath().normalize().toString());
      up.setLong(3, size);
      up.setLong(4, mtime);
      up.setLong(5, now);
      up.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    try (PreparedStatement ins = conn.prepareStatement("INSERT OR REPLACE INTO chunks(chunk_id, file_id, path, chunk_index, content, updated_at) VALUES (?,?,?,?,?,?)")) {
      for (int i = 0; i < chunks.size(); i++) {
        String chunkId = fileId + ":" + i;
        ins.setString(1, chunkId);
        ins.setString(2, fileId);
        ins.setString(3, path.toAbsolutePath().normalize().toString());
        ins.setInt(4, i);
        ins.setString(5, chunks.get(i));
        ins.setLong(6, now);
        ins.addBatch();
      }
      ins.executeBatch();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public synchronized void deleteByPath(Path path) {
    String p = path.toAbsolutePath().normalize().toString();
    try (PreparedStatement delChunks = conn.prepareStatement("DELETE FROM chunks WHERE path = ?")) {
      delChunks.setString(1, p);
      delChunks.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    try (PreparedStatement delFile = conn.prepareStatement("DELETE FROM files WHERE path = ?")) {
      delFile.setString(1, p);
      delFile.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public synchronized List<ChunkRow> searchChunksLike(String query, int limit) {
    List<ChunkRow> out = new ArrayList<>();
    try (PreparedStatement ps = conn.prepareStatement("SELECT chunk_id, path, content FROM chunks WHERE content LIKE ? ORDER BY updated_at DESC LIMIT ?")) {
      ps.setString(1, "%" + query + "%");
      ps.setInt(2, limit);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(new ChunkRow(rs.getString(1), rs.getString(2), rs.getString(3)));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return out;
  }

  public synchronized List<ChunkRow> listChunksByIds(List<String> chunkIds) {
    List<ChunkRow> out = new ArrayList<>();
    if (chunkIds == null || chunkIds.isEmpty()) {
      return out;
    }
    StringBuilder sb = new StringBuilder();
    sb.append("SELECT chunk_id, path, content FROM chunks WHERE chunk_id IN (");
    for (int i = 0; i < chunkIds.size(); i++) {
      if (i > 0) sb.append(",");
      sb.append("?");
    }
    sb.append(")");
    try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
      for (int i = 0; i < chunkIds.size(); i++) {
        ps.setString(i + 1, chunkIds.get(i));
      }
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(new ChunkRow(rs.getString(1), rs.getString(2), rs.getString(3)));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return out;
  }

  public synchronized void upsertSession(String sessionId, String title) {
    upsertSession(sessionId, "", title);
  }

  public synchronized void upsertSession(String sessionId, String agentId, String title) {
    long now = System.currentTimeMillis();
    String aid = agentId == null ? "" : agentId;
    try (PreparedStatement ps = conn.prepareStatement(
        "INSERT OR REPLACE INTO chat_session(session_id, agent_id, title, created_at, updated_at) " +
            "VALUES (?, ?, COALESCE((SELECT title FROM chat_session WHERE session_id = ?), ?), " +
            "COALESCE((SELECT created_at FROM chat_session WHERE session_id = ?), ?), ?)"
    )) {
      ps.setString(1, sessionId);
      ps.setString(2, aid);
      ps.setString(3, sessionId);
      ps.setString(4, title);
      ps.setString(5, sessionId);
      ps.setLong(6, now);
      ps.setLong(7, now);
      ps.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public synchronized void appendMessage(String sessionId, String role, String content) {
    long now = System.currentTimeMillis();
    String messageId = UUID.randomUUID().toString();
    try (PreparedStatement ps = conn.prepareStatement(
        "INSERT INTO chat_message(message_id, session_id, role, content, created_at) VALUES (?,?,?,?,?)"
    )) {
      ps.setString(1, messageId);
      ps.setString(2, sessionId);
      ps.setString(3, role);
      ps.setString(4, content);
      ps.setLong(5, now);
      ps.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    try (PreparedStatement ps = conn.prepareStatement("UPDATE chat_session SET updated_at = ? WHERE session_id = ?")) {
      ps.setLong(1, now);
      ps.setString(2, sessionId);
      ps.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public synchronized void deleteSession(String sessionId) {
    try (PreparedStatement delMsg = conn.prepareStatement("DELETE FROM chat_message WHERE session_id = ?")) {
      delMsg.setString(1, sessionId);
      delMsg.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    try (PreparedStatement delSes = conn.prepareStatement("DELETE FROM chat_session WHERE session_id = ?")) {
      delSes.setString(1, sessionId);
      delSes.executeUpdate();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public synchronized List<SessionRow> listSessions(int limit) {
    return listSessionsByAgent("", limit);
  }

  public synchronized List<SessionRow> listSessionsByAgent(String agentId, int limit) {
    String aid = agentId == null ? "" : agentId;
    List<SessionRow> out = new ArrayList<SessionRow>();
    try (PreparedStatement ps = conn.prepareStatement(
        "SELECT session_id, title, created_at, updated_at FROM chat_session WHERE agent_id = ? ORDER BY updated_at DESC LIMIT ?"
    )) {
      ps.setString(1, aid);
      ps.setInt(2, limit);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(new SessionRow(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4)));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return out;
  }

  public synchronized List<MessageRow> listMessages(String sessionId, int limit) {
    List<MessageRow> out = new ArrayList<MessageRow>();
    try (PreparedStatement ps = conn.prepareStatement(
        "SELECT role, content, created_at FROM chat_message WHERE session_id = ? ORDER BY created_at ASC LIMIT ?"
    )) {
      ps.setString(1, sessionId);
      ps.setInt(2, limit);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(new MessageRow(rs.getString(1), rs.getString(2), rs.getLong(3)));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return out;
  }

  public static final class ChunkRow {
    public final String chunkId;
    public final String path;
    public final String content;

    public ChunkRow(String chunkId, String path, String content) {
      this.chunkId = chunkId;
      this.path = path;
      this.content = content;
    }
  }

  public static final class JobRow {
    public final String jobId;
    public final String type;
    public final String path;
    public final int attempts;

    public JobRow(String jobId, String type, String path, int attempts) {
      this.jobId = jobId;
      this.type = type;
      this.path = path;
      this.attempts = attempts;
    }
  }

  public static final class JobStatusRow {
    public final String jobId;
    public final String type;
    public final String path;
    public final String status;
    public final int attempts;
    public final String lastError;
    public final long createdAt;
    public final long updatedAt;

    public JobStatusRow(String jobId, String type, String path, String status, int attempts, String lastError, long createdAt, long updatedAt) {
      this.jobId = jobId;
      this.type = type;
      this.path = path;
      this.status = status;
      this.attempts = attempts;
      this.lastError = lastError;
      this.createdAt = createdAt;
      this.updatedAt = updatedAt;
    }
  }

  public static final class AgentSummaryRow {
    public final String agentId;
    public final String name;
    public final String description;
    public final List<String> tags;
    public final int skillCount;
    public final int ruleCount;
    public final long createdAt;
    public final long updatedAt;

    public AgentSummaryRow(String agentId, String name, String description, List<String> tags, int skillCount, int ruleCount, long createdAt, long updatedAt) {
      this.agentId = agentId;
      this.name = name;
      this.description = description;
      this.tags = tags;
      this.skillCount = skillCount;
      this.ruleCount = ruleCount;
      this.createdAt = createdAt;
      this.updatedAt = updatedAt;
    }
  }

  public static final class AgentDetailRow {
    public final String agentId;
    public final String name;
    public final String description;
    public final List<String> tags;
    public final List<String> skillFiles;
    public final List<String> systemRuleFiles;
    public final List<String> triggerRuleFiles;
    public final long createdAt;
    public final long updatedAt;

    public AgentDetailRow(String agentId, String name, String description, List<String> tags, List<String> skillFiles, List<String> systemRuleFiles, List<String> triggerRuleFiles, long createdAt, long updatedAt) {
      this.agentId = agentId;
      this.name = name;
      this.description = description;
      this.tags = tags;
      this.skillFiles = skillFiles;
      this.systemRuleFiles = systemRuleFiles;
      this.triggerRuleFiles = triggerRuleFiles;
      this.createdAt = createdAt;
      this.updatedAt = updatedAt;
    }
  }

  public static final class FileMeta {
    public final long size;
    public final long mtime;

    public FileMeta(long size, long mtime) {
      this.size = size;
      this.mtime = mtime;
    }
  }

  public static final class SessionRow {
    public final String sessionId;
    public final String title;
    public final long createdAt;
    public final long updatedAt;

    public SessionRow(String sessionId, String title, long createdAt, long updatedAt) {
      this.sessionId = sessionId;
      this.title = title;
      this.createdAt = createdAt;
      this.updatedAt = updatedAt;
    }
  }

  public static final class MessageRow {
    public final String role;
    public final String content;
    public final long createdAt;

    public MessageRow(String role, String content, long createdAt) {
      this.role = role;
      this.content = content;
      this.createdAt = createdAt;
    }
  }

  @Override
  public synchronized void close() {
    try {
      if (conn != null) {
        conn.close();
      }
    } catch (Exception ignored) {
    }
  }
}
