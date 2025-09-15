package com.example;
import com.example.Analytics_Propagator.Type1.Propagator_1;
import com.example.View.Visulations;
import org.orekit.data.DataProvider;
import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;
import org.orekit.estimation.sequential.KalmanEstimatorBuilder;
import org.orekit.orbits.PositionAngleType;
import org.orekit.utils.Constants;
import java.util.List;
import java.util.Random;
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
        List<Parametres> liste_par_sats_real_orbit = real_orbit(nb_sat);
        List<Parametres> liste_par_sats_noisy_orbit = noisy_orbit(liste_par_sats_real_orbit);

        //Propagation of the real orbits
        Propagator_1 propagator_real_orbit = new Propagator_1();
        propagator_real_orbit.propagator_real_orbit(liste_par_sats_real_orbit);

        //Setting up and propagation of the noise orbits
        Propagator_1 propagator_noisy_orbit = new Propagator_1();
        propagator_noisy_orbit.propagator_noisy_orbit(liste_par_sats_noisy_orbit,liste_par_sats_real_orbit);

        Visulations.RunPythonScript(liste_par_sats_real_orbit,liste_par_sats_noisy_orbit);
        
    }
    public static List<Parametres> real_orbit(int nb_sat){
        
        List<Parametres> liste_par_sats = new ArrayList<>();
        for (int i=0;i<nb_sat;i++){
        // Création des paramètres pour une orbite avec infos sur modèle et earth fixées
        liste_par_sats.add(
            new Parametres.Builder()
                .nom_sat("Sat_real" + (i+1))
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
     return liste_par_sats;
    }

    public static List<Parametres> noisy_orbit(List<Parametres> liste_par_sats_real_orbit) {
    List<Parametres> liste_par_sats_noise_orbit = new ArrayList<>();
    Random rng = new Random();

    int i = 0;
    for (Parametres p : liste_par_sats_real_orbit) {
        // Define noise levels (1-sigma). Adjust as needed
        double sigmaA = 1000.0;                 // semi-major axis [m]
        double sigmaE = 1e-3;                   // eccentricity
        double sigmaI = Math.toRadians(0.1);    // inclination [rad]
        double sigmaRAAN = Math.toRadians(0.1); // RAAN [rad]
        double sigmaArgP = Math.toRadians(0.1); // argument of perigee [rad]
        double sigmaM = Math.toRadians(0.1);    // mean anomaly [rad]

        // Add Gaussian noise (rng.nextGaussian() ~ N(0,1))
        double noisyA = p.get_semi_axis() + sigmaA * rng.nextGaussian();
        double noisyE = p.get_eccentricity() + sigmaE * rng.nextGaussian();
        double noisyI = p.get_inclinaison() + sigmaI * rng.nextGaussian();
        double noisyRAAN = p.get_long_noeud_ascendant() + sigmaRAAN * rng.nextGaussian();
        double noisyArgP = p.get_arg_periastre() + sigmaArgP * rng.nextGaussian();
        double noisyM = p.get_anomalie() + sigmaM * rng.nextGaussian();

        // Build noisy orbit parameters
        Parametres noisyP = new Parametres.Builder()
            .nom_sat("Sat_noisy" + (++i))
            .mass(p.get_mass()) // keep same
            .semi_axis(noisyA)
            .eccentricity(noisyE)
            .inclinaison(noisyI)
            .long_noeud_ascendant(noisyRAAN)
            .arg_periastre(noisyArgP)
            .anomalie(noisyM)
            .type_anomalie(p.get_type_anomalie())
            .type_moteur(p.get_Type_moteur())
            .start_manoeuvre(p.get_de())
            .duration_manoeuvre(p.get_duration_manoeuvre())
            .build();

        liste_par_sats_noise_orbit.add(noisyP);
    }

    return liste_par_sats_noise_orbit;
    }
}

   



