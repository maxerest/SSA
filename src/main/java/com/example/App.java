package com.example;
import com.example.Analytics_Propagator.Type1.Propagator_1;
import com.example.View.Visulations;
import org.orekit.data.DataProvider;
import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;
import org.orekit.propagation.events.AltitudeDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.orbits.OrbitType;
import org.orekit.time.AbsoluteDate;
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
        Parametres p = new Parametres();
        
        // Paramétrage du propagateur numérique
        NumericalPropagator propagator = new NumericalPropagator(p.integrator());

        propagator.setOrbitType(OrbitType.KEPLERIAN);
        propagator.setInitialState(p.s_initialState);
        
        //Ajout des forces au modèles
        Propagator_1.add_force_propagator(propagator,p.area,p.cd,p.srpCrossSection, p.srpCoeff);
        // Ajout du détecteur d'altitude
        AltitudeDetector altitudeDetector = new AltitudeDetector(p.Detectionaltitude,p.earth).withHandler(new Propagator_1.Altitude_limit(p,propagator));
        propagator.addEventDetector(altitudeDetector);

        Visulations.export_csv(propagator, p.date_orekit);
        propagator.propagate(new AbsoluteDate(p.date_orekit, p.duration));
        Visulations.RunPythonScript();
    }


    

}

   



