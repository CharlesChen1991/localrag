package local.ai.server;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class ServerMain {
  public static void main(String[] args) {
    int port = 18080;
    Path home = Paths.get(System.getProperty("user.dir"), "config");
    for (String arg : args) {
      if (arg.startsWith("--port=")) {
        port = Integer.parseInt(arg.substring("--port=".length()));
      }
      if (arg.startsWith("--home=")) {
        home = Paths.get(arg.substring("--home=".length()));
      }
    }

    ServerRuntime runtime = ServerRuntime.start(port, home);
    Runtime.getRuntime().addShutdownHook(new Thread(runtime::stop));
  }
}
