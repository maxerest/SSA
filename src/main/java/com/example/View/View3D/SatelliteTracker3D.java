package com.example.View.View3D;

import com.example.Ground_stations.EO_detection;
import com.example.Ground_stations.Ground_station;
import com.example.Parametres;
import com.example.View.JavaBridge;
import com.example.View.SatelliteTrackerUI;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SatelliteTracker3D {

    private final URL tracker3DUrl =
            getClass().getResource("/final_visualization_3D/3D_visualiztion.html");

    private WebView webView;
    private WebEngine engine;

    private boolean pageLoaded = false;

    private String pendingEpoch;
    private boolean pendingSatelliteLoad;
    private boolean pendingGroundStationLoad;

    public void start(Stage stage) {
        webView = new WebView();
        engine = webView.getEngine();
        engine.load(tracker3DUrl.toExternalForm());
        JavaBridge3D bridge = new JavaBridge3D(engine);
        engine.getLoadWorker().stateProperty().addListener(
                (obs, oldState, newState) -> {

                    if (newState != Worker.State.SUCCEEDED) {
                        return;
                    }

                    // 1. Register the Java bridge on window
                    JSObject window = (JSObject) engine.executeScript("window");
                    window.setMember("javaBridge", bridge);
                    window.setMember("javaConsole", new SatelliteTrackerUI.JavaConsole());
                    System.out.println("[3DUI] HTML loaded");

                    pageLoaded = true;

                    engine.executeScript(
                            "console.log = function(m){ javaConsole.log(String(m)); };" +
                                    "console.warn = function(m){ javaConsole.warn(String(m)); };" +
                                    "console.error = function(m){ javaConsole.error(String(m)); };"
                    );

                    waitForGlobe(engine);
                    flushPendingData();
                    Platform.runLater(() -> {
                        try {
                            String startDateIso = Parametres.date_orekit.toString();
                            engine.executeScript("setSimulationEpoch('" + startDateIso + "');");

                            String gsPath = Paths.get("src/main/resources/Satcom/GS_coordinates.csv")
                                    .toAbsolutePath().toString();
                            String gsContent = new String(Files.readAllBytes(Paths.get(gsPath)));
                            gsContent = gsContent.replace("\\", "\\\\").replace("`", "\\`");
                            engine.executeScript("loadGSCSV(`" + gsContent + "`);");

                            String satPath = Paths.get("src/main/resources/CSV_exports/real_sat/real_sats.csv")
                                    .toAbsolutePath().toString();
                            String satContent = new String(Files.readAllBytes(Paths.get(satPath)));
                            satContent = satContent.replace("\\", "\\\\").replace("`", "\\`");
                            engine.executeScript("receiveCSVContent(`" + satContent + "`);");


                        } catch (Exception e) {
                            System.err.println("Error loading GS/EO data in 3D: " + e.getMessage());
                        }
                    });
                });

        if (tracker3DUrl == null) {
            throw new RuntimeException(
                    "Cannot find 3D_visualiztion.html");
        }


        Scene scene = new Scene(webView, 1000, 600);

        stage.setTitle("3D Satellite Visualizer");
        stage.setScene(scene);
        stage.show();
    }

    private void flushPendingData() {

        if (pendingEpoch != null) {
            sendEpoch(pendingEpoch);
            pendingEpoch = null;
        }

        if (pendingGroundStationLoad) {
            sendGroundStations(Ground_station.URL_GS);
            pendingGroundStationLoad = false;
        }

        if (pendingSatelliteLoad) {
            sendSatellites(
                    "src/main/resources/CSV_exports/real_sat/real_sats.csv");
            pendingSatelliteLoad = false;
        }
    }

    public void setEpoch(String isoDate) {

        if (!pageLoaded) {
            pendingEpoch = isoDate;
            return;
        }

        sendEpoch(isoDate);
    }

    public void loadSatellites() {

        if (!pageLoaded) {
            pendingSatelliteLoad = true;
            return;
        }

        sendSatellites(
                "src/main/resources/CSV_exports/real_sat/real_sats.csv");
    }

    public void loadGroundStations() {

        if (!pageLoaded) {
            pendingGroundStationLoad = true;
            return;
        }

        sendGroundStations(Ground_station.URL_GS);
    }

    private void sendEpoch(String isoDate) {

        Platform.runLater(() -> {
            try {

                engine.executeScript(
                        "setSimulationEpoch('" + isoDate + "');"
                );

                System.out.println(
                        "[3DUI] Epoch sent: " + isoDate);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Keeps method name unchanged.
     * Reads CSV content and sends actual file content to JS.
     */
    private void sendSatellites(String csvPath) {

        Platform.runLater(() -> {
            try {

                String csvContent =
                        Files.readString(Paths.get(csvPath));

                csvContent = csvContent
                        .replace("\\", "\\\\")
                        .replace("`", "\\`");

                engine.executeScript(
                        "receiveCSVContent(`" + csvContent + "`);"
                );

                System.out.println(
                        "[3DUI] Satellite CSV sent");

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Keeps method name unchanged.
     * Reads CSV content and sends actual file content to JS.
     */
    private void sendGroundStations(String csvPath) {

        Platform.runLater(() -> {
            try {

                String csvContent =
                        Files.readString(Paths.get(csvPath));

                csvContent = csvContent
                        .replace("\\", "\\\\")
                        .replace("`", "\\`");

                engine.executeScript(
                        "receiveGSCSVContent(`" + csvContent + "`);"
                );

                System.out.println(
                        "[3DUI] Ground station CSV sent");

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public boolean isLoaded() {
        return pageLoaded;
    }

    public WebView getWebView() {
        return webView;
    }

    public WebEngine getEngine() {
        return engine;
    }
    private void waitForGlobe(WebEngine engine) {
        for (int i = 0; i < 50; i++) {
            try { Thread.sleep(100); } catch (Exception ignored) {}
            final boolean[] ready = {false};
            Platform.runLater(() -> ready[0] = Boolean.TRUE.equals(engine.executeScript("window.GlobeInitialized")));
            try { Thread.sleep(50); } catch (Exception ignored) {}
            if (ready[0]) break;
        }
    }
}