package com.example.Orbiting_object.Satellite_sub_systems;

import org.hipparchus.util.FastMath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Antenna{
    private static final String URL_motors =
            "src/main/resources/subsystems/antennas.csv";
    private String name;
    private double gain;           // dBi
    private double noiseFigure;    // dB
    private double frequency;      // GHz
    private double bandwidth;      // MHz
    private double efficiency;     // %
    private double txPowerDbm;     // dBm
    private double teta3dB;
    public Antenna() {
        this.gain = 20.0;           // dBi (satellite TX antenna)
        this.noiseFigure = 2.0;     // dB
        this.frequency = 8.0;      // GHz (Ku-band uplink/downlink)
        this.bandwidth = 100;      // MHz (typical satellite transponder)
        this.efficiency = 0.60;     // 60%
        this.txPowerDbm = 40.0;     //dB for the power
        this.teta3dB= FastMath.toRadians(2.0);
    }
    public Antenna(String name,double gain, double noiseFigure, double frequency,double bandwidth,double efficiency, double txPowerDbm,double teta3dB) {
        this.name = name;
        this.gain = gain;
        this.noiseFigure = noiseFigure;
        this.frequency = frequency;
        this.bandwidth=bandwidth;
        this.efficiency=efficiency;
        this.teta3dB=FastMath.toRadians(teta3dB);
    }
    public static final Map<String, Antenna> antenna_catalogue = new LinkedHashMap<>();



    public static void loadAntennaFromCSV() throws IOException {
        antenna_catalogue.clear();
        List<String> lines = Files.readAllLines(Paths.get(URL_motors));

        if (lines.isEmpty()) {
            System.out.println("[Antennas] CSV file is empty — motors catalogue is empty.");
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
                Antenna antenna = parseAntennaRow(line, i + 1);
                antenna_catalogue.put(antenna.getName(), antenna);
                loaded++;
            }
            catch (Exception e) {
                System.err.printf("[Antenna] WARNING — skipping line %d: %s (%s)%n",
                        i + 1, line, e.getMessage());
                skipped++;
            }
        }
        System.out.printf("[Antenna] Catalogue ready: %d loaded, %d skipped.%n",
                loaded, skipped);
    }
    public static Antenna parseAntennaRow(String line, int lineNumber) {
        String[] cols = line.split(",", -1);

        if (cols.length < 8) {
            throw new IllegalArgumentException(
                    "Expected 8 columns in motors csv, found " + cols.length);
        }
        String name = cols[0].trim();
        String gainStr = cols[1].trim();
        String noiseFigureStr = cols[2].trim();
        String frequencyStr = cols[3].trim();
        String bandwidthStr = cols[4].trim();
        String efficiencyStr = cols[5].trim();
        String txPowerDbmStr = cols[6].trim();
        String teta3dBStr = cols[7].trim();

        if (name.isEmpty()) {
            throw new IllegalArgumentException("Antenna name is empty");
        }
        if (gainStr.isEmpty()) {
            throw new IllegalArgumentException("Antenna gain is empty");
        }
        if (noiseFigureStr.isEmpty()) {
            throw new IllegalArgumentException("Antenna noise figure is empty");
        }
        if (frequencyStr.isEmpty()) {
            throw new IllegalArgumentException("Antenna frequency is empty");
        }
        if (bandwidthStr.isEmpty()) {
            throw new IllegalArgumentException("Antenna bandwidth is empty");
        }
        if (efficiencyStr.isEmpty()) {
            throw new IllegalArgumentException("Antenna efficiency is empty");
        }
        if (txPowerDbmStr.isEmpty()) {
            throw new IllegalArgumentException("Antenna TX power is empty");
        }
        if (teta3dBStr.isEmpty()) {
            throw new IllegalArgumentException("Antenna theta 3dB is empty");
        }

        double gain = Double.parseDouble(gainStr);
        double noiseFigure = Double.parseDouble(noiseFigureStr);
        double frequency = Double.parseDouble(frequencyStr);
        double bandwidth = Double.parseDouble(bandwidthStr);
        double efficiency = Double.parseDouble(efficiencyStr);
        double txPowerDbm = Double.parseDouble(txPowerDbmStr);
        double teta3dB = Double.parseDouble(teta3dBStr);

        return new Antenna(
                name,
                gain,
                noiseFigure,
                frequency,
                bandwidth,
                efficiency,
                txPowerDbm,
                teta3dB
        );
    }


    public String getName() {
        return name;
    }
    public double getGain() {
        return gain;
    }

    public double getNoiseFigure() {
        return noiseFigure;
    }

    public double getFrequency() {
        return frequency;
    }

    public double getBandwidth() {
        return bandwidth;
    }

    public double getEfficiency() {
        return efficiency;
    }
    public double getteta3dB() {
        return teta3dB;
    }
    public double getTxPowerDbm(){return txPowerDbm;}


}
