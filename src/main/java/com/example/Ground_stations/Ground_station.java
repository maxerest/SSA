package com.example.Ground_stations;
import com.example.Parametres;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.frames.TopocentricFrame;
import org.hipparchus.util.FastMath;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;

public class Ground_station {
    public static List<TopocentricFrame> liste_GS=new ArrayList<>();
    
    // Call this method at program initialization
    public static void loadStationsFromCSV() {
        String filename = "src/main/java/com/example/Ground_stations/GS_coordinates.csv";
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                if (firstLine) {  // skip header
                    firstLine = false;
                    continue;
                }
                String[] parts = line.split(",");
                String name = parts[0].trim();
                double lat = Double.parseDouble(parts[1].trim());
                double lon = Double.parseDouble(parts[2].trim());
                double alt = Double.parseDouble(parts[3].trim());
                double station_activation= Double.parseDouble(parts[4].trim());
                if (station_activation==0){
                    continue;
                    }
                TopocentricFrame station = new TopocentricFrame(
                        Parametres.earth,
                        new GeodeticPoint(
                                FastMath.toRadians(lat),
                                FastMath.toRadians(lon),
                                alt
                        ),
                        name
                );
                liste_GS.add(station);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isVisibleFromStation(TopocentricFrame station, SpacecraftState s, AbsoluteDate current_date){
        
        return station.getElevation(s.getPosition(), Parametres.frame, current_date)>Parametres.elevation;
    }

    public static boolean hasVisibleStations(SpacecraftState s, AbsoluteDate current_date) {
        for (TopocentricFrame station : liste_GS) {
            if (isVisibleFromStation(station, s,current_date)) {
                //System.err.println("Visible from station: " + station.getName());
                return true;
            }
        }
        return false;
    }
}