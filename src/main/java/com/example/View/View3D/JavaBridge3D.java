package com.example.View.View3D;

import javafx.application.Platform;
import javafx.scene.web.WebEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JavaBridge3D {

    private final WebEngine engine;

    public JavaBridge3D(WebEngine engine) {
        this.engine = engine;
    }

    public void loadSatCSV(String absolutePath) {
        Platform.runLater(() -> {
            try {
                String content = Files.readString(Path.of(absolutePath));

                String escaped = content
                        .replace("\\", "\\\\")
                        .replace("`", "\\`")
                        .replace("${", "\\${");

                engine.executeScript(
                        "window.receiveCSVContent(`" + escaped + "`);"
                );

            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public void loadGroundStationsCSV(String absolutePath) {
        Platform.runLater(() -> {
            try {
                String content = Files.readString(Path.of(absolutePath));

                String escaped = content
                        .replace("\\", "\\\\")
                        .replace("`", "\\`")
                        .replace("${", "\\${");

                engine.executeScript(
                        "window.receiveGSCSVContent(`" + escaped + "`);"
                );

            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public void setSimulationEpoch(String isoDate) {
        Platform.runLater(() ->
                engine.executeScript(
                        "window.setSimulationEpoch('" + isoDate + "');"
                )
        );
    }
}