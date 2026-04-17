package com.example.Ground_stations;

import com.example.Analytics_Propagator.Type1.Handlers;
import com.example.Orbiting_object.Satellite;
import com.example.Parametres;
import org.hipparchus.util.FastMath;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.frames.TopocentricFrame;
import org.orekit.propagation.events.ElevationDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

public class EO_detection {
    public static boolean EO_detection=true; // false on default but gets change if needed from main
    public static Map<String, List<TreeMap<AbsoluteDate,AbsoluteDate>>> Map_area_history = new HashMap<>();
    public static Map<String,List<GeodeticPoint>> Map_area_positions = new HashMap<>();
    public EO_detection() {
        init_observable_zones();

    }
    public static void EO_usage_detection(NumericalPropagator propagator, Satellite sat) {
        for (String name : Map_area_positions.keySet()) {
            Handlers.ZoneObservationContext context = new Handlers.ZoneObservationContext();
            int i = 0;
            for (GeodeticPoint point : Map_area_positions.get(name)) {
                TopocentricFrame topoFrame = new TopocentricFrame(Parametres.earth, point, name + "_" + i);
                ElevationDetector elevationDetector = new ElevationDetector(
                        60.0,
                        1e-3,
                        topoFrame)
                        .withConstantElevation(Math.toRadians(Parametres.elevation))
                        .withHandler(new Handlers.Area_revisit_Handler(
                                name,
                                sat.get_liste_state_propa(),
                                Parametres.frame,
                                i,
                                context  // shared across all points in this zone
                        ));
                propagator.addEventDetector(elevationDetector);
                i++;
            }
        }
    }
    public static void init_observable_zones(){
        String filename = "src/main/java/com/example/RevisitFrequency/Coordinates_area_to_observe.csv";
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                String[] parts = line.split(",");
                String name = parts[0].trim();

                // Declared inside the loop — new list per zone
                List<GeodeticPoint> geodeticPoints = new ArrayList<>();
                List<TreeMap<AbsoluteDate, AbsoluteDate>> perPointHistory = new ArrayList<>();

                for (int i = 1; i < parts.length; i = i + 3) {
                    double lat = Math.toRadians(Double.parseDouble(parts[i].trim()));  // CSV is degrees
                    double lon = Math.toRadians(Double.parseDouble(parts[i + 1].trim()));
                    double alt = Double.parseDouble(parts[i + 2].trim());
                    geodeticPoints.add(new GeodeticPoint(lat, lon, alt));
                    perPointHistory.add(new TreeMap<>());
                }

                Map_area_positions.put(name, geodeticPoints);
                Map_area_history.put(name, perPointHistory);
            }
            System.out.println(Map_area_positions.keySet());
        } catch (Exception e) {
            e.printStackTrace();
        }
        }
    }
