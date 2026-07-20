package com.example;

import com.example.Analytics_Propagator.Least_squares_batch;
import com.example.Analytics_Propagator.Type1.Propagator_1;
import com.example.Ground_stations.EO_detection;
import com.example.Ground_stations.Satcom;
import com.example.Orbiting_object.*;
import com.example.Ground_stations.Ground_station;
import com.example.Orbiting_object.Satellite_sub_systems.Antenna;
import com.example.Orbiting_object.Satellite_sub_systems.EO_sensors;
import com.example.Orbiting_object.Satellite_sub_systems.Motors;
import com.example.SSA.Patera_detection;
import com.example.TLE.My_TLE;
import com.example.View.View3D.Visualizer3DServer;
import com.example.View.Visulations;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.data.DataProvider;
import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.Transform;
import org.orekit.orbits.PositionAngleType;
import com.example.Mission_config.MissionConfig;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.IERSConventions;

import java.util.*;
import java.io.File;
import java.io.IOException;

public class App
{   public static List<MissionConfig> liste_config = new ArrayList<>();
    public static List<Satellite> liste_par_sats_real_orbit;
    public static void main(String[] args) throws IOException {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down — closing CSV writers...");
            Visulations.closeCSV();
        }));
        //Recuperation des données Orekit à FAIRE EN PREMIER
        final File orekitData = new File("orekit-data");
        final boolean visualisation_2D = true;
        final boolean visualisation_3D = true;

        final DataProvider dirCrawler = new DirectoryCrawler(orekitData);
        DataContext.getDefault().getDataProvidersManager().addProvider(dirCrawler);
        //Start the websocket where the program is handled of the globe as a starting point
        new Thread(() -> new Visualizer3DServer().launch()).start();
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
                    .motor(p.getMotor())
                    .build();

            liste_par_sats_noise_orbit.add(noisyP);
        }

        return liste_par_sats_noise_orbit;
    }
    public static void runSimulation(MissionConfig missionConfig) throws IOException {
        boolean propagate_real_orbit = true;
        boolean propagate_kalman_filter = false;
        boolean propagate_least_squares = false;
        boolean TLE_visualisation = false;
        boolean TLE_propagation=false;
        boolean py_3d_visualizations=false;
        boolean py_graphs_visualizations=false;
        boolean check_collision = false;
        // Definition des GS
        Ground_station.loadStationsFromCSV();


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

            Propagator_1.propagator_real_orbit(liste_par_sats_real_orbit);
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
            //Visulations.Python_graph_orbital_param();
        }
        if(EO_detection.EO_detection){
            Visulations.Python_EO_detection();
        }
    }
    public static List<Satellite> real_orbit(MissionConfig missionConfig) throws IOException {
        Frame itrf = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
        Vector3D position_sat_static;
        SpacecraftState temp_state;
        List<Satellite> liste_par_sats = new ArrayList<>();
        Motors.loadMotorsFromCSV();
        Visulations.create_static_position_file();
        for (MissionConfig.SatConfig satConfig : missionConfig.satellites) {
              liste_par_sats.add(new Satellite.Builder()
                    .nom_sat(satConfig.name)
                    .mass(satConfig.mass)
                    .semi_axis(satConfig.semiAxis)
                    .eccentricity(satConfig.eccentricity)
                    .inclinaison(satConfig.inclination)
                    .long_noeud_ascendant(satConfig.raan)
                    .arg_periastre(satConfig.argPerigee)
                    .anomalie(satConfig.trueAnomaly)
                    .type_anomalie(PositionAngleType.TRUE)
                    .motor(Motors.motor_catalogue.get(satConfig.subsystems.get("MOTORS")))
                    .eo_sensor(EO_sensors.sensor_catalogue.get(satConfig.subsystems.get("EO_SENSORS")))
                    .antenna(Antenna.antenna_catalogue.get(satConfig.subsystems.get("ANTENNAS")))
                    .date_initialState(satConfig.date_propagation)
                    .build());
            temp_state=liste_par_sats.getLast().get_s_initialState();
            Transform toItrf = temp_state.getFrame().getTransformTo(itrf, temp_state.getDate());
            position_sat_static=toItrf.transformPosition(temp_state.getPosition());
            Visulations.export_initial_position(liste_par_sats.getLast().get_Name(),position_sat_static.getX(),position_sat_static.getY(),position_sat_static.getZ());
        }
        return liste_par_sats;
    }
}

   



