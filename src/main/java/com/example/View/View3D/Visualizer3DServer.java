package com.example.View.View3D;

import com.example.App;
import com.example.Ground_stations.Ground_station;
import com.example.Mission_config.ConfigBridge;
import com.example.Mission_config.MissionConfig;
import com.example.Mission_config.MissionConfiguratorUI;
import com.example.Parametres;
import com.example.View.SatelliteTrackerUI;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Visualizer3DServer extends WebSocketServer {

    private static final int PORT = 8765;
    private WebSocket connectedClient = null;
    private final CountDownLatch clientConnected = new CountDownLatch(1);

    public Visualizer3DServer() {
        super(new InetSocketAddress("localhost", PORT));
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
    }

    @Override
    public void onError(WebSocket conn, Exception e) {
        System.err.println("[3DUI] WebSocket error: " + e.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("[3DUI] WebSocket server started on port " + PORT);
    }

    // ----------------------------------------------------------------
    // Start server, open browser, send data
    // ----------------------------------------------------------------
    public void launch() {
        try {
            this.start();

            URI htmlUri = getClass().getResource(
                    "/final_visualization_3D/3D_visualiztion.html").toURI();
            Desktop.getDesktop().browse(htmlUri);
            System.out.println("[3DUI] Opened in browser.");

            boolean connected = clientConnected.await(15, TimeUnit.SECONDS);
            if (!connected) {
                System.err.println("[3DUI] Browser did not connect in time.");
                return;
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
            String satcomContent = Files.readString(
                    Paths.get("src/main/resources/CSV_exports/Static_position/initial_position.csv").toAbsolutePath());
            send("configurator" + escapeForJs(satcomContent));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }



        System.out.println("[3DUI] Launch configurator complete.");
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