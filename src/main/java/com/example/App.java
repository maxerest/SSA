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

        int nb_sat =2;
        List<Parametres> liste_par_sats = new ArrayList<>();
        for (int i=0;i<nb_sat;i++){
        // Création des paramètres pour une orbite avec infos sur modèle et earth fixées
        liste_par_sats.add(
            new Parametres.Builder()
                .nom_sat("Sat" + (i+1))
                .mass(2500)
                .semi_axis(Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 7000e3)
                .eccentricity(0.005)
                .inclinaison(Math.toRadians(50))
                .long_noeud_ascendant(Math.toRadians(30))
                .arg_periastre(Math.toRadians(45))
                .anomalie(Math.toRadians(60 + ((i+1)*15)))
                .type_anomalie(PositionAngleType.MEAN)
                .type_moteur(1)
                .start_manoeuvre(300.0)
                .duration_manoeuvre(360.0)
                .build());        
        }
        Propagator_1 propa = new Propagator_1();
        // Paramétrage du propagateur numérique
        for  (Parametres p :  liste_par_sats){
            NumericalPropagator propagator = new NumericalPropagator(propa.integrator(p));
            propagator.setOrbitType(OrbitType.KEPLERIAN);
            propagator.setInitialState(p.s_initialState); 
            //Ajout des forces au modèles
            Propagator_1.add_force_propagator(propagator,p.get_area(),p.get_cd(),p.get_srpCrossSection(), p.get_srpCoeff());
            // Ajout du détecteur d'altitude
            AltitudeDetector altitudeDetector = new AltitudeDetector(p.Detectionaltitude,Parametres.earth).withHandler(new Propagator_1.Altitude_limit(p,propagator));
            propagator.addEventDetector(altitudeDetector);
            p.manoeuvre.lancement_manoeuvre(p, propagator);
            Visulations.export_csv(propagator, p);
            propagator.propagate(new AbsoluteDate(Parametres.date_orekit, Parametres.duration)); 
        }
        Visulations.RunPythonScript(liste_par_sats);
    }


    

}

   



