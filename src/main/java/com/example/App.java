package com.example;

import com.example.Analytics_Propagator.Least_squares_batch;
import com.example.Analytics_Propagator.Type1.Handlers;
import com.example.Analytics_Propagator.Type1.Propagator_1;
import com.example.Ground_stations.EO_detection;
import com.example.Ground_stations.Satcom;
import com.example.Manoeuvre.Manoeuvre;
import com.example.Orbiting_object.*;
import com.example.Ground_stations.Ground_station;
import com.example.SSA.Patera_detection;
import com.example.TLE.My_TLE;
import com.example.View.SatelliteTrackerUI;
import com.example.View.Visulations;
import javafx.application.Application;
import org.orekit.data.DataProvider;
import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;
import org.orekit.models.earth.EarthShape;
import org.orekit.orbits.PositionAngleType;
import org.orekit.utils.Constants;

import java.util.*;
import java.io.File;
import java.nio.file.*;
import java.io.IOException;

public class App 
{
    public static void main( String[] args )throws IOException 
    {

        boolean propagate_real_orbit = true;
        boolean propagate_kalman_filter = false;
        boolean propagate_least_squares = false;
        boolean TLE_visualisation = false;
        boolean TLE_propagation=false;
        boolean py_3d_visualizations=false;
        boolean py_graphs_visualizations=false;
        boolean check_collision = false;
        boolean satcom_gs_communication =false;
        boolean EO_detection=true;
        int nb_sat =1;
        //Recuperation des données Orekit à FAIRE EN PREMIER
        final File orekitData = new File("orekit-data");
        final DataProvider dirCrawler = new DirectoryCrawler(orekitData);
        DataContext.getDefault().getDataProvidersManager().addProvider(dirCrawler);
        // Definition des GS    
        Ground_station.loadStationsFromCSV();
        Manoeuvre.Motor.initialize_list();
        // Delete past CSV files
        Visulations.deleteAllCsvFiles();
        if (TLE_visualisation)
            My_TLE.choixTLE();
        if (TLE_propagation){
            My_TLE.propagation();
            if (check_collision){
                My_TLE.collision_TLE();
            }
        }
        if (propagate_real_orbit){
            if (satcom_gs_communication){
                Ground_station.satcom_activated=true;
            }
            if (EO_detection){
                new EO_detection();
            }
            // Definition des satellites
            List<Satellite> liste_par_sats_real_orbit = real_orbit(nb_sat);
            Propagator_1.propagator_real_orbit(liste_par_sats_real_orbit,satcom_gs_communication);
            List<Satellite> liste_par_sats_noisy_orbit;

            if (check_collision){
                Patera_detection.check_per_sat_collision(liste_par_sats_real_orbit);
            }
            if (propagate_kalman_filter){
                liste_par_sats_noisy_orbit = noisy_orbit(liste_par_sats_real_orbit);
                Propagator_1.propagator_noisy_orbit(liste_par_sats_noisy_orbit,liste_par_sats_real_orbit);
            }

            if (propagate_least_squares){
                for (Satellite pReal : liste_par_sats_real_orbit)
                    //Creation of the estimated orbit through the least square batch method
                    Visulations.export_LSB_csv(pReal,Least_squares_batch.least_squares_estimation(pReal,Ground_station.liste_GS,60));
            }
        }

        if(py_3d_visualizations){
        Visulations.RunPythonScript();
        }
        if(py_graphs_visualizations){
            Visulations.Python_graph_orbital_param();
        }
        if(EO_detection){
            Visulations.Python_EO_detection();
            //Application.launch(SatelliteTrackerUI.class, args);
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
                .nom_sat("ISS" + (i+1))
                .mass(2500)
                .semi_axis(Constants.WGS84_EARTH_EQUATORIAL_RADIUS+420000)
                .eccentricity(0.0002267)
                .inclinaison(Math.toRadians(51.64))
                .long_noeud_ascendant(Math.toRadians(90))
                .arg_periastre(Math.toRadians(0))
                .anomalie(Math.toRadians(0))
                .type_anomalie(PositionAngleType.TRUE)
                .motor_name("Moteur_2")
                .build());
            liste_par_sats.add(
                    new Satellite.Builder()
                            .nom_sat("Sat_real_" + (2))
                            .mass(2500)
                            .semi_axis(26600000)
                            .eccentricity(0.74)
                            .inclinaison(Math.toRadians(63.435))
                            .long_noeud_ascendant(Math.toRadians(0))
                            .arg_periastre(Math.toRadians(270))
                            .anomalie(Math.toRadians(0))
                            .type_anomalie(PositionAngleType.TRUE)
                            .motor_name("Moteur_2")
                            .build());
            liste_par_sats.add(
                    new Satellite.Builder()
                            .nom_sat("Sat_real_" + (3))
                            .mass(2500)
                            .semi_axis(26600000)
                            .eccentricity(0.74)
                            .inclinaison(Math.toRadians(63.435))
                            .long_noeud_ascendant(Math.toRadians(90))
                            .arg_periastre(Math.toRadians(270))
                            .anomalie(Math.toRadians(0))
                            .type_anomalie(PositionAngleType.TRUE)
                            .motor_name("Moteur_2")
                            .build());
            liste_par_sats.add(
                    new Satellite.Builder()
                            .nom_sat("Sat_real_" + (4))
                            .mass(2500)
                            .semi_axis(26600000)
                            .eccentricity(0.74)
                            .inclinaison(Math.toRadians(63.435))
                            .long_noeud_ascendant(Math.toRadians(180))
                            .arg_periastre(Math.toRadians(270))
                            .anomalie(Math.toRadians(0))
                            .type_anomalie(PositionAngleType.TRUE)
                            .motor_name("Moteur_2")
                            .build());
            liste_par_sats.add(
                    new Satellite.Builder()
                            .nom_sat("Sat_real_" + (5))
                            .mass(2500)
                            .semi_axis(26600000)
                            .eccentricity(0.74)
                            .inclinaison(Math.toRadians(63.435))
                            .long_noeud_ascendant(Math.toRadians(270))
                            .arg_periastre(Math.toRadians(270))
                            .anomalie(Math.toRadians(240))
                            .type_anomalie(PositionAngleType.TRUE)
                            .motor_name("Moteur_2")
                            .build());


        }

        /*
        liste_par_sats.add(
                new Satellite.Builder()
                        .nom_sat("Sat_real_2")
                        .mass(2500)
                        .semi_axis(24396159)
                        .eccentricity(0.5)
                        .inclinaison(Math.toRadians(180))
                        .long_noeud_ascendant(Math.toRadians(180))
                        .arg_periastre(Math.toRadians(180))
                        .anomalie(Math.toRadians(200))
                        .type_anomalie(PositionAngleType.TRUE)
                        .motor_name("Moteur_2")
                        .build());
        /*
        liste_par_sats.add(
                new Satellite.Builder()
                        .nom_sat("Sat_real_3")
                        .mass(2500)
                        .semi_axis(24396159)
                        .eccentricity(0.5)
                        .inclinaison(Math.toRadians(180))
                        .long_noeud_ascendant(Math.toRadians(180))
                        .arg_periastre(Math.toRadians(90))
                        .anomalie(Math.toRadians(180))
                        .type_anomalie(PositionAngleType.MEAN)
                        .motor_name("Moteur_2")
                        .build());




        liste_par_sats.add(
                new Satellite.Builder()
                        .nom_sat("Sat_real GTO")
                        .mass(2500)
                        .semi_axis(28000000)
                        .eccentricity(0.7285)
                        .inclinaison(Math.toRadians(0.05))
                        .long_noeud_ascendant(Math.toRadians(0))
                        .arg_periastre(Math.toRadians(180))
                        .anomalie(Math.toRadians(180))
                        .type_anomalie(PositionAngleType.TRUE)
                        .type_moteur(1)
                        .start_manoeuvre(86400)
                        .duration_manoeuvre(0)
                        .build());

         */
        user_orbit_input.close();
        return liste_par_sats;
    }
    
    /**
     * Création objets satellites pour la propagation avec des orbites bruitées
     * 
     * @param liste_par_sats_real_orbit liste des satellites avec des orbites réelles pour l'ajout de bruit
     */

    public static List<Satellite> noisy_orbit(List<Satellite> liste_par_sats_real_orbit) {
    List<Satellite> liste_par_sats_noise_orbit = new LinkedList<>();
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
            .motor_name(p.get_Motor_name())
            .build();

        liste_par_sats_noise_orbit.add(noisyP);
    }

    return liste_par_sats_noise_orbit;
    }
}

   



