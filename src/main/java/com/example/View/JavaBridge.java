package com.example.View;
import javafx.application.Platform;
import netscape.javascript.JSObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Injected into the WebEngine as window.javaBridge.
 * JS calls javaBridge.loadSatCSV(absolutePath) when the user picks a file.
 */
public class JavaBridge {

    private final javafx.scene.web.WebEngine engine;

    public JavaBridge(javafx.scene.web.WebEngine engine) {
        this.engine = engine;
    }

    /** Called from JS: window.javaBridge.loadSatCSV(path) */
    public void loadSatCSV(String absolutePath) {
        Platform.runLater(() -> {
            try {
                String content = new String(Files.readAllBytes(Paths.get(absolutePath)));
                content = content.replace("\\", "\\\\").replace("`", "\\`");
                engine.executeScript("window.receiveCSVContent(`" + content + "`);");
            } catch (IOException e) {
                System.err.println("JavaBridge.loadSatCSV error: " + e.getMessage());
            }
        });
    }
}