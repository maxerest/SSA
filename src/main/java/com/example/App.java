package com.example;
import com.example.Analytics_Propagator.Type1.Propagator_1;
import com.example.View.Visulations;
import org.orekit.data.DataProvider;
import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;
import org.orekit.propagation.events.AltitudeDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.OrbitType;
import org.orekit.orbits.PositionAngleType;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

import java.io.File;
/**
 * Hello world!
 *
 */

public class App 
{       
    public static void main( String[] args )
    {   //Recuperation des données Orekit A FAIRE EN PREMIER
        final File orekitData = new File("C:\\Users\\maxen\\Desktop\\Java\\ssa\\temp\\SSA");
        final DataProvider dirCrawler = new DirectoryCrawler(orekitData);
        DataContext.getDefault().getDataProvidersManager().addProvider(dirCrawler);

        // Récupération des paramètres sats, orbite
        Parametres p = new Parametres("Sats1",Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 700e3,0.001,Math.toRadians(45),Math.toRadians(30),Math.toRadians(45),Math.toRadians(60),PositionAngleType.MEAN);



        Propagator_1 propa = new Propagator_1();

        // Paramétrage du propagateur numérique
        NumericalPropagator propagator = new NumericalPropagator(propa.integrator(p));

        propagator.setOrbitType(OrbitType.KEPLERIAN);
        propagator.setInitialState(p.s_initialState);
        
        //Ajout des forces au modèles
        Propagator_1.add_force_propagator(propagator,p.area,p.cd,p.srpCrossSection, p.srpCoeff);

        // Ajout du détecteur d'altitude
        AltitudeDetector altitudeDetector = new AltitudeDetector(p.Detectionaltitude,p.earth).withHandler(new Propagator_1.Altitude_limit(p,propagator));
        propagator.addEventDetector(altitudeDetector);

        Visulations.export_csv(propagator, p);
        propagator.propagate(new AbsoluteDate(Parametres.date_orekit, Parametres.duration));
        Visulations.RunPythonScript(p);
    }


    

}

   



