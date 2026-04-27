package com.example.Ground_stations;
import com.example.Analytics_Propagator.Type1.Handlers;
import com.example.Analytics_Propagator.Type1.Propagator_1;
import com.example.Parametres;
import com.example.Orbiting_object.*;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.frames.TopocentricFrame;
import org.orekit.orbits.Orbit;
import org.hipparchus.util.FastMath;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.ElevationDetector;
import org.orekit.propagation.events.EventDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.PVCoordinates;
import org.orekit.estimation.iod.IodGauss;
import org.orekit.estimation.measurements.AngularAzEl;
import org.orekit.estimation.measurements.GroundStation;
import org.orekit.estimation.measurements.ObservableSatellite;



public class Ground_station {
    public static List<GroundStation_physical> liste_GS=new ArrayList<>();
    public static boolean satcom_activated=false; // false on default but gets change if needed from main

    public static class GroundStation_physical extends GroundStation {
        String name;
        double antenna_size;
        double antenna_gain;
        double teta3dB=2;       //degrees
        double noiseFigureDb = 3.0;
        double noiseBandwidthMhz = 50.0;
        double temperatureK = 290.0;
        public Map <SpacecraftState,Boolean> map_visibility_from_sat=new HashMap<>();
        public GeodeticPoint geo_point;

        public GroundStation_physical(TopocentricFrame baseFrame,String name, GeodeticPoint point) {
            super(baseFrame);
            this.antenna_gain=10; //dB
            this.antenna_size=10; //m
            this.name=name;
            this.geo_point=point;
        }

        public String getName() {
            return name;
        }

        public double getAntenna_size() {
            return antenna_size;
        }

        public double getAntenna_gain() {
            return antenna_gain;
        }

        public double getNoiseFigureDb() {
            return noiseFigureDb;
        }

        public double getNoiseBandwidthMhz() {
            return noiseBandwidthMhz;
        }

        public double getTemperatureK() {
            return temperatureK;
        }
        public double getteta3dB(){
            return teta3dB;
        }
        public void get_visibility(SpacecraftState s){

        }
    }
    // Call this method at program initialization to load ground stations from CSV within the public static list liste_GS
    public static void loadStationsFromCSV() {
        String filename = "src/main/resources/GS_coordinates.csv";
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
                double lonNorm = lon > 180 ? lon - 360 : lon;
                double alt = Double.parseDouble(parts[3].trim());
                double station_activation= Double.parseDouble(parts[4].trim());
                if (station_activation==0){
                    // Station is deactivated, skip it
                    continue;
                    }
                GeodeticPoint point= new GeodeticPoint(
                        FastMath.toRadians(lat),
                        FastMath.toRadians(lonNorm),
                        alt
                );
                TopocentricFrame station = new TopocentricFrame(
                        Parametres.earth,point
                        ,
                        name
                );
                GroundStation_physical station_gs = new GroundStation_physical(station,name,point);
                
                liste_GS.add(station_gs);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Check if the satellite is visible from the ground station
     * @param station : GroundStation where the satellite visibility is checked
     * @param s : SpacecraftState of the satellite to be checked
     * @param current_date : Time of check
     * @return boolean : true if visible, false otherwise
     */
    private static boolean isVisibleFromStation(GroundStation_physical station, SpacecraftState s, AbsoluteDate current_date){

        TopocentricFrame topo = station.getBaseFrame();
        double elevation = topo.getElevation(
                s.getPVCoordinates().getPosition(), // satellite position
                Parametres.frame,                    // satellite frame
                current_date                         // observation time
        );
        return elevation > Parametres.elevation;
    }
    public static List<GroundStation_physical> get_list_visible_GS(SpacecraftState s){
        List<GroundStation_physical> list_visible_station=new ArrayList<>();
        for (GroundStation_physical GS : liste_GS){
            if (isVisibleFromStation(GS,s,s.getDate())){
                list_visible_station.add(GS);
            }
        }
        return list_visible_station;
    }

    /**
     * Check if at least one ground station can see the satellite
     * @param s : SpacecraftState of the satellite to be checked
     * @param current_date : Time of check
     * @return boolean : true if at least one station can see the satellite, false otherwise
     */

    public static boolean hasVisibleStations(SpacecraftState s, AbsoluteDate current_date) {
        for (GroundStation_physical station : liste_GS) {
            if (isVisibleFromStation(station, s,current_date)) {
                return true;
            }
        }
        return false;
    }
    /**
     * Return the first ground station that can see the satellite
     * @param s : SpacecraftState of the satellite to be checked
     * @param current_date : Time of check
     * @return GroundStation : first station that can see the satellite, null if none can see it
     */
    public static GroundStation_physical which_station_visible(SpacecraftState s, AbsoluteDate current_date) {
        for (GroundStation_physical station : liste_GS) {
            if (isVisibleFromStation(station, s,current_date)) {
                return station;
            }
        }
        return null;
    }
    /**
     * Implementation of the IOD Gauss method to get an initial orbit determination from 3 angular measurements
     * @param t0 : time of first measurement
     * @param t1 : time of second measurement
     * @param t2 : time of third measurement
     * @param station : GroundStation where the measurements are taken
     * @param sat : ObservableSatellite object
     * @param pReal : Parametres object of the real satellite
     * @return PVCoordinates : estimated PVCoordinates at time t1
     */
    public static PVCoordinates getIodGaussInstance(AbsoluteDate t0, AbsoluteDate t1, AbsoluteDate t2,GroundStation station,ObservableSatellite sat,Orbiting_object pReal) {    
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
        //Change it if no valid orbit is found, or too close in time
        double noise = 1e-1;
        // Added noise to the true positions to help Gauss to converge
        for (int i=0; i<2; i++) {
            azel0[i] += rand.nextGaussian() * noise;
            azel1[i] += rand.nextGaussian() * noise;
            azel2[i] += rand.nextGaussian() * noise;
        }
        
    // Measurement uncertainties 
        double[] sigma = { noise, noise};
    // Measurement weights (example: higher weight = more confidence => longer conv)
        double[] weight = { 1, 1 };
        
        AngularAzEl meas0 = new AngularAzEl(station, t0, azel0, sigma, weight, sat);
        AngularAzEl meas1 = new AngularAzEl(station, t1, azel1, sigma, weight, sat);
        AngularAzEl meas2 = new AngularAzEl(station, t2, azel2, sigma, weight, sat);
 
        
        IodGauss gauss = new IodGauss(Parametres.mu);
        // IodGauss also returns an Orbit at the central observation time (t1)
        Orbit gaussOrbit = gauss.estimate(Parametres.frame, meas0, meas1, meas2);
        if (gaussOrbit == null) {
            throw new RuntimeException("IodGauss could not estimate a valid orbit. Check geometry and inputs.");
        }
        PVCoordinates pvG = gaussOrbit.getPVCoordinates(Parametres.frame);

        return pvG;
    }
    public static void satcom_station_link(NumericalPropagator propagator){
        final double maxcheck  = 60.0;
        final double threshold =  0.001;
        for (GroundStation_physical GS : liste_GS ){
             final EventDetector station_visibility =
                     new ElevationDetector(maxcheck, threshold, GS.getBaseFrame())
                             .withConstantElevation(Parametres.elevation)
                             .withHandler((s, d, increasing) -> {

                                 // Flip the boolean inside the map for the satellite and ground stations combo showing visibility or not
                                 GS.map_visibility_from_sat.put(s,increasing);
                                 return Action.CONTINUE;
                             });
             propagator.addEventDetector(station_visibility);
        }
    }

   }
