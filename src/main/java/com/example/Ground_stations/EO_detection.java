package com.example.Ground_stations;

import com.example.Analytics_Propagator.Type1.Handlers;
import com.example.Orbiting_object.Satellite;
import com.example.Orbiting_object.Satellite_sub_systems.EO_sensors;
import com.example.Parametres;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.TopocentricFrame;
import org.orekit.frames.Transform;
import org.orekit.geometry.fov.CircularFieldOfView;
import org.orekit.geometry.fov.DoubleDihedraFieldOfView;
import org.orekit.geometry.fov.FieldOfView;
import org.orekit.propagation.events.ElevationDetector;
import org.orekit.propagation.events.EventDetector;
import org.orekit.propagation.events.FieldOfViewDetector;
import org.orekit.propagation.events.VisibilityTrigger;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.PVCoordinatesProvider;
import org.orekit.utils.TimeStampedPVCoordinates;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EO_detection {
    public static boolean EO_detection=false; // false on default but gets change if needed from main
    public static Map<String, List<TreeMap<AbsoluteDate,AbsoluteDate>>> Map_area_history = new HashMap<>();
    public static Map<String,List<GeodeticPoint>> Map_area_positions = new HashMap<>();

    public EO_detection() {
        init_observable_zones();
        EO_detection=true;
    }
    public static void EO_usage_detection(NumericalPropagator propagator, Satellite sat) {
        for (String name : Map_area_positions.keySet()) {
            int i = 0;
            for (String sensor_name:sat.get_sensor().getAllSensors().keySet()){
                FieldOfView fov=sat.get_sensor_FoV(sensor_name);

                for (GeodeticPoint point : Map_area_positions.get(name)) {

                    TopocentricFrame tcf = new TopocentricFrame(Parametres.earth, point, name + "_" + i);

                    EventDetector detector = Handlers.buildAreaRevisitDetector(tcf,fov,60.0,name,i,sat,Parametres.frame);

                    propagator.addEventDetector(detector);
                    i++;
                }
            }
        }
    }
    public static void init_observable_zones(){
        String filename = "src/main/resources/EO detection/Coordinates_area_to_observe.csv";
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
                    double lat = Double.parseDouble(parts[i].trim());
                    double lonDeg = Double.parseDouble(parts[i + 1].trim());
                    double alt = Double.parseDouble(parts[i + 2].trim());
                    double lonNorm = lonDeg > 180 ? lonDeg - 360 : lonDeg;
                    geodeticPoints.add(new GeodeticPoint(
                            Math.toRadians(lat),
                            Math.toRadians(lonNorm),
                            alt
                    ));
                    perPointHistory.add(new TreeMap<>());
                }

                Map_area_positions.put(name, geodeticPoints);
                Map_area_history.put(name, perPointHistory);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        }
    }
