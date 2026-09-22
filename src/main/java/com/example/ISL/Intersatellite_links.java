package com.example.ISL;

import com.example.Analytics_Propagator.Type1.Handlers;
import com.example.App;
import com.example.Orbiting_object.Satellite;
import com.example.Parametres;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.propagation.events.InterSatDirectViewDetector;

import java.util.List;

public class Intersatellite_links {

    public static void Detector_2_sats(Satellite satellite1,Satellite satellite2){
        InterSatDirectViewDetector LoSdetector = new InterSatDirectViewDetector((OneAxisEllipsoid) Parametres.earth,satellite2.getPropagator()).withMaxCheck(30.0)
                .withThreshold(1.0e-3)
                .withHandler(new Handlers.IntersatelliteLinksHandler(satellite1,satellite2));
    System.out.println("initialized "+ satellite2.get_Name()+" and "+ satellite1.get_Name());
    satellite1.getPropagator().addEventDetector(LoSdetector);
    }
    public static void ISL_initilisation(){
        List<Satellite> satelliteList= App.liste_par_sats_real_orbit;
        for (int i = 0; i < satelliteList.size(); i++) {
            for (int j = i + 1; j < satelliteList.size(); j++) {
                Satellite sat1 = satelliteList.get(i);
                Satellite sat2 = satelliteList.get(j);
                Detector_2_sats(sat1, sat2);
            }
        }
    }

}
