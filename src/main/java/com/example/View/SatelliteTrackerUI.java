package com.example.View;
import com.example.Ground_stations.EO_detection;
import com.example.Ground_stations.Ground_station;
import com.example.Ground_stations.Satcom;
import com.example.Parametres;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SatelliteTrackerUI extends Application {

    // Root folder containing subfolders of CSV exports
    private static final String CSV_ROOT = "src/main/resources/CSV_exports";
    public static class JavaConsole {
        public void log(String msg)   { System.out.println("[JS] " + msg); }
        public void warn(String msg)  { System.out.println("[JS WARN] " + msg); }
        public void error(String msg) { System.err.println("[JS ERR] " + msg); }
    }
    @Override
    public void start(Stage stage) {
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();

        URL url = getClass().getResource("/satellite_tracker.html");
        engine.load(url.toExternalForm());

        // Keep a strong reference to the bridge — WebEngine only holds a weak ref
        JavaBridge bridge = new JavaBridge(engine);

        engine.getLoadWorker().stateProperty().addListener((obs, old, newState) -> {
            if (newState != Worker.State.SUCCEEDED) return;

            // 1. Register the Java bridge on window
            JSObject window = (JSObject) engine.executeScript("window");
            window.setMember("javaBridge", bridge);
            window.setMember("javaConsole", new JavaConsole());
            engine.executeScript(
                    "console.log = function(m){ javaConsole.log(String(m)); };" +
                            "console.warn = function(m){ javaConsole.warn(String(m)); };" +
                            "console.error = function(m){ javaConsole.error(String(m)); };"
            );

            // 2. Scan CSV_exports folder tree and send it to the explorer
            injectFolderTree(engine);

            // 3. Load GS and (optionally) EO data as before
            new Thread(() -> {
                waitForMap(engine);
                Platform.runLater(() -> {
                    try {
                        String startDateIso = Parametres.date_orekit.toString();
                        engine.executeScript("setSimulationEpoch('" + startDateIso + "');");

                        String gsPath = Paths.get("src/main/resources/Satcom/GS_coordinates.csv")
                                .toAbsolutePath().toString();
                        String gsContent = new String(Files.readAllBytes(Paths.get(gsPath)));
                        gsContent = gsContent.replace("\\", "\\\\").replace("`", "\\`");
                        engine.executeScript("loadGSFromText(`" + gsContent + "`);");

                        if (EO_detection.EO_detection) {
                            //Coordinates to observe
                            String eoPath = Paths.get("src/main/resources/EO detection/Coordinates_area_to_observe.csv")
                                    .toAbsolutePath().toString();
                            String eoContent = new String(Files.readAllBytes(Paths.get(eoPath)));
                            eoContent = eoContent.replace("\\", "\\\\").replace("`", "\\`");
                            engine.executeScript("loadEOFromText(`" + eoContent + "`);");
                            //Observation file
                            String OBSPath = Paths.get("src/main/resources/EO detection/observations.csv")
                                    .toAbsolutePath().toString();
                            String OBSContent = new String(Files.readAllBytes(Paths.get(OBSPath)));
                            OBSContent = OBSContent.replace("\\", "\\\\").replace("`", "\\`");
                            engine.executeScript("loadObsFromText(`" + OBSContent + "`);");

                        }
                        if (Ground_station.satcom_activated) {
                            //Coordinates to observe
                            String SATCOMPath = Paths.get("src/main/resources/Satcom/satcom_link.csv")
                                    .toAbsolutePath().toString();
                            String SATCOMContent = new String(Files.readAllBytes(Paths.get(SATCOMPath)));
                            SATCOMContent = SATCOMContent.replace("\\", "\\\\").replace("`", "\\`");
                            engine.executeScript("loadSATCOMFromText(`" + SATCOMContent + "`);");

                        }

                    } catch (Exception e) {
                        System.err.println("Error loading GS/EO data: " + e.getMessage());
                    }
                });
            }).start();
        });

        Scene scene = new Scene(webView, 1200, 700);
        stage.setTitle("Satellite Ground Track");
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Scans CSV_exports recursively (one level deep) and pushes the tree
     * to the HTML explorer via window.populateExplorer(json).
     *
     * JSON format sent to JS:
     * [
     *   { "folder": "real_sat", "path": "/abs/path/CSV_exports/real_sat", "files": ["a.csv","b.csv"] },
     *   ...
     * ]
     */
    private void injectFolderTree(WebEngine engine) {
        File root = Paths.get(CSV_ROOT).toAbsolutePath().toFile();
        List<String> entries = new ArrayList<>();

        if (root.exists() && root.isDirectory()) {
            File[] subDirs = root.listFiles(File::isDirectory);
            if (subDirs != null) {
                Arrays.sort(subDirs);
                for (File dir : subDirs) {
                    File[] csvFiles = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".csv"));
                    if (csvFiles == null || csvFiles.length == 0) continue;
                    Arrays.sort(csvFiles);
                    List<String> fileNames = new ArrayList<>();
                    for (File f : csvFiles) fileNames.add(f.getName());
                    String filesJson = "[" + String.join(",", fileNames.stream()
                            .map(n -> "\"" + n.replace("\"", "\\\"") + "\"")
                            .toArray(String[]::new)) + "]";
                    String absPath = dir.getAbsolutePath().replace("\\", "/");
                    entries.add("{\"folder\":\"" + dir.getName() + "\","
                            + "\"path\":\"" + absPath + "\","
                            + "\"files\":" + filesJson + "}");
                }
            }
            // Also include CSVs directly in the root
            File[] rootCsvs = root.listFiles(f -> f.isFile() && f.getName().endsWith(".csv"));
            if (rootCsvs != null && rootCsvs.length > 0) {
                Arrays.sort(rootCsvs);
                List<String> fileNames = new ArrayList<>();
                for (File f : rootCsvs) fileNames.add(f.getName());
                String filesJson = "[" + String.join(",", fileNames.stream()
                        .map(n -> "\"" + n.replace("\"", "\\\"") + "\"")
                        .toArray(String[]::new)) + "]";
                String absPath = root.getAbsolutePath().replace("\\", "/");
                entries.add("{\"folder\":\"CSV_exports\","
                        + "\"path\":\"" + absPath + "\","
                        + "\"files\":" + filesJson + "}");
            }
        }

        final String json = "[" + String.join(",", entries) + "]";
        Platform.runLater(() -> engine.executeScript("window.populateExplorer('" + json + "');"));
    }

    /** Blocks (off FX thread) until window.mapInitialized is true. */
    private void waitForMap(WebEngine engine) {
        for (int i = 0; i < 50; i++) {
            try { Thread.sleep(100); } catch (Exception ignored) {}
            final boolean[] ready = {false};
            Platform.runLater(() -> ready[0] = Boolean.TRUE.equals(engine.executeScript("window.mapInitialized")));
            try { Thread.sleep(50); } catch (Exception ignored) {}
            if (ready[0]) break;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
