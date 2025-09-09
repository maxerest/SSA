package com.example;
import com.example.Analytics_Propagator.Type1.Propagator_1;
import com.example.Manoeuvre.Manoeuvre;
import com.example.View.Visulations;
import org.orekit.data.DataProvider;
import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;
import org.orekit.propagation.events.AltitudeDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.orbits.OrbitType;
import org.orekit.orbits.PositionAngleType;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import java.util.List;
import java.io.File;
import java.util.ArrayList;
/**
 * Hello world!
 *
 */

public class App 
{       
    public static void main( String[] args )
    {   
        //Recuperation des données Orekit A FAIRE EN PREMIER
        final File orekitData = new File("C:\\Users\\maxen\\Desktop\\Java\\ssa\\temp\\SSA");
        final DataProvider dirCrawler = new DirectoryCrawler(orekitData);
        DataContext.getDefault().getDataProvidersManager().addProvider(dirCrawler);

        int nb_sat =1;
        List<Parametres> liste_par_sats = new ArrayList<>();
        for (int i=0;i<nb_sat;i++){
        // Création des paramètres pour une orbite avec infos sur modèle et earth fixées
            liste_par_sats.add(new Parametres("Sat"+ (i+1),2500,Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 7000e3,0.005,Math.toRadians(50*(i+1)),Math.toRadians(30),Math.toRadians(45),Math.toRadians(60),PositionAngleType.MEAN));
        }

        Propagator_1 propa = new Propagator_1();
        // Paramétrage du propagateur numérique
        for  (Parametres p :  liste_par_sats){
            NumericalPropagator propagator = new NumericalPropagator(propa.integrator(p));
            propagator.setOrbitType(OrbitType.KEPLERIAN);
            propagator.setInitialState(p.s_initialState); 
            //Ajout des forces au modèles
            Propagator_1.add_force_propagator(propagator,p.area,p.cd,p.srpCrossSection, p.srpCoeff);
            // Ajout du détecteur d'altitude
            AltitudeDetector altitudeDetector = new AltitudeDetector(p.Detectionaltitude,Parametres.earth).withHandler(new Propagator_1.Altitude_limit(p,propagator));
            propagator.addEventDetector(altitudeDetector);
            propagator= Manoeuvre.Moteur_1(p,propagator);
            Visulations.export_csv(propagator, p);
            propagator.propagate(new AbsoluteDate(Parametres.date_orekit, Parametres.duration)); 
        }
        Visulations.RunPythonScript(liste_par_sats);
    }


    

}

   



