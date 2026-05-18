package com.example.Orbiting_object.Satellite_sub_systems;

import com.example.Ground_stations.EO_detection;
import org.orekit.geometry.fov.FieldOfView;
import org.orekit.geometry.fov.CircularFieldOfView;
import org.orekit.geometry.fov.EllipticalFieldOfView;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>CSV format (header + data rows):
 * <pre>
 * sensorName,sensorType,type_of_FOV,fieldOfView(°),imageSize_MB,
 * imageAcquisitionTime_sec,dataRate_Mbps,power_consumption_W,
 * wavelength_um,numberOfBands
 *
 * <p>Sensors are only loaded when {@code EO_detection.EO_detection} is {@code true}.
 */
public class EO_sensors {

    private static final String URL_SENSORS =
            "src/main/resources/subsystems/eo_sensors.csv";


    public enum SensorType {
        SAR("Synthetic Aperture Radar"),
        SOUNDER("Atmospheric Sounder"),
        PUSHBROOM("Pushbroom Imaging"),
        ELECTRO_OPTICAL("Electro-Optical Camera"),
        TIR("Thermal Infrared Imaging"),
        RF("Radio Frequency Sensor");

        private final String description;

        SensorType(String description) { this.description = description; }

        public String getDescription() { return description; }
    }

    // -----------------------------------------------------------------------
    // Sensor — static nested class (no outer-instance reference needed)
    // -----------------------------------------------------------------------

    public static class Sensor {

        private String     sensorName;
        private SensorType sensorType;
        private FieldOfView fieldOfView;
        private double     imageSize_MB;
        private double     imageAcquisitionTime_sec;
        private double     dataRate_Mbps;
        private double     power_consumption_W;
        private double     wavelength_um;
        private int        numberOfBands;

        /** Minimal constructor — derives dataRate from size and acquisition time. */
        public Sensor(String sensorName, SensorType sensorType, FieldOfView fieldOfView,
                      double imageSize_MB, double imageAcquisitionTime_sec) {
            this.sensorName               = sensorName;
            this.sensorType               = sensorType;
            this.fieldOfView              = fieldOfView;
            this.imageSize_MB             = imageSize_MB;
            this.imageAcquisitionTime_sec = imageAcquisitionTime_sec;
            this.dataRate_Mbps            = (imageSize_MB * 8) / imageAcquisitionTime_sec;
            this.power_consumption_W      = 0;
            this.wavelength_um            = 0;
            this.numberOfBands            = 1;
        }

        /** Full constructor. */
        public Sensor(String sensorName, SensorType sensorType, FieldOfView fieldOfView,
                      double imageSize_MB, double imageAcquisitionTime_sec,
                      double dataRate_Mbps, double power_consumption_W,
                      double wavelength_um, int numberOfBands) {
            this.sensorName               = sensorName;
            this.sensorType               = sensorType;
            this.fieldOfView              = fieldOfView;
            this.imageSize_MB             = imageSize_MB;
            this.imageAcquisitionTime_sec = imageAcquisitionTime_sec;
            this.dataRate_Mbps            = dataRate_Mbps;
            this.power_consumption_W      = power_consumption_W;
            this.wavelength_um            = wavelength_um;
            this.numberOfBands            = numberOfBands;
        }

        // Getters
        public String      getSensorName()               { return sensorName; }
        public SensorType  getSensorType()               { return sensorType; }
        public FieldOfView getFieldOfView()              { return fieldOfView; }
        public double      getImageSize_MB()             { return imageSize_MB; }
        public double      getImageAcquisitionTime_sec() { return imageAcquisitionTime_sec; }
        public double      getDataRate_Mbps()            { return dataRate_Mbps; }
        public double      getPower_consumption_W()      { return power_consumption_W; }
        public double      getWavelength_um()            { return wavelength_um; }
        public int         getNumberOfBands()            { return numberOfBands; }

        /** Data generated during a given observation window. */
        public double getGeneratedData_MB(double duration_sec) {
            return (dataRate_Mbps * duration_sec) / 8.0;
        }

        // Setters
        public void setFieldOfView(FieldOfView fov)         { this.fieldOfView = fov; }
        public void setImageSize_MB(double size)            { this.imageSize_MB = size; }
        public void setImageAcquisitionTime_sec(double time) {
            this.imageAcquisitionTime_sec = time;
            this.dataRate_Mbps = (this.imageSize_MB * 8) / time;  // keep derived field consistent
        }
        public void setDataRate_Mbps(double rate)           { this.dataRate_Mbps = rate; }
        public void setPower_consumption_W(double power)    { this.power_consumption_W = power; }
        public void setWavelength_um(double wavelength)     { this.wavelength_um = wavelength; }
        public void setNumberOfBands(int bands)             { this.numberOfBands = bands; }

        /** Time to transmit one image over a given RF bandwidth. */
        public double getTotalTransmissionTime_sec(double bandwidthMHz) {
            return (this.imageSize_MB * 8) / bandwidthMHz;
        }

        /** Total time to acquire AND transmit one image. */
        public double getTotalTime_sec(double bandwidthMHz) {
            return this.imageAcquisitionTime_sec + getTotalTransmissionTime_sec(bandwidthMHz);
        }

        @Override
        public String toString() {
            return String.format(
                    "Sensor: %s | Type: %s | FoV: %s | Image: %.1f MB"
                            + " | Acq Time: %.1f sec | Data Rate: %.2f Mbps | Bands: %d",
                    sensorName, sensorType.getDescription(),
                    fieldOfView.getClass().getSimpleName(),
                    imageSize_MB, imageAcquisitionTime_sec, dataRate_Mbps, numberOfBands);
        }
    }

    // -----------------------------------------------------------------------
    // Static sensor_catalogue — one shared registry for the whole simulation
    // -----------------------------------------------------------------------

    public static final Map<String, Sensor> sensor_catalogue = new LinkedHashMap<>();

    /**
     * Load all sensors from the CSV file into the static sensor_catalogue.
     * No-op (silent) if {@code EO_detection.EO_detection} is {@code false}.
     * Safe to call multiple times — subsequent calls clear and reload the sensor_catalogue.
     *
     * @throws IOException if the CSV file cannot be read
     */
    public static void loadSensorsFromCSV() throws IOException {

        if (!EO_detection.EO_detection) {
            return;
        }

        sensor_catalogue.clear();

        List<String> lines = Files.readAllLines(Paths.get(URL_SENSORS));

        if (lines.isEmpty()) {
            System.out.println("[EO_sensors] CSV file is empty — sensor_catalogue is empty.");
            return;
        }

        int loaded  = 0;
        int skipped = 0;

        // Line 0 is the header — start at 1
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            if (line.isEmpty() || line.startsWith("#")) {
                skipped++;
                continue;
            }

            try {
                Sensor sensor = parseSensorRow(line, i + 1);
                sensor_catalogue.put(sensor.getSensorName(), sensor);
                loaded++;
                }
            catch (Exception e) {
                System.err.printf("[EO_sensors] WARNING — skipping line %d: %s (%s)%n",
                        i + 1, line, e.getMessage());
                skipped++;
            }
        }
        System.out.printf("[EO_sensors] Catalogue ready: %d loaded, %d skipped.%n",
                loaded, skipped);
    }

    /**
     * Look up a sensor definition in the sensor_catalogue by name.
     *
     * @param sensorName name as it appears in the CSV
     * @return the {@link Sensor}, or {@code null} if not found
     */
    public static Sensor getCatalogueSensor(String sensorName) {
        return sensor_catalogue.get(sensorName);
    }

    /**
     * Returns an unmodifiable view of the full sensor_catalogue.
     */
    public static Map<String, Sensor> getSensor_catalogue() {
        return Collections.unmodifiableMap(sensor_catalogue);
    }

    /**
     * Returns all sensor_catalogue sensors of a given type.
     */
    public static List<Sensor> getCatalogueSensorsByType(SensorType type) {
        return sensor_catalogue.values().stream()
                .filter(s -> s.getSensorType() == type)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Instance active sensors — what this particular satellite carries
    // -----------------------------------------------------------------------

    private final Map<String, Sensor> activeSensors = new LinkedHashMap<>();

    /**
     * Assign a catalogued sensor to this satellite.
     * The sensor must have been loaded into the sensor_catalogue first via
     * {@link #loadSensorsFromCSV()}.
     *
     * @param sensorName name of the sensor to add
     * @throws IllegalArgumentException if the name is not found in the sensor_catalogue
     */
    public void addSensor(String sensorName) {
        Sensor s = sensor_catalogue.get(sensorName);
        if (s == null) {
            throw new IllegalArgumentException(
                    "Sensor '" + sensorName + "' not found in sensor_catalogue. "
                            + "Available: " + sensor_catalogue.keySet());
        }
        activeSensors.put(sensorName, s);
    }

    /**
     * Remove a sensor from this satellite (sensor_catalogue is unaffected).
     */
    public void removeSensor(String sensorName) {
        activeSensors.remove(sensorName);
    }

    /**
     * Get one of this satellite's active sensors by name.
     *
     * @return the {@link Sensor}, or {@code null} if not assigned to this satellite
     */
    public Sensor getActiveSensor(String sensorName) {
        return activeSensors.get(sensorName);
    }

    /**
     * Returns an unmodifiable view of this satellite's active sensors.
     */
    public Map<String, Sensor> getActiveSensors() {
        return Collections.unmodifiableMap(activeSensors);
    }

    /**
     * Returns this satellite's active sensors filtered by type.
     */
    public List<Sensor> getActiveSensorsByType(SensorType type) {
        return activeSensors.values().stream()
                .filter(s -> s.getSensorType() == type)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Private CSV parsing utilities (static — pure functions, no state)
    // -----------------------------------------------------------------------

    /**
     * Parse one CSV data row into a {@link Sensor}.
     *
     * <p>Column layout (0-indexed):
     * <pre>
     *  0  sensorName
     *  1  sensorType
     *  2  type_of_FOV          ("circular" | "elliptical")
     *  3  fieldOfView(°)       single angle OR "majorDeg/minorDeg"
     *  4  imageSize_MB
     *  5  imageAcquisitionTime_sec
     *  6  dataRate_Mbps
     *  7  power_consumption_W
     *  8  wavelength_um
     *  9  numberOfBands
     * </pre>
     */
    private static Sensor parseSensorRow(String line, int lineNumber) {

        String[] cols = line.split(",", -1);

        if (cols.length < 10) {
            throw new IllegalArgumentException(
                    "Expected 10 columns, found " + cols.length);
        }

        String sensorName       = cols[0].trim();
        String sensorTypeStr    = cols[1].trim().toUpperCase();
        String fovType          = cols[2].trim().toLowerCase();
        String fovAngleStr      = cols[3].trim();
        double imageSize_MB     = Double.parseDouble(cols[4].trim());
        double imageAcqTime_sec = Double.parseDouble(cols[5].trim());
        double dataRate_Mbps    = Double.parseDouble(cols[6].trim());
        double power_W          = Double.parseDouble(cols[7].trim());
        double wavelength_um    = Double.parseDouble(cols[8].trim());
        int    numberOfBands    = Integer.parseInt(cols[9].trim());

        if (sensorName.isEmpty()) {
            throw new IllegalArgumentException("sensorName is empty");
        }

        SensorType sensorType;
        try {
            sensorType = SensorType.valueOf(sensorTypeStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown sensorType '" + cols[1].trim() + "'. "
                            + "Valid values: " + Arrays.toString(SensorType.values()));
        }

        FieldOfView fov = buildFieldOfView(fovType, fovAngleStr, lineNumber);

        return new Sensor(
                sensorName, sensorType, fov,
                imageSize_MB, imageAcqTime_sec,
                dataRate_Mbps, power_W,
                wavelength_um, numberOfBands);
    }

    /**
     * Build an Orekit {@link FieldOfView} from type and angle specification.
     *
     * @param fovType     {@code "circular"} or {@code "elliptical"}
     * @param fovAngleStr for circular: degrees, e.g. {@code "20.0"};
     *                    for elliptical: {@code "majorDeg/minorDeg"}, e.g. {@code "15.0/5.25"}
     * @param lineNumber  CSV line number, used only in error messages
     */
    private static FieldOfView buildFieldOfView(String fovType, String fovAngleStr,
                                                int lineNumber) {
        switch (fovType) {

            case "circular": {
                double halfAngleDeg = Double.parseDouble(fovAngleStr);
                return new CircularFieldOfView(
                        Vector3D.PLUS_K,
                        Math.toRadians(halfAngleDeg),
                        0.0);
            }

            case "elliptical": {
                String[] parts = fovAngleStr.split("/");
                if (parts.length != 2) {
                    throw new IllegalArgumentException(
                            "Elliptical FOV must be 'majorDeg/minorDeg', got: '"
                                    + fovAngleStr + "'");
                }
                double semiMajorDeg = Double.parseDouble(parts[0].trim());
                double semiMinorDeg = Double.parseDouble(parts[1].trim());
                return new EllipticalFieldOfView(
                        Vector3D.PLUS_K,          // centre direction (nadir)
                        Vector3D.PLUS_I,          // along-track direction
                        Math.toRadians(semiMajorDeg),
                        Math.toRadians(semiMinorDeg),
                        0.0);
            }

            default:
                throw new IllegalArgumentException(
                        "Unknown type_of_FOV '" + fovType
                                + "' at line " + lineNumber
                                + ". Expected 'circular' or 'elliptical'.");
        }
    }

}