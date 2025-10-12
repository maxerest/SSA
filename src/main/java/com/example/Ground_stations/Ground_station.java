package com.example.Ground_stations;
import com.example.Parametres;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.frames.TopocentricFrame;
import org.orekit.orbits.Orbit;
import org.hipparchus.util.FastMath;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;
import org.orekit.estimation.iod.IodGauss;
import org.orekit.estimation.measurements.AngularAzEl;
import org.orekit.estimation.measurements.GroundStation;
import org.orekit.estimation.measurements.ObservableSatellite;



public class Ground_station {
    public static List<GroundStation> liste_GS=new ArrayList<>();
    
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
                GroundStation station_gs = new GroundStation(station);
                liste_GS.add(station_gs);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isVisibleFromStation(GroundStation station, SpacecraftState s, AbsoluteDate current_date){
        
        TopocentricFrame topo = station.getBaseFrame();
        double elevation = topo.getElevation(
                s.getPVCoordinates().getPosition(), // satellite position
                Parametres.frame,                    // satellite frame
                current_date                         // observation time
        );
        return elevation > Parametres.elevation;
    }

    public static boolean hasVisibleStations(SpacecraftState s, AbsoluteDate current_date) {
        for (GroundStation station : liste_GS) {
            if (isVisibleFromStation(station, s,current_date)) {
                //System.err.println("Visible from station: " + station.getName());
                return true;
            }
        }
        return false;
    }
    public static GroundStation which_station_visible(SpacecraftState s, AbsoluteDate current_date) {
        for (GroundStation station : liste_GS) {
            if (isVisibleFromStation(station, s,current_date)) {
                return station;
            }
        }
        return null;
    }
    public static PVCoordinates getIodGaussInstance(AbsoluteDate t0, AbsoluteDate t1, AbsoluteDate t2,GroundStation station,ObservableSatellite sat,Parametres pReal) {
        
    // Example: az/el in radians
        double[] azel0 = new double[2];
        double[] azel1 = new double[2];
        double[] azel2 = new double[2];

        PVCoordinates pv0 = pReal.get_Cartesian_Orbit().getPVCoordinates(Parametres.date_orekit.shiftedBy(t0), Parametres.frame);
        azel0[0] = station.getBaseFrame().getAzimuth(pv0.getPosition(), Parametres.frame, t0);
        azel0[1] = station.getBaseFrame().getElevation(pv0.getPosition(), Parametres.frame, t0);
        
        PVCoordinates pv1 = pReal.get_Cartesian_Orbit().getPVCoordinates(Parametres.date_orekit.shiftedBy(t1), Parametres.frame);
        azel1[0] = station.getBaseFrame().getAzimuth(pv1.getPosition(), Parametres.frame, t1);
        azel1[1] = station.getBaseFrame().getElevation(pv1.getPosition(), Parametres.frame, t1);
        
        PVCoordinates pv2 = pReal.get_Cartesian_Orbit().getPVCoordinates(Parametres.date_orekit.shiftedBy(t2), Parametres.frame);
        azel2[0] = station.getBaseFrame().getAzimuth(pv2.getPosition(), Parametres.frame, t2);
        azel2[1] = station.getBaseFrame().getElevation(pv2.getPosition(), Parametres.frame, t2);
        
        Random rand = new Random();
        double noise = 1e-4;
        // Added noise to the true positions to help Gauss to converge
        for (int i=0; i<2; i++) {
            azel0[i] += rand.nextGaussian() * noise;
            azel1[i] += rand.nextGaussian() * noise;
            azel2[i] += rand.nextGaussian() * noise;
        }
        
    // Measurement uncertainties (example: 1 milliradian)
        double[] sigma = { noise, noise};
        double[] weight = { 10, 10 };
        
        AngularAzEl meas0 = new AngularAzEl(station, t0, azel0, sigma, weight, sat);
        AngularAzEl meas1 = new AngularAzEl(station, t1, azel1, sigma, weight, sat);
        AngularAzEl meas2 = new AngularAzEl(station, t2, azel2, sigma, weight, sat);
        // ------------------ Gauss example ------------------
        
        IodGauss gauss = new IodGauss(Parametres.mu);
        // IodGauss also returns an Orbit at the central observation time (t1)
        Orbit gaussOrbit = gauss.estimate(Parametres.frame, meas0, meas1, meas2);
        if (gaussOrbit == null) {
            throw new RuntimeException("IodGauss could not estimate a valid orbit. Check geometry and inputs.");
        }
        PVCoordinates pvG = gaussOrbit.getPVCoordinates(Parametres.frame);

        return pvG;
    }
}