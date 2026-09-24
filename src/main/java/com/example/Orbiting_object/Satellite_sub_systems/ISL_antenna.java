package com.example.Orbiting_object.Satellite_sub_systems;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ISL_antenna extends Antenna {

    private static final String URL_ANTENNAS =
            "src/main/resources/subsystems/ISL_antennas.csv";


    public static final Map<String, ISL_antenna> isl_antenna_catalogue =
            new LinkedHashMap<>();


    public ISL_antenna(
            String name,
            double gain,
            double noiseFigure,
            double frequency,
            double bandwidth,
            double efficiency,
            double txPowerDbm,
            double teta3dB) {

        super(
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


    public static ISL_antenna parseAntennaRow(
            String line,
            int lineNumber) {

        String[] cols = line.split(",", -1);

        if (cols.length < 8) {
            throw new IllegalArgumentException(
                    "Expected 8 columns in ISL antenna CSV, found "
                            + cols.length
            );
        }

        String nameStr        = cols[0].trim();
        String gainStr        = cols[1].trim();
        String noiseFigureStr = cols[2].trim();
        String frequencyStr   = cols[3].trim();
        String bandwidthStr   = cols[4].trim();
        String efficiencyStr  = cols[5].trim();
        String txPowerDbmStr  = cols[6].trim();
        String teta3dBStr     = cols[7].trim();

        if (nameStr.isEmpty()) {
            throw new IllegalArgumentException(
                    "ISL antenna name is empty"
            );
        }

        if (gainStr.isEmpty()) {
            throw new IllegalArgumentException(
                    "ISL antenna gain is empty"
            );
        }

        if (noiseFigureStr.isEmpty()) {
            throw new IllegalArgumentException(
                    "ISL antenna noise figure is empty"
            );
        }

        if (frequencyStr.isEmpty()) {
            throw new IllegalArgumentException(
                    "ISL antenna frequency is empty"
            );
        }

        if (bandwidthStr.isEmpty()) {
            throw new IllegalArgumentException(
                    "ISL antenna bandwidth is empty"
            );
        }

        if (efficiencyStr.isEmpty()) {
            throw new IllegalArgumentException(
                    "ISL antenna efficiency is empty"
            );
        }

        if (txPowerDbmStr.isEmpty()) {
            throw new IllegalArgumentException(
                    "ISL antenna TX power is empty"
            );
        }

        if (teta3dBStr.isEmpty()) {
            throw new IllegalArgumentException(
                    "ISL antenna theta 3dB is empty"
            );
        }


        double gain =
                Double.parseDouble(gainStr);

        double noiseFigure =
                Double.parseDouble(noiseFigureStr);

        double frequency =
                Double.parseDouble(frequencyStr);

        double bandwidth =
                Double.parseDouble(bandwidthStr);

        double efficiency =
                Double.parseDouble(efficiencyStr);

        double txPowerDbm =
                Double.parseDouble(txPowerDbmStr);

        double teta3dB =
                Double.parseDouble(teta3dBStr);


        return new ISL_antenna(
                nameStr,
                gain,
                noiseFigure,
                frequency,
                bandwidth,
                efficiency,
                txPowerDbm,
                teta3dB
        );
    }


    public static void loadAntennaFromCSV()
            throws IOException {

        isl_antenna_catalogue.clear();

        List<String> lines =
                Files.readAllLines(
                        Paths.get(URL_ANTENNAS)
                );

        if (lines.isEmpty()) {
            System.out.println(
                    "[ISL_Antenna] CSV file is empty."
            );
            return;
        }

        int loaded = 0;
        int skipped = 0;

        // Skip header
        for (int i = 1; i < lines.size(); i++) {

            String line = lines.get(i).trim();

            if (line.isEmpty() || line.startsWith("#")) {
                skipped++;
                continue;
            }

            try {

                ISL_antenna antenna = parseAntennaRow(line, i + 1);
                isl_antenna_catalogue.put(antenna.getName(), antenna);

                loaded++;

            } catch (Exception e) {

                System.err.printf("[ISL_Antenna] WARNING - skipping line %d: %s (%s)%n", i + 1, line, e.getMessage());
                skipped++;
            }
        }

        System.out.printf("[ISL_Antenna] Catalogue ready: %d loaded, %d skipped.%n", loaded, skipped);
    }

}