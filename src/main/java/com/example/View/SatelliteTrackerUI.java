package com.example.View;
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
                try {
                    // Read CSV in Java, pass content directly to JS — no fetch needed
                    String csvPath = Paths.get("src/main/resources/CSV_exports/real_sat/real_sats.csv")
                            .toAbsolutePath().toString();
                    String csvContent = new String(java.nio.file.Files.readAllBytes(Paths.get(csvPath)));

                    // Escape for JS string injection
                    csvContent = csvContent.replace("\\", "\\\\").replace("`", "\\`");

                    engine.executeScript("applyData(parseCSV(`" + csvContent + "`));");
                } catch (Exception e) {
                    System.out.println("Failed to read CSV: " + e.getMessage());
                }
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
