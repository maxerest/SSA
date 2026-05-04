package com.example.Orbiting_object.Satellite_sub_systems;

import org.orekit.geometry.fov.FieldOfView;
import org.orekit.geometry.fov.CircularFieldOfView;
import org.orekit.geometry.fov.EllipticalFieldOfView;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import java.util.*;

/**
 * Earth Observation Sensors with Orekit FieldOfView support
 */
public class EO_sensors {

    /**
     * Enum for different types of Earth Observation sensors
     */
    public enum SensorType {
        SAR("Synthetic Aperture Radar"),
        SOUNDER("Atmospheric Sounder"),
        PUSHBROOM("Pushbroom Imaging"),
        ELECTRO_OPTICAL("Electro-Optical Camera"),
        TIR("Thermal Infrared Imaging"),
        RF("Radio Frequency Sensor");

        private final String description;

        SensorType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Inner class representing a single EO sensor configuration
     */
    public class Sensor {
        private String sensorName;
        private SensorType sensorType;
        private FieldOfView fieldOfView;    // Orekit FieldOfView object
        private double imageSize_MB;        // Size of one image in megabytes
        private double imageAcquisitionTime_sec;  // Time to acquire one image in seconds
        private double dataRate_Mbps;       // Data transmission rate in Mbps
        private double power_consumption_W; // Power consumption in watts
        private double wavelength_um;       // Wavelength in micrometers (for optical/IR)
        private int numberOfBands;         // Number of spectral bands

        // Constructor with required parameters
        public Sensor(String sensorName, SensorType sensorType, FieldOfView fieldOfView,
                      double imageSize_MB, double imageAcquisitionTime_sec) {
            this.sensorName = sensorName;
            this.sensorType = sensorType;
            this.fieldOfView = fieldOfView;
            this.imageSize_MB = imageSize_MB;
            this.imageAcquisitionTime_sec = imageAcquisitionTime_sec;
            this.dataRate_Mbps = (imageSize_MB * 8) / imageAcquisitionTime_sec;
            this.power_consumption_W = 0;
            this.wavelength_um = 0;
            this.numberOfBands = 1;
        }

        // Full constructor
        public Sensor(String sensorName, SensorType sensorType, FieldOfView fieldOfView,
                      double imageSize_MB, double imageAcquisitionTime_sec,
                      double dataRate_Mbps, double power_consumption_W,
                      double wavelength_um, int numberOfBands) {
            this.sensorName = sensorName;
            this.sensorType = sensorType;
            this.fieldOfView = fieldOfView;
            this.imageSize_MB = imageSize_MB;
            this.imageAcquisitionTime_sec = imageAcquisitionTime_sec;
            this.dataRate_Mbps = dataRate_Mbps;
            this.power_consumption_W = power_consumption_W;
            this.wavelength_um = wavelength_um;
            this.numberOfBands = numberOfBands;
        }

        // Getters
        public String getSensorName() { return sensorName; }
        public SensorType getSensorType() { return sensorType; }
        public FieldOfView getFieldOfView() { return fieldOfView; }
        public double getImageSize_MB() { return imageSize_MB; }
        public double getImageAcquisitionTime_sec() { return imageAcquisitionTime_sec; }
        public double getDataRate_Mbps() { return dataRate_Mbps; }
        public double getPower_consumption_W() { return power_consumption_W; }
        public double getWavelength_um() { return wavelength_um; }
        public int getNumberOfBands() { return numberOfBands; }
        public double getGeneratedData_MB(double duration_sec) {
            return (dataRate_Mbps * duration_sec) / 8.0;
        }
        // Setters
        public void setFieldOfView(FieldOfView fov) { this.fieldOfView = fov; }
        public void setImageSize_MB(double size) { this.imageSize_MB = size; }
        public void setImageAcquisitionTime_sec(double time) {
            this.imageAcquisitionTime_sec = time;
            this.dataRate_Mbps = (this.imageSize_MB * 8) / time;  // Update data rate
        }
        public void setDataRate_Mbps(double rate) { this.dataRate_Mbps = rate; }
        public void setPower_consumption_W(double power) { this.power_consumption_W = power; }
        public void setWavelength_um(double wavelength) { this.wavelength_um = wavelength; }
        public void setNumberOfBands(int bands) { this.numberOfBands = bands; }

        /**
         * Calculate total transmission time for one image
         */
        public double getTotalTransmissionTime_sec(double bandwidthMHz) {
            double bandwidthMbps = bandwidthMHz * 1.0;
            return (this.imageSize_MB * 8) / bandwidthMbps;
        }

        /**
         * Calculate total time to acquire and transmit one image
         */
        public double getTotalTime_sec(double bandwidthMHz) {
            return this.imageAcquisitionTime_sec + getTotalTransmissionTime_sec(bandwidthMHz);
        }

        @Override
        public String toString() {
            return String.format(
                    "Sensor: %s | Type: %s | FoV: %s | Image: %.1f MB | Acq Time: %.1f sec | Data Rate: %.2f Mbps | Bands: %d",
                    sensorName, sensorType.getDescription(), fieldOfView.getClass().getSimpleName(),
                    imageSize_MB, imageAcquisitionTime_sec, dataRate_Mbps, numberOfBands
            );
        }
    }

    // Storage for multiple sensors
    private Map<String, Sensor> sensors = new LinkedHashMap<>();

    /**
     * Add a sensor to the satellite
     */
    public void addSensor(Sensor sensor) {
        sensors.put(sensor.getSensorName(), sensor);
    }

    /**
     * Remove a sensor
     */
    public void removeSensor(String sensorName) {
        sensors.remove(sensorName);
    }

    /**
     * Get a specific sensor
     */
    public Sensor getSensor(String sensorName) {
        return sensors.get(sensorName);
    }

    /**
     * Get all sensors
     */
    public Map<String, Sensor> getAllSensors() {
        return new LinkedHashMap<>(sensors);
    }


    /**
     * Get sensor by type
     */
    public List<Sensor> getSensorsByType(SensorType type) {
        return sensors.values().stream()
                .filter(s -> s.getSensorType() == type)
                .collect(java.util.stream.Collectors.toList());
    }


    /**
     * Factory method to create pre-configured sensors with Orekit FieldOfView
     */
    public static class SensorFactory {

        /**
         * Create a SAR (Synthetic Aperture Radar) sensor with circular FOV
         */
        public static Sensor createSAR(String name) {
            // CircularFieldOfView: radius in radians = 45 degrees
            FieldOfView fov = new CircularFieldOfView(
                    Vector3D.PLUS_K,  // Along Z axis (nadir)
                    Math.toRadians(20.0),
                    0.0  // No margin
            );

            return new EO_sensors().new Sensor(
                    name, SensorType.SAR,
                    fov,
                    800.0,          // Image size: 800 MB
                    60.0,           // Acquisition time: 60 seconds
                    106.67,         // Data rate: ~107 Mbps
                    250.0,          // Power: 250 W
                    0.03,           // Wavelength: 3 cm (microwave)
                    1               // Single band
            );
        }

        /**
         * Create an Atmospheric Sounder with circular FOV
         */
        public static Sensor createSounder(String name) {
            FieldOfView fov = new CircularFieldOfView(
                    Vector3D.PLUS_K,
                    Math.toRadians(12.0),  // 12 degree radius
                    0.0
            );

            return new EO_sensors().new Sensor(
                    name, SensorType.SOUNDER,
                    fov,
                    400.0,          // Image size: 400 MB
                    240.0,          // Acquisition time: 240 seconds (~4 min)
                    13.33,          // Data rate: ~13 Mbps
                    150.0,          // Power: 150 W
                    15.0,           // Wavelength: IR bands (15 um)
                    20              // 20 spectral bands
            );
        }

        /**
         * Create a Pushbroom imaging sensor with elliptical FOV
         * (wider along-track, narrower cross-track)
         */
        public static Sensor createPushbroom(String name) {
            // EllipticalFieldOfView: along-track wider than cross-track
            FieldOfView fov = new EllipticalFieldOfView(
                    Vector3D.PLUS_K,  // Center direction (nadir)
                    Vector3D.PLUS_I,  // Along-track direction (x-axis of ellipse)
                    Math.toRadians(15.0),  // Semi-major axis: 15 degrees (along-track)
                    Math.toRadians(5.25),   // Semi-minor axis: 5.25 degrees (cross-track)
                    0.0  // No margin
            );

            return new EO_sensors().new Sensor(
                    name, SensorType.PUSHBROOM,
                    fov,
                    1200.0,         // Image size: 1200 MB
                    1020.0,         // Acquisition time: 1020 seconds (~17 min)
                    9.41,           // Data rate: ~9 Mbps
                    120.0,          // Power: 120 W
                    0.55,           // Wavelength: visible (0.55 um)
                    4               // 4 bands (RGBN)
            );
        }

        /**
         * Create an Electro-Optical camera with narrow circular FOV
         */
        public static Sensor createElectroOptical(String name) {
            FieldOfView fov = new CircularFieldOfView(
                    Vector3D.PLUS_K,
                    Math.toRadians(5.0),  // 5 degree radius (narrow)
                    0.0
            );

            return new EO_sensors().new Sensor(
                    name, SensorType.ELECTRO_OPTICAL,
                    fov,
                    50.0,           // Image size: 50 MB
                    5.0,            // Acquisition time: 5 seconds
                    80.0,           // Data rate: 80 Mbps
                    80.0,           // Power: 80 W
                    0.5,            // Wavelength: visible (0.5 um)
                    3               // 3 bands (RGB)
            );
        }

        /**
         * Create a Thermal Infrared imaging sensor with elliptical FOV
         */
        public static Sensor createTIR(String name) {
            FieldOfView fov = new EllipticalFieldOfView(
                    Vector3D.PLUS_K,
                    Vector3D.PLUS_I,
                    Math.toRadians(14.0),  // Semi-major axis: 14 degrees
                    Math.toRadians(4.75),   // Semi-minor axis: 4.75 degrees
                    0.0
            );

            return new EO_sensors().new Sensor(
                    name, SensorType.TIR,
                    fov,
                    1200.0,         // Image size: 1200 MB
                    1020.0,         // Acquisition time: 1020 seconds (~17 min)
                    9.41,           // Data rate: ~9 Mbps
                    200.0,          // Power: 200 W
                    10.0,           // Wavelength: thermal (10 um)
                    1               // Single band
            );
        }

        /**
         * Create an RF (Radio Frequency) sensor with wide circular FOV
         */
        public static Sensor createRF(String name) {
            FieldOfView fov = new CircularFieldOfView(
                    Vector3D.PLUS_K,
                    Math.toRadians(60.0),  // 60 degree radius (wide)
                    0.0
            );

            return new EO_sensors().new Sensor(
                    name, SensorType.RF,
                    fov,
                    900.0,          // Image size: 900 MB
                    180.0,          // Acquisition time: 180 seconds (~3 min)
                    40.0,           // Data rate: 40 Mbps
                    300.0,          // Power: 300 W
                    0.03,           // Wavelength: RF band (3 cm)
                    2               // 2 frequency bands
            );
        }
    }
}