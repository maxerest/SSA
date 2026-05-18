package com.example.Mission_config;

import javafx.application.Application;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.io.File;

public class MissionConfiguratorUI extends Application {

    private static ConfigBridge bridge;

    public static void setBridge(ConfigBridge b) {
        bridge = b;
    }

    @Override
    public void start(Stage stage) {
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) engine.executeScript("window");
                window.setMember("configBridge", bridge);
                System.out.println("[MissionConfiguratorUI] configBridge injected");
                engine.executeScript("loadSubsystems()");
            }
        });

        File html = new File("src/main/resources/Initial_config_setup/configurator.html");
        engine.load(html.toURI().toString());

        stage.setTitle("Mission Configurator");
        stage.setScene(new Scene(webView, 1200, 800));
        stage.show();
    }
}