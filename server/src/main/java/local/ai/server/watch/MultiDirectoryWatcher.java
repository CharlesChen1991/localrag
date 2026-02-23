package local.ai.server.watch;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import local.ai.server.etl.EtlService;

public final class MultiDirectoryWatcher {
  private final EtlService etl;
  private final WatchService watchService;
  private final Map<WatchKey, Path> keys = new ConcurrentHashMap<>();
  private final Set<Path> roots = ConcurrentHashMap.newKeySet();
  private final ExecutorService loop;
  private final ScheduledExecutorService debounce;
  private final Map<Path, Runnable> pending = new ConcurrentHashMap<>();

  public MultiDirectoryWatcher(EtlService etl) {
    this.etl = etl;
    try {
      this.watchService = FileSystems.getDefault().newWatchService();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    this.loop = Executors.newSingleThreadExecutor();
    this.debounce = Executors.newSingleThreadScheduledExecutor();
  }

  public void addRoot(Path root) {
    Path r = root.toAbsolutePath().normalize();
    if (!Files.isDirectory(r)) {
      return;
    }
    if (roots.add(r)) {
      registerAll(r);
      scanExisting(r);
    }
  }

  public Set<Path> roots() {
    return new HashSet<Path>(roots);
  }

  public void start() {
    loop.submit(this::runLoop);
  }

  public void stop() {
    try {
      watchService.close();
    } catch (Exception ignored) {
    }
    loop.shutdownNow();
    debounce.shutdownNow();
  }

  private void runLoop() {
    while (true) {
      WatchKey key;
      try {
        key = watchService.take();
      } catch (Exception e) {
        return;
      }

      Path dir = keys.get(key);
      if (dir != null) {
        for (WatchEvent<?> event : key.pollEvents()) {
          WatchEvent.Kind<?> kind = event.kind();
          if (kind == StandardWatchEventKinds.OVERFLOW) {
            continue;
          }
          Path name = (Path) event.context();
          Path child = dir.resolve(name);
          if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            if (Files.isDirectory(child)) {
              registerAll(child);
            }
            scheduleUpsert(child);
          } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            scheduleUpsert(child);
          } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            scheduleDelete(child);
          }
        }
      }

      boolean valid = key.reset();
      if (!valid) {
        keys.remove(key);
      }
    }
  }

  private void scheduleUpsert(Path path) {
    schedule(path, () -> etl.submitUpsert(path));
  }

  private void scheduleDelete(Path path) {
    schedule(path, () -> etl.submitDelete(path));
  }

  private void schedule(Path path, Runnable r) {
    Path p = path.toAbsolutePath().normalize();
    pending.put(p, r);
    debounce.schedule(() -> {
      Runnable rr = pending.remove(p);
      if (rr != null) {
        rr.run();
      }
    }, 500, TimeUnit.MILLISECONDS);
  }

  private void registerAll(Path start) {
    try {
      Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
          register(dir);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException ignored) {
    }
  }

  private void scanExisting(Path root) {
    loop.submit(() -> {
      try {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (attrs.isRegularFile()) {
              scheduleUpsert(file);
            }
            return FileVisitResult.CONTINUE;
          }
        });
      } catch (IOException ignored) {
      }
    });
  }

  private void register(Path dir) {
    try {
      WatchKey key = dir.register(
          watchService,
          StandardWatchEventKinds.ENTRY_CREATE,
          StandardWatchEventKinds.ENTRY_MODIFY,
          StandardWatchEventKinds.ENTRY_DELETE
      );
      keys.put(key, dir);
    } catch (IOException ignored) {
    }
  }
}
