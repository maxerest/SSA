package com.example.Orbiting_object.Satellite_sub_systems;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Motors{
    private static final String URL_motors =
            "src/main/resources/subsystems/motors.csv";
    private String name;
    private double ISP;
    private double thrust;
    private double electrical_consumption;
    public Motors(String name,double ISP,double thrust,double  electrical_consumption){
        this.name=name;
        this.ISP=ISP;
        this.thrust=thrust;
        this.electrical_consumption=electrical_consumption;
    }
    public static final Map<String, Motors> motor_catalogue = new LinkedHashMap<>();


    public static void loadMotorsFromCSV() throws IOException {
        motor_catalogue.clear();
        List<String> lines = Files.readAllLines(Paths.get(URL_motors));

        if (lines.isEmpty()) {
            System.out.println("[MOtors] CSV file is empty — motors catalogue is empty.");
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
                Motors motor = parseMotorRow(line, i + 1);
                motor_catalogue.put(motor.getName(), motor);
                loaded++;
            }
            catch (Exception e) {
                System.err.printf("[Motor] WARNING — skipping line %d: %s (%s)%n",
                        i + 1, line, e.getMessage());
                skipped++;
            }
        }
        System.out.printf("[Motors] Catalogue ready: %d loaded, %d skipped.%n",
                loaded, skipped);
    }
    public static Motors parseMotorRow(String line, int lineNumber) {
        String[] cols = line.split(",", -1);

        if (cols.length < 4) {
            throw new IllegalArgumentException(
                    "Expected 4 columns in motors csv, found " + cols.length);
        }
        String name = cols[0].trim();
        String ispStr = cols[1].trim();
        String thrustStr = cols[2].trim();
        String powerStr = cols[3].trim();

        if (name.isEmpty()) {
            throw new IllegalArgumentException("Motor name is empty");
        }
        if (ispStr.isEmpty()) {
            throw new IllegalArgumentException("Motor ISP is empty");
        }
        if (thrustStr.isEmpty()) {
            throw new IllegalArgumentException("Motor thrust is empty");
        }
        if (powerStr.isEmpty()) {
            throw new IllegalArgumentException("Motor consumption is empty");
        }
        double ISP = Double.parseDouble(ispStr);
        double thrust = Double.parseDouble(thrustStr);
        double electrical_consumption = Double.parseDouble(powerStr);
        return new Motors(name,ISP,thrust,electrical_consumption);
    }


    public String getName() {
        return name;
    }

    public double getISP() {
        return ISP;
    }

    public double getThrust() {
        return thrust;
    }

    public double getElectrical_consumption() {
        return electrical_consumption;
    }
}
