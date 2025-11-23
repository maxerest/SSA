package com.example;
import com.example.Analytics_Propagator.Least_squares_batch;
import com.example.Analytics_Propagator.Type1.Propagator_1;
import com.example.Ground_stations.Ground_station;
import com.example.View.Visulations;
import org.orekit.data.DataProvider;
import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;
import org.orekit.orbits.PositionAngleType;
import org.orekit.utils.Constants;
import java.util.List;
import java.io.File;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;
import java.io.IOException;

public class App 
{       

    public static void main( String[] args )throws IOException 
    {   
        boolean propagate_real_orbit = true;
        boolean propgate_kalman_filter = false;
        boolean propagate_least_squares = false;
        
        //Recuperation des données Orekit A FAIRE EN PREMIER
        final File orekitData = new File("C:\\Users\\maxen\\Desktop\\Java\\ssa\\temp\\SSA");
        final DataProvider dirCrawler = new DirectoryCrawler(orekitData);
        DataContext.getDefault().getDataProvidersManager().addProvider(dirCrawler);
        // Definition des GS    
        Ground_station.loadStationsFromCSV();
        // Delete past CSV files
        deleteAllCsvFiles();
        // Definition des satellites
        int nb_sat =1;
        List<Parametres> liste_par_sats_real_orbit = real_orbit(nb_sat);
        List<Parametres> liste_par_sats_noisy_orbit = noisy_orbit(liste_par_sats_real_orbit);

        //Propagation of the real orbits
        if (propagate_real_orbit){
            Propagator_1 propagator_real_orbit = new Propagator_1();
            propagator_real_orbit.propagator_real_orbit(liste_par_sats_real_orbit);
        }
        if (propgate_kalman_filter){
            Propagator_1 propagator_noisy_orbit = new Propagator_1();
            propagator_noisy_orbit.propagator_noisy_orbit(liste_par_sats_noisy_orbit,liste_par_sats_real_orbit);
        }
        if (propagate_least_squares){
            for (Parametres pReal : liste_par_sats_real_orbit)
                //Creation of the estimated orbit through the least square batch method
                Visulations.export_LSB_csv(pReal,Least_squares_batch.least_squares_estimation(pReal,Ground_station.liste_GS,60));
        }

        //Launch of the python file for visualization
        Visulations.RunPythonScript(liste_par_sats_real_orbit,liste_par_sats_noisy_orbit);    
    }

    public static void deleteAllCsvFiles() throws IOException  {
        String folderPath = "C:\\Users\\maxen\\Desktop\\Java\\ssa\\temp\\SSA\\src\\main\\java\\com\\example\\View";
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(folderPath), "*.csv")) {
            stream.forEach(file -> {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    // Ignore if file doesn't exist or cannot be deleted
                }
            });
        } catch (NoSuchFileException e) {
            // Ignore if no CSV files found
        }
    }

    /**
     * Création objets satellites pour les orbites réelle de propoagation
     * @param nb_sat nb d'objets satellites à créer
     */
    public static List<Parametres> real_orbit(int nb_sat){
        boolean random_orbit=false;
        Scanner user_orbit_input = new Scanner(System.in);
        List<Parametres> liste_par_sats = new ArrayList<>();
        for (int i=0;i<nb_sat;i++){
        
        System.out.println("Do you want a random orbit for satellite "+(i+1)+"? (true/false): ");
        random_orbit= user_orbit_input.nextBoolean();

        if (random_orbit){
            System.out.println("Random orbit selected.");
            Random rand = new Random();
            double semi_axis = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + (500 + rand.nextDouble() * 1500) * 1000; // between 500 km and 2000 km
            double eccentricity = rand.nextDouble() * 0.01; // between 0 and 0.01
            double inclinaison = Math.toRadians(rand.nextDouble() * 90); // between 0 and 90 degrees
            double long_noeud_ascendant = Math.toRadians(rand.nextDouble() * 360); // between 0 and 360 degrees
            double arg_periastre = Math.toRadians(rand.nextDouble() * 360); // between 0 and 360 degrees
            double anomalie = Math.toRadians(rand.nextDouble() * 360); // between 0 and 360 degrees

            liste_par_sats.add(
                new Parametres.Builder()
                    .nom_sat("Sat_real" + (i+1))
                    .mass(2500)
                    .semi_axis(semi_axis)
                    .eccentricity(eccentricity)
                    .inclinaison(inclinaison)
                    .long_noeud_ascendant(long_noeud_ascendant)
                    .arg_periastre(arg_periastre)
                    .anomalie(anomalie)
                    .type_anomalie(PositionAngleType.MEAN)
                    .type_moteur(1)
                    .start_manoeuvre(300.0)
                    .duration_manoeuvre(360.0)
                    .build());
            continue;
        } 
        liste_par_sats.add(
            new Parametres.Builder()
                .nom_sat("Sat_real" + (i+1))
                .mass(2500)
                .semi_axis(Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 7000e3)
                .eccentricity(0.005)
                .inclinaison(Math.toRadians(50))
                .long_noeud_ascendant(Math.toRadians(90))
                .arg_periastre(Math.toRadians((i+1)*15))
                .anomalie(Math.toRadians(60 + ((i+1)*15)))
                .type_anomalie(PositionAngleType.MEAN)
                .type_moteur(1)
                .start_manoeuvre(300.0)
                .duration_manoeuvre(360.0)
                .build());        
        }
        user_orbit_input.close();
        return liste_par_sats;
    }
    
    /**
     * Création objets satellites pour la propagation avec des orbites bruitées
     * 
     * @param liste_par_sats_real_orbit liste des satellites avec des orbites réelles pour l'ajout de bruit
     */

    public static List<Parametres> noisy_orbit(List<Parametres> liste_par_sats_real_orbit) {
    List<Parametres> liste_par_sats_noise_orbit = new ArrayList<>();
    int i = 0;
    for (Parametres p : liste_par_sats_real_orbit) {

        // Build noisy orbit parameters
        Parametres noisyP = new Parametres.Builder()
            .nom_sat("Sat_noisy" + (++i))
            .mass(p.get_mass()) // keep same
            .semi_axis(p.get_semi_axis()-100000)
            .eccentricity(p.get_eccentricity())
            .inclinaison(p.get_inclinaison())
            .long_noeud_ascendant(p.get_long_noeud_ascendant())
            .arg_periastre(p.get_arg_periastre())
            .anomalie(p.get_anomalie())
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

   



