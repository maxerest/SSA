package com.example;

import com.example.Analytics_Propagator.Least_squares_batch;
import com.example.Analytics_Propagator.Type1.Propagator_1;
import com.example.Orbiting_object.*;
import com.example.Ground_stations.Ground_station;
import com.example.TLE.My_TLE;
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
        boolean propagate_kalman_filter = false;
        boolean propagate_least_squares = true;
        boolean TLE_visualisation = false;
        boolean py_3d_visulations=true;
        boolean py_graphs_visulations=true;

        //Recuperation des données Orekit A FAIRE EN PREMIER
        final File orekitData = new File("C:\\Users\\maxen\\Desktop\\Java\\ssa\\temp\\SSA");
        final DataProvider dirCrawler = new DirectoryCrawler(orekitData);
        DataContext.getDefault().getDataProvidersManager().addProvider(dirCrawler);
        // Definition des GS    
        Ground_station.loadStationsFromCSV();
        // Delete past CSV files
        deleteAllCsvFiles();
        Satellite test_sat =new Satellite.Builder().cd(10).build();
        if (TLE_visualisation)
            My_TLE.choixTLE();
        
        if (propagate_real_orbit){
            // Definition des satellites
            int nb_sat =1;
            List<Satellite> liste_par_sats_real_orbit = real_orbit(nb_sat);
            Propagator_1 propagator_real_orbit = new Propagator_1();
            propagator_real_orbit.propagator_real_orbit(liste_par_sats_real_orbit);
            List<Satellite> liste_par_sats_noisy_orbit = null;

            if (propagate_kalman_filter){
                liste_par_sats_noisy_orbit = noisy_orbit(liste_par_sats_real_orbit);
                Propagator_1 propagator_noisy_orbit = new Propagator_1();
                propagator_noisy_orbit.propagator_noisy_orbit(liste_par_sats_noisy_orbit,liste_par_sats_real_orbit);
            }
            if (propagate_least_squares){
                for (Satellite pReal : liste_par_sats_real_orbit)
                    //Creation of the estimated orbit through the least square batch method
                    Visulations.export_LSB_csv(pReal,Least_squares_batch.least_squares_estimation(pReal,Ground_station.liste_GS,60));
            }  
        }

        if(py_3d_visulations){
        Visulations.RunPythonScript();
        }
        if(py_graphs_visulations){
            Visulations.Python_graph_orbital_param();
        }
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
    public static List<Satellite> real_orbit(int nb_sat){
        Scanner user_orbit_input = new Scanner(System.in);
        List<Satellite> liste_par_sats = new ArrayList<>();

        for (int i = 0; i<nb_sat; i++){
        liste_par_sats.add(
            new Satellite.Builder()
                .nom_sat("Sat_real" + (i+1))
                .mass(2500)
                .semi_axis(Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 300000)
                .eccentricity(0.005)
                .inclinaison(Math.toRadians(96.6))
                .long_noeud_ascendant(Math.toRadians(90))
                .arg_periastre(Math.toRadians((i+1)*15))
                .anomalie(Math.toRadians(60 + ((i+1)*15)))
                .type_anomalie(PositionAngleType.MEAN)
                .type_moteur(1)
                .start_manoeuvre(300.0)
                .duration_manoeuvre(1)
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

    public static List<Satellite> noisy_orbit(List<Satellite> liste_par_sats_real_orbit) {
    List<Satellite> liste_par_sats_noise_orbit = new ArrayList<>();
    int i = 0;
    for (Satellite p : liste_par_sats_real_orbit) {

        // Build noisy orbit parameters
        Satellite noisyP = new Satellite.Builder()
            .nom_sat("Sat_noisy" + (++i))
            .mass(p.get_mass()) // keep same
            .semi_axis(p.get_semi_axis()-100000)
            .eccentricity(p.get_eccentricity())
            .inclinaison(p.get_inclinaison())
            .long_noeud_ascendant(p.get_long_noeud_ascendant())
            .arg_periastre(p.get_arg_periastre())
            .anomalie(p.get_anomalie())
            .type_anomalie(p.get_type_anomalie())
            .type_moteur(p.getType_moteur())
            .start_manoeuvre(p.getStart_manoeuvre())
            .duration_manoeuvre(p.getDuration_manoeuvre())
            .build();

        liste_par_sats_noise_orbit.add(noisyP);
    }

    return liste_par_sats_noise_orbit;
    }
}

   



