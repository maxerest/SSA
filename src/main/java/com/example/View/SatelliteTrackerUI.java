package com.example.View;
import com.example.Ground_stations.EO_detection;
import javafx.application.Application;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import java.net.URL;
import java.nio.file.Paths;

public class SatelliteTrackerUI extends Application {

    @Override
    public void start(Stage stage) {
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();

        // Load the HTML from resources
        URL url = getClass().getResource("/satellite_tracker.html");
        engine.load(url.toExternalForm());

        // Once page is loaded, inject your CSV
        engine.getLoadWorker().stateProperty().addListener((obs, old, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                new Thread(() -> {
                    // Poll mapInitialized on the FX thread
                    for (int i = 0; i < 50; i++) {
                        try { Thread.sleep(100); } catch (Exception ignored) {}
                        final boolean[] ready = {false};
                        javafx.application.Platform.runLater(() -> {
                            ready[0] = Boolean.TRUE.equals(engine.executeScript("window.mapInitialized"));
                        });
                        try { Thread.sleep(50); } catch (Exception ignored) {}
                        if (ready[0]) break;
                    }

                    javafx.application.Platform.runLater(() -> {
                        try {
                            String csvPath = Paths.get("src/main/resources/CSV_exports/real_sat/real_sats.csv")
                                    .toAbsolutePath().toString();
                            String csvContent = new String(java.nio.file.Files.readAllBytes(Paths.get(csvPath)));
                            csvContent = csvContent.replace("\\", "\\\\").replace("`", "\\`");
                            engine.executeScript("applyData(parseCSV(`" + csvContent + "`));");

                            String gsPath = Paths.get("src/main/resources/GS_coordinates.csv")
                                    .toAbsolutePath().toString();
                            String gsContent = new String(java.nio.file.Files.readAllBytes(Paths.get(gsPath)));
                            gsContent = gsContent.replace("\\", "\\\\").replace("`", "\\`");
                            engine.executeScript("loadGSFromText(`" + gsContent + "`);");

                            if (EO_detection.EO_detection){
                                String EOPath = Paths.get("src/main/resources/EO detection/Coordinates_area_to_observe.csv")
                                        .toAbsolutePath().toString();
                                String EOContent = new String(java.nio.file.Files.readAllBytes(Paths.get(EOPath)));
                                EOContent = EOContent.replace("\\", "\\\\").replace("`", "\\`");
                                engine.executeScript("loadEOFromText(`" + EOContent + "`);");
                            }


                        } catch (Exception e) {
                            System.out.println("ERROR: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                }).start();
            }
        });

        Scene scene = new Scene(webView, 1200, 700);
        stage.setTitle("Satellite Ground Track");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
