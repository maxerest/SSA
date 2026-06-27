package com.example.View.View2D;

import com.example.Ground_stations.EO_detection;
import com.example.Ground_stations.Ground_station;
import com.example.Parametres;
import com.sun.net.httpserver.HttpServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Drop-in replacement for SatelliteTrackerUI (JavaFX WebView).
 *
 * Architecture mirrors Visualizer3DServer:
 *   - WebSocket on port 8767  →  real-time data push to the browser
 *   - HTTP       on port 8768  →  serves the 2D HTML + assets as static files
 *
 * The HTML page must connect to ws://localhost:8767 and expose the same
 * JS entry-points that were previously called via engine.executeScript():
 *   window.setSimulationEpoch(isoString)
 *   window.loadGSFromText(csvText)
 *   window.loadEOFromText(csvText)
 *   window.loadObsFromText(csvText)
 *   window.loadSATCOMFromText(csvText)
 *   window.populateExplorer(jsonString)
 *
 * WebSocket message protocol (Java → Browser):
 *   EPOCH:<iso>
 *   GS_CSV:<csv>
 *   EO_CSV:<csv>
 *   OBS_CSV:<csv>
 *   SATCOM_CSV:<csv>
 *   EXPLORER:<json>
 *
 * WebSocket message protocol (Browser → Java):  none currently defined,
 * but onMessage() is ready to handle future commands.
 */

    public class VisualizerGroundTrack extends WebSocketServer {

        // ── Ports ────────────────────────────────────────────────────────
        private static final int WS_PORT   = 8767;
        private static final int HTTP_PORT = 8768;

        // ── Resource roots (match original paths) ────────────────────────
        private static final String HTML_RESOURCE_ROOT = "/final_visualization_2D";
        private static final String HTML_ENTRY_POINT   = "/satellite_tracker.html";
        private static final String CSV_ROOT           = "src/main/resources/CSV_exports";

        // ── WebSocket state ──────────────────────────────────────────────
        private WebSocket connectedClient = null;
        private final CountDownLatch clientConnected = new CountDownLatch(1);
        private final CountDownLatch serverReady     = new CountDownLatch(1);

        // ────────────────────────────────────────────────────────────────
        // Constructor
        // ────────────────────────────────────────────────────────────────
        public VisualizerGroundTrack() {
            super(new InetSocketAddress("localhost", WS_PORT));
        }

        // ────────────────────────────────────────────────────────────────
        // WebSocketServer callbacks
        // ────────────────────────────────────────────────────────────────
        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            connectedClient = conn;
            clientConnected.countDown();
            System.out.println("[2DUI] Browser connected via WebSocket.");
            sendAllData();
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            System.out.println("[2DUI] Browser disconnected (code=" + code + ").");
            connectedClient = null;
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            System.out.println("[2DUI←Browser] " + message);
            // Extend here if the 2D page needs to send commands back to Java
        }

        @Override
        public void onError(WebSocket conn, Exception e) {
            System.err.println("[2DUI] WebSocket error: " + e.getMessage());
        }

        @Override
        public void onStart() {
            System.out.println("[2DUI] WebSocket server ready on port " + WS_PORT);
            serverReady.countDown();
        }

        // ────────────────────────────────────────────────────────────────
        // HTTP server — serves 2D HTML + assets as static files
        // ────────────────────────────────────────────────────────────────
        private void startHttpServer() throws IOException {
            HttpServer http = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);

            http.createContext("/", exchange -> {
                String reqPath = exchange.getRequestURI().getPath();
                if (reqPath.equals("/")) reqPath = HTML_ENTRY_POINT;

                String resourcePath = HTML_RESOURCE_ROOT + reqPath;

                try {
                    var resource = getClass().getResource(resourcePath);
                    if (resource == null) {
                        System.err.println("[2DUI HTTP] 404: " + resourcePath);
                        byte[] body = "404 Not Found".getBytes();
                        exchange.sendResponseHeaders(404, body.length);
                        exchange.getResponseBody().write(body);
                        exchange.close();
                        return;
                    }

                    Path filePath = Paths.get(resource.toURI());
                    byte[] bytes  = Files.readAllBytes(filePath);

                    exchange.getResponseHeaders().set("Content-Type", getMime(reqPath));
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);

                } catch (Exception e) {
                    System.err.println("[2DUI HTTP] Error serving " + resourcePath + ": " + e.getMessage());
                    byte[] body = "500 Internal Server Error".getBytes();
                    exchange.sendResponseHeaders(500, body.length);
                    exchange.getResponseBody().write(body);
                }

                exchange.close();
            });

            http.start();
            System.out.println("[2DUI] HTTP server ready on port " + HTTP_PORT);
        }

        private String getMime(String path) {
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".js"))   return "application/javascript";
            if (path.endsWith(".css"))  return "text/css";
            if (path.endsWith(".json")) return "application/json";
            if (path.endsWith(".png"))  return "image/png";
            if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
            return "application/octet-stream";
        }

        // ────────────────────────────────────────────────────────────────
        // Public launch entry-point  (replaces Application.launch())
        // ────────────────────────────────────────────────────────────────
        public void launch() {
            try {
                this.start();

                boolean started = serverReady.await(10, TimeUnit.SECONDS);
                if (!started) {
                    System.err.println("[2DUI] WebSocket server failed to start in time.");
                    return;
                }

                startHttpServer();

                Desktop.getDesktop().browse(new URI("http://localhost:" + HTTP_PORT + "/"));
                System.out.println("[2DUI] Opened browser at http://localhost:" + HTTP_PORT + "/");

                boolean connected = clientConnected.await(30, TimeUnit.SECONDS);
                if (!connected) {
                    System.err.println("[2DUI] Browser did not connect within 30 s.");
                }

            } catch (Exception e) {
                System.err.println("[2DUI] Launch error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // ────────────────────────────────────────────────────────────────
        // Data sending — mirrors what engine.executeScript() did before
        // ────────────────────────────────────────────────────────────────

        /**
         * Send every data source the page needs.
         * Called automatically on browser connect (onOpen).
         */
        private void sendAllData() {
            sendEpoch();
            sendGroundStations();
            sendFolderTree();

            if (EO_detection.EO_detection) {
                sendEOCoordinates();
                sendObservations();
            }

            if (Ground_station.satcom_activated) {
                sendSatcomLinks();
            }
        }

        // ── Individual senders ───────────────────────────────────────────

        public void sendEpoch() {
            String iso = Parametres.date_orekit.toString();
            send("EPOCH:" + iso);
            System.out.println("[2DUI] Epoch sent: " + iso);
        }

        public void sendGroundStations() {
            sendFile(
                    "GS_CSV",
                    "src/main/resources/Satcom/GS_coordinates.csv",
                    "Ground stations"
            );
        }

        public void sendEOCoordinates() {
            sendFile(
                    "EO_CSV",
                    "src/main/resources/EO detection/Coordinates_area_to_observe.csv",
                    "EO coordinates"
            );
        }

        public void sendObservations() {
            sendFile(
                    "OBS_CSV",
                    "src/main/resources/EO detection/observations.csv",
                    "Observations"
            );
        }

        public void sendSatcomLinks() {
            sendFile(
                    "SATCOM_CSV",
                    "src/main/resources/Satcom/satcom_link.csv",
                    "Satcom links"
            );
        }

        /**
         * Scans CSV_exports (one level deep) and sends the folder tree to the
         * browser via EXPLORER:<json>.
         *
         * JSON format:
         * [
         *   { "folder": "real_sat", "path": "/abs/...", "files": ["a.csv", "b.csv"] },
         *   ...
         * ]
         */
        public void sendFolderTree() {
            File root = Paths.get(CSV_ROOT).toAbsolutePath().toFile();
            List<String> entries = new ArrayList<>();

            if (!root.exists() || !root.isDirectory()) {
                System.err.println("[2DUI] CSV root not found: " + root.getAbsolutePath());
                return;
            }

            // Sub-folders
            File[] subDirs = root.listFiles(File::isDirectory);
            if (subDirs != null) {
                Arrays.sort(subDirs);
                for (File dir : subDirs) {
                    String entry = buildFolderEntry(dir.getName(), dir);
                    if (entry != null) entries.add(entry);
                }
            }

            // CSVs directly in root
            File[] rootCsvs = root.listFiles(f -> f.isFile() && f.getName().endsWith(".csv"));
            if (rootCsvs != null && rootCsvs.length > 0) {
                String entry = buildFolderEntry("CSV_exports", root);
                if (entry != null) entries.add(entry);
            }

            String json = "[" + String.join(",", entries) + "]";
            send("EXPLORER:" + json);
            System.out.println("[2DUI] Folder tree sent (" + entries.size() + " folder(s)).");
        }

        // ────────────────────────────────────────────────────────────────
        // Helpers
        // ────────────────────────────────────────────────────────────────

        /**
         * Read a file and send it as `prefix:content`.
         * Logs clearly on success or failure.
         */
        private void sendFile(String prefix, String relativePath, String label) {
            try {
                Path path    = Paths.get(relativePath).toAbsolutePath();
                String content = Files.readString(path);
                send(prefix + ":" + escapeForJs(content));
                System.out.println("[2DUI] " + label + " sent from " + path);
            } catch (IOException e) {
                System.err.println("[2DUI] Failed to send " + label + " (" + relativePath + "): " + e.getMessage());
            }
        }

        /**
         * Build a JSON folder-entry object for sendFolderTree().
         * Returns null if the folder contains no CSV files.
         */
        private String buildFolderEntry(String folderName, File dir) {
            File[] csvFiles = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".csv"));
            if (csvFiles == null || csvFiles.length == 0) return null;

            Arrays.sort(csvFiles);
            List<String> quotedNames = new ArrayList<>();
            for (File f : csvFiles) {
                quotedNames.add("\"" + f.getName().replace("\"", "\\\"") + "\"");
            }

            String absPath  = dir.getAbsolutePath().replace("\\", "/");
            String filesArr = "[" + String.join(",", quotedNames) + "]";

            return "{\"folder\":\"" + folderName + "\","
                    + "\"path\":\""   + absPath    + "\","
                    + "\"files\":"    + filesArr   + "}";
        }

        /** Escape content so it is safe to embed in a WebSocket text frame. */
        private String escapeForJs(String content) {
            return content
                    .replace("\\", "\\\\")
                    .replace("`",  "\\`")
                    .replace("${", "\\${");
        }

        /** Send a message to the connected browser, or warn if none is connected. */
        public void send(String message) {
            if (connectedClient != null && connectedClient.isOpen()) {
                connectedClient.send(message);
            } else {
                System.err.println("[2DUI] No browser connected — message dropped: "
                        + message.substring(0, Math.min(60, message.length())) + "…");
            }
        }

}
