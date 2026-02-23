package local.ai.desktop;

import java.nio.file.Path;
import java.nio.file.Paths;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import local.ai.server.ServerRuntime;

public final class DesktopApp extends Application {
  private ServerRuntime server;

  @Override
  public void start(Stage stage) {
    int port = 18080;
    String homeProp = System.getProperty("assistant.home");
    Path home = homeProp == null || homeProp.isEmpty()
        ? Paths.get(System.getProperty("user.dir"), "config")
        : Paths.get(homeProp);

    server = ServerRuntime.start(port, home);

    WebView view = new WebView();
    WebEngine engine = view.getEngine();
    engine.load("http://127.0.0.1:" + port + "/");

    stage.setTitle("AI Assistant Prototype");
    stage.setScene(new Scene(view, 1100, 780));
    stage.show();
  }

  @Override
  public void stop() {
    if (server != null) {
      server.stop();
    }
    Platform.exit();
  }
}

