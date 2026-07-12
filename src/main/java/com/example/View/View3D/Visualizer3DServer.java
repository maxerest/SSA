package com.example.View.View3D;

import com.example.App;
import com.example.Ground_stations.EO_detection;
import com.example.Ground_stations.Ground_station;
import com.example.Mission_config.ConfigBridge;
import com.example.Mission_config.MissionConfig;
import com.example.Mission_config.MissionConfiguratorUI;
import com.example.Parametres;
import com.example.View.Sun_position;
import com.example.View.View2D.VisualizerGroundTrack;
import javafx.application.Application;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Visualizer3DServer extends WebSocketServer {

    private static final int PORT = 8765;
    private WebSocket connectedClient = null;
    private final CountDownLatch clientConnected = new CountDownLatch(1);
    private final CountDownLatch serverReady = new CountDownLatch(1);
    public Visualizer3DServer() {
        super(new InetSocketAddress("localhost", PORT));
    }
    private void startHttpServer() throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress(8766), 0);

        // Serve all files from your resources folder
        http.createContext("/", exchange -> {
            String requestPath = exchange.getRequestURI().getPath();
            if (requestPath.equals("/")) requestPath = "/3D_visualiztion.html";

            // Always look inside /final_visualization_3D/
            String resourcePath = "/final_visualization_3D" + requestPath;

            try {
                var resource = getClass().getResource(resourcePath);
                if (resource == null) {
                    System.err.println("[HTTP] 404: " + resourcePath);
                    byte[] notFound = "404 Not Found".getBytes();
                    exchange.sendResponseHeaders(404, notFound.length);
                    exchange.getResponseBody().write(notFound);
                    exchange.close();
                    return;
                }
                Path filePath = Paths.get(resource.toURI());
                byte[] bytes = Files.readAllBytes(filePath);
                String mime = getMime(requestPath);
                exchange.getResponseHeaders().set("Content-Type", mime);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (Exception e) {
                System.err.println("[HTTP] Error serving " + resourcePath + ": " + e.getMessage());
                byte[] err = "500 Internal Server Error".getBytes();
                exchange.sendResponseHeaders(500, err.length);
                exchange.getResponseBody().write(err);
            }
            exchange.close();
        });

        http.start();
        System.out.println("[3DUI] HTTP server started on port 8766");
    }

    private String getMime(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".js"))   return "application/javascript";
        if (path.endsWith(".css"))  return "text/css";
        if (path.endsWith(".jpg"))  return "image/jpeg";
        if (path.endsWith(".png"))  return "image/png";
        return "application/octet-stream";
    }
    // ----------------------------------------------------------------
    // WebSocketServer callbacks
    // ----------------------------------------------------------------
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connectedClient = conn;
        clientConnected.countDown();
        System.out.println("[3DUI] Browser connected.");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("[3DUI] Browser disconnected — shutting down.");
        System.exit(0);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("[3DUI→Java] " + message);
        if(message.startsWith("propagate")) {
            System.out.println("Starting propagation");
            launch_propagation();
        }
        if(message.startsWith("configure")) {
            System.out.println("Starting configuration");
            launch_configurator();
        }
        if(message.startsWith("2d_view")) {
            System.out.println("Starting 2D view launch");
            Thread t = new Thread(() -> new VisualizerGroundTrack().launch(), "2DUI-launcher");
            t.setDaemon(true);
            t.start();
        }

    }

    @Override
    public void onError(WebSocket conn, Exception e) {
        System.err.println("[3DUI] WebSocket error: " + e.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("[3DUI] WebSocket server started on port " + PORT);
        serverReady.countDown(); // signal that server is truly ready
    }

    // ----------------------------------------------------------------
    // Start server, open browser, send data
    // ----------------------------------------------------------------
    public void launch() {
        try {
            this.start();

            boolean started = serverReady.await(10, TimeUnit.SECONDS);
            if (!started) {
                System.err.println("[3DUI] Server failed to start in time.");
                return;
            }

            // Start HTTP server BEFORE opening browser
            startHttpServer();

            // Open via HTTP instead of file://
            Desktop.getDesktop().browse(new URI("http://localhost:8766/"));
            System.out.println("[3DUI] Opened in browser.");

            boolean connected = clientConnected.await(30, TimeUnit.SECONDS);
            if (!connected) {
                System.err.println("[3DUI] Browser did not connect in time.");
            }

        } catch (Exception e) {
            System.err.println("[3DUI] Launch error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ----------------------------------------------------------------
    // Send all data
    // ----------------------------------------------------------------
    private void sendAllData() {
        try {
            // Epoch
            String epoch = Parametres.date_orekit.toString();
            send("EPOCH:" + epoch);
            System.out.println("[3DUI] Epoch sent.");

            // Ground stations
            String gsContent = Files.readString(
                    Paths.get("src/main/resources/Satcom/GS_coordinates.csv").toAbsolutePath());
            send("GS_CSV:" + escapeForJs(gsContent));
            System.out.println("[3DUI] Ground stations sent.");

            // Satellites
            String satContent = Files.readString(
                    Paths.get("src/main/resources/CSV_exports/real_sat/real_sats.csv").toAbsolutePath());
            send("SAT_CSV:" + escapeForJs(satContent));
            System.out.println("[3DUI] Satellites sent.");

            // Orbital parameters
            String orbContent = Files.readString(
                    Paths.get("src/main/resources/CSV_exports/real_sat/real_sats_Orbital_param.csv").toAbsolutePath());
            send("ORBITAL_CSV:" + escapeForJs(orbContent));
            System.out.println("[3DUI] Orbital params sent.");

            // Satcom links
            String satcomContent = Files.readString(
                    Paths.get("src/main/resources/Satcom/satcom_link.csv").toAbsolutePath());
            send("SATCOM_CSV:" + escapeForJs(satcomContent));
            System.out.println("[3DUI] Satcom links sent.");
            if(EO_detection.EO_detection){
                // Satcom links
                String EOContent = Files.readString(
                        Paths.get("src/main/resources/EO detection/observations.csv").toAbsolutePath());
                send("EO_CSV:" + escapeForJs(EOContent));
                System.out.println("[3DUI] EO detections sent.");
                String ZonesContent = Files.readString(
                        Paths.get("src/main/resources/EO detection/Coordinates_area_to_observe.csv").toAbsolutePath());
                send("EO_Zones:" + escapeForJs(ZonesContent));
                System.out.println("[3DUI] EO zones sent.");
            }



        } catch (Exception e) {
            System.err.println("[3DUI] sendAllData error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------
    private String escapeForJs(String content) {
        return content
                .replace("\\", "\\\\")
                .replace("`",  "\\`")
                .replace("${", "\\${");
    }

    public void send(String message) {
        if (connectedClient != null && connectedClient.isOpen()) {
            connectedClient.send(message);
        } else {
            System.err.println("[3DUI] No client connected, cannot send.");
        }
    }

    // ----------------------------------------------------------------
    // Public API for live updates
    // ----------------------------------------------------------------
    public void updateEpoch(String isoDate) {
        send("EPOCH:" + isoDate);
    }

    public void reloadSatellites() {
        try {
            String content = Files.readString(
                    Paths.get("src/main/resources/CSV_exports/real_sat/real_sats.csv").toAbsolutePath());
            send("SAT_CSV:" + escapeForJs(content));
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void reloadGroundStations() {
        try {
            String content = Files.readString(Paths.get(Ground_station.URL_GS).toAbsolutePath());
            send("GS_CSV:" + escapeForJs(content));
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void reloadOrbitalParams() {
        try {
            String content = Files.readString(
                    Paths.get("src/main/resources/CSV_exports/real_sat/real_sats_Orbital_param.csv").toAbsolutePath());
            send("ORBITAL_CSV:" + escapeForJs(content));
        } catch (Exception e) { e.printStackTrace(); }
    }

    /*
    THIS is where the configruator will be used when clicked on in the websocket
     */
    private void launch_configurator()  {

        ConfigBridge bridge = new ConfigBridge(config -> {
            System.out.println("[java] Mission config received");
            // Hide configurator window
            for (javafx.stage.Window window : new ArrayList<>(javafx.stage.Window.getWindows())) {
                window.hide();
            }

        }, new CountDownLatch(1));

        MissionConfiguratorUI.setBridge(bridge);

        Application.launch(MissionConfiguratorUI.class);
        try{
            String staticContent = Files.readString(
                    Paths.get("src/main/resources/CSV_exports/Static_position/initial_position.csv").toAbsolutePath());
            send("configurator" + escapeForJs(staticContent));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println("[3DUI] Launch configurator complete.");
        // Set the Epoch of the 3D view
        send("EPOCH:" + Parametres.date_orekit.toString());
        System.out.println("[3DUI] Epoch  sent.");

        //update to the sun position
        Vector3D sunposition = Sun_position.getSun_position_initial();
        send("Sun position:"+ sunposition.getX()+","+ sunposition.getY()+","+sunposition.getZ());
        System.out.println("[3DUI] Initial sun position  sent.");
    }

    /*
   THIS is where the propgation will be used when clicked on in the websocket
    Need to make sure :
    -con,figurator is received
    -dates are set
    -satellties are created
    */
    private void launch_propagation(){
        for (MissionConfig m:App.liste_config){
            try {
                App.runSimulation(m);
                sendAllData();
                System.out.println("[3DUI] Simulation complete.");
            }catch (Exception e){System.out.println("[Java] Launch propagation error: " + e.getMessage());}
        }
    }


}