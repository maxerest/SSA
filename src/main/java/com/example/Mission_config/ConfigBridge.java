package com.example.Mission_config;

import com.example.App;
import com.example.Ground_stations.EO_detection;
import com.example.Ground_stations.Satcom;
import com.example.Parametres;
import javafx.application.Platform;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Injected into the configurator WebView as window.configBridge.
 *
 * JS calls:
 *   configBridge.submitConfig(jsonString)   — user clicked "Launch Mission"
 *   configBridge.saveConfig(jsonString)     — user clicked "Save"
 *   configBridge.loadSavedConfig()          — returns JSON string or ""
 *   configBridge.getSubsystemsJson(folder)  — returns JSON array of subsystem objects
 *   configBridge.log(msg)                   — debug console
 */
public class ConfigBridge {

    private static final String SAVE_PATH        = "src/main/resources/mission_config.json";

    private final Consumer<MissionConfig> onConfigReady;
    private final CountDownLatch latch;

    public ConfigBridge(Consumer<MissionConfig> onConfigReady, CountDownLatch latch) {
        this.onConfigReady = onConfigReady;
        this.latch         = latch;
    }

    // ── Called by JS when user clicks "Accept config" ─────────────────────
    public void submitConfig(String jsonString) {
        Platform.runLater(() -> {
            try {
                MissionConfig config = parseConfig(jsonString);
                App.liste_par_sats_real_orbit = App.real_orbit(config);
                System.out.println("[Configurator] Config received: " + config.satellites.size() + " satellites");
                onConfigReady.accept(config);
                App.liste_config.add(config);
                latch.countDown();
            } catch (Exception e) {
                System.err.println("[ConfigBridge] submitConfig error: " + e.getMessage());
            }
        });
    }

    // ── Save config to disk ────────────────────────────────────────────────
    public void saveConfig(String jsonString) {
        try {
            File f = new File(SAVE_PATH);
            f.getParentFile().mkdirs();
            Files.writeString(f.toPath(), jsonString);
            System.out.println("[Configurator] Config saved to " + SAVE_PATH);
        } catch (IOException e) {
            System.err.println("[ConfigBridge] saveConfig error: " + e.getMessage());
        }
    }

    // ── Load previously saved config (returns "" if none) ─────────────────
    public String loadSavedConfig() {
        try {
            File f = new File(SAVE_PATH);
            if (f.exists()) return Files.readString(f.toPath());
        } catch (IOException e) {
            System.err.println("[ConfigBridge] loadSavedConfig error: " + e.getMessage());
        }
        return "";
    }

    /**
     * Scans src/main/resources/subsystems/ and returns a JSON object:
     * {
     *   "Motors": [ {name, type, mass_kg, power_w, ...}, ... ],
     *   "Eo_sensors":  [ ... ],
     *   "Antennas":     [ ... ],
     *   ...
     * }
     * Categories are derived from the 'type' column in each CSV.
     * Multiple CSV files in the same category are merged.
     */
    public String getSubsystemsJson() {
        Map<String, List<Map<String, String>>> result = new LinkedHashMap<>();
        // Try classpath first
        java.net.URL dirUrl = ConfigBridge.class.getClassLoader().getResource("subsystems");
        File root;
        if (dirUrl != null) {
            root = new File(dirUrl.getFile());
           } else {
            // Fallback to working directory
            root = Paths.get("src/main/resources/subsystems").toAbsolutePath().toFile();
        }

        if (!root.exists() || !root.isDirectory()) {
            System.err.println("[ConfigBridge] Subsystems folder not found: " + root);
            return "{}";
        }

        File[] csvFiles = root.listFiles(f -> f.isFile() && f.getName().endsWith(".csv"));
        if (csvFiles == null) return "{}";
        Arrays.sort(csvFiles);

        for (File csv : csvFiles) {
            try {
                List<String> lines = Files.readAllLines(csv.toPath());
                if (lines.isEmpty()) continue;
                String[] headers = lines.get(0).split(",", -1);

                for (int i = 1; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (line.isEmpty()) continue;
                    String[] vals = line.split(",", -1);
                    Map<String, String> entry = new LinkedHashMap<>();
                    for (int j = 0; j < headers.length; j++) {
                        entry.put(headers[j].trim(), j < vals.length ? vals[j].trim() : "");
                    }
                    // Category = value of 'type' column, fallback to filename stem uppercased
                    String category = entry.getOrDefault("type",
                            csv.getName().replace(".csv","").toUpperCase());
                    result.computeIfAbsent(category, k -> new ArrayList<>()).add(entry);
                }
            } catch (IOException e) {
                System.err.println("[ConfigBridge] Error reading " + csv.getName() + ": " + e.getMessage());
            }
        }

        System.out.println(result);
        // Serialize to JSON manually (no external dependency)
        return mapToJson(result);
    }

    public void log(String msg) {
        System.out.println("[JS-Config] " + msg);
    }

    // ── JSON parsing ───────────────────────────────────────────────────────
    /*
    The goal of this method is to go from the config format to as format exploitable by the Java program
     */
    @SuppressWarnings("unchecked")
    private MissionConfig parseConfig(String json) throws Exception {
        // Lightweight JSON parsing without external dependencies
        // Relies on the structure produced by the JS side

        // Use javax/Jackson if available, else fall back to manual parse
        // We use a simple recursive descent approach for our known schema
        SimpleJsonParser p = new SimpleJsonParser(json);
        Map<String, Object> root = p.parseObject();

        Satcom.activated_satcom = getBool(root, "satcomEnabled");
        EO_detection.EO_detection     = getBool(root, "eoDetectionEnabled");
        Parametres.duration = getDouble(root, "duration_mission");
        List<Map<String, Object>> satList = (List<Map<String, Object>>) root.getOrDefault("satellites", List.of());
        List<MissionConfig.SatConfig> configs = new ArrayList<>();
        AbsoluteDate epoch   = getAbsDate(root, "epoch");

        for (Map<String, Object> s : satList) {
            String name       = getString(s, "name");
            double mass       = getDouble(s, "mass");
            double alt        = getDouble(s, "altitudeKm") * 1000.0 + 6_378_137.0;
            double ecc        = getDouble(s, "eccentricity");
            double inc        = Math.toRadians(getDouble(s, "inclinationDeg"));
            double raan       = Math.toRadians(getDouble(s, "raanDeg"));
            double omega      = Math.toRadians(getDouble(s, "argPerigeeDeg"));
            double nu         = Math.toRadians(getDouble(s, "trueAnomalyDeg"));
            String satEpoch = getString(s, "epochISO");
            AbsoluteDate date_propagation= parseEpoch(satEpoch);
            if(!epoch.isBefore(date_propagation)){
                epoch = date_propagation;
            }
            Map<String, Object> subs = (Map<String, Object>) s.getOrDefault("subsystems", Map.of());
            Map<String, String> subsStr = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : subs.entrySet()) {
                subsStr.put(e.getKey(), e.getValue() == null ? "" : e.getValue().toString());
            }
            configs.add(new MissionConfig.SatConfig(name, mass, alt, ecc, inc, raan, omega, nu, subsStr,date_propagation));
        }
        Parametres.date_orekit=epoch;
        // Definition des satellites

        return new MissionConfig(configs);
    }
    /**
     * Helper: Parse ISO datetime string to Orekit AbsoluteDate
     * Format: YYYY-MM-DDTHH:mm (e.g., "2024-01-15T14:30")
     *
     * @param epochStr ISO format datetime string
     * @return AbsoluteDate in UTC
     */
    private AbsoluteDate parseEpoch(String epochStr) throws Exception {
        if (epochStr == null || epochStr.isEmpty()) {
            return new AbsoluteDate();  // Current time
        }

        try {
            // Format: "2024-01-15T14:30"
            // Replace 'T' with space and pad to full ISO 8601 if needed
            String normalized = epochStr.replace("T", " ");

            // Add seconds if missing (assume :00)
            if (!normalized.contains(":") || normalized.lastIndexOf(":") == normalized.indexOf(":")) {
                normalized += ":00";
            }

            // Parse: "2024-01-15 14:30:00"
            String[] parts = normalized.split(" ");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid epoch format: " + epochStr);
            }

            String[] dateParts = parts[0].split("-");
            String[] timeParts = parts[1].split(":");

            int year = Integer.parseInt(dateParts[0]);
            int month = Integer.parseInt(dateParts[1]);
            int day = Integer.parseInt(dateParts[2]);
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);
            int second = timeParts.length > 2 ? Integer.parseInt(timeParts[2]) : 0;

            // Create AbsoluteDate in UTC
            return new AbsoluteDate(year, month, day, hour, minute, second, TimeScalesFactory.getUTC());

        } catch (Exception e) {
            throw new Exception("Failed to parse epoch: " + epochStr, e);
        }
    }
    private boolean getBool(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof Boolean) return (Boolean) v;
        return "true".equalsIgnoreCase(String.valueOf(v));
    }

    private String getString(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : v.toString();
    }

    private AbsoluteDate getAbsDate(Map<String, Object> m, String k) {

        Object v = m.get(k);
        if (v == null) {
            throw new IllegalArgumentException("Missing date field: " + k);
        }
        String iso = v.toString().trim();
        return new AbsoluteDate(
                iso,
                TimeScalesFactory.getUTC()
        );
    }

    private double getDouble(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return 0.0;
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0.0; }
    }

    // ── Minimal JSON serializer ────────────────────────────────────────────
    private String mapToJson(Map<String, List<Map<String, String>>> data) {
        StringBuilder sb = new StringBuilder("{");
        boolean firstCat = true;
        for (Map.Entry<String, List<Map<String, String>>> cat : data.entrySet()) {
            if (!firstCat) sb.append(",");
            firstCat = false;
            sb.append("\"").append(esc(cat.getKey())).append("\":[");
            boolean firstItem = true;
            for (Map<String, String> item : cat.getValue()) {
                if (!firstItem) sb.append(",");
                firstItem = false;
                sb.append("{");
                boolean firstField = true;
                for (Map.Entry<String, String> field : item.entrySet()) {
                    if (!firstField) sb.append(",");
                    firstField = false;
                    sb.append("\"").append(esc(field.getKey())).append("\":\"")
                            .append(esc(field.getValue())).append("\"");
                }
                sb.append("}");
            }
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }

    private String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ── Minimal JSON parser ────────────────────────────────────────────────
    static class SimpleJsonParser {
        private final String src;
        private int pos = 0;

        SimpleJsonParser(String src) { this.src = src.trim(); }

        @SuppressWarnings("unchecked")
        Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWs();
            while (pos < src.length() && src.charAt(pos) != '}') {
                String key = parseString();
                skipWs(); expect(':'); skipWs();
                Object val = parseValue();
                map.put(key, val);
                skipWs();
                if (pos < src.length() && src.charAt(pos) == ',') { pos++; skipWs(); }
            }
            if (pos < src.length()) pos++; // consume '}'
            return map;
        }

        @SuppressWarnings("unchecked")
        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWs();
            while (pos < src.length() && src.charAt(pos) != ']') {
                list.add(parseValue());
                skipWs();
                if (pos < src.length() && src.charAt(pos) == ',') { pos++; skipWs(); }
            }
            if (pos < src.length()) pos++;
            return list;
        }

        private Object parseValue() {
            skipWs();
            if (pos >= src.length()) return null;
            char c = src.charAt(pos);
            if (c == '"')  return parseString();
            if (c == '{')  return parseObject();
            if (c == '[')  return parseArray();
            if (c == 't')  { pos += 4; return Boolean.TRUE; }
            if (c == 'f')  { pos += 5; return Boolean.FALSE; }
            if (c == 'n')  { pos += 4; return null; }
            // number
            int start = pos;
            while (pos < src.length() && "-0123456789.eE+".indexOf(src.charAt(pos)) >= 0) pos++;
            String num = src.substring(start, pos);
            try { return Double.parseDouble(num); } catch (Exception e) { return num; }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') break;
                if (c == '\\' && pos < src.length()) {
                    char e = src.charAt(pos++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private void expect(char c) {
            skipWs();
            if (pos < src.length() && src.charAt(pos) == c) pos++;
        }

        private void skipWs() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
        }

    }

}