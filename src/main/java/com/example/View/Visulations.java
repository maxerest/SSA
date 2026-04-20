package com.example.View;

import com.example.Ground_stations.Ground_station;
import com.example.TLE.My_TLE;
import com.example.Analytics_Propagator.Type1.Propagator_1;
import com.example.Orbiting_object.*;
import com.example.Analytics_Propagator.Least_squares_batch;
import com.example.Parametres;

import java.io.*;

import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.orekit.frames.Frame;
import org.orekit.frames.Transform;
import org.orekit.models.earth.GeoMagneticField;
import org.orekit.orbits.Orbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.linear.RealVector;

import java.nio.file.*;
import java.util.*;

import org.orekit.estimation.measurements.EstimatedMeasurementBase;
import org.orekit.estimation.measurements.GroundStation;
import org.orekit.estimation.measurements.ObservedMeasurement;
import org.orekit.frames.FramesFactory;

import javax.swing.plaf.synth.SynthTextAreaUI;

public class Visulations {
    private static Map<String, PrintWriter> csvWriters = new HashMap<>();
    private static String currentFileName = null;

    public static void update_csv_orbital_realsat (String name,String name_sat,Orbit orbit_sat, AbsoluteDate currentdate,boolean trigger){
        name = name + "_Orbital_param";
        if (!csvWriters.containsKey(name)) {
            csvWriters.get(name).close();
        }
        try {
            PrintWriter v = csvWriters.get(name);
            v.printf(Locale.US,"%s,%f,%f,%f,%f,%d,%d%n",name_sat, orbit_sat.getA(), orbit_sat.getE(), orbit_sat.getI(), (double)currentdate.durationFrom(Parametres.date_orekit), trigger ? 1 : 0, 0);
            v.flush(); // Ensure data is written immediately
        } catch (Exception e) {
            System.out.println("erreur param orbit CSV");
            e.printStackTrace();
        }


    }
    public static void update_CSV_xyz_realsat(String name,String name_sat,Vector3D pos, AbsoluteDate currentdate,boolean trigger,boolean station_visible,String name_station) {

        try {
            PrintWriter v = csvWriters.get(name);
            v.printf(Locale.US,"%s,%f,%f,%f,%f,%d,%b,%s%n",name_sat, pos.getX(), pos.getY(), pos.getZ(), (double)currentdate.durationFrom(Parametres.date_orekit), trigger ? 1 : 0, station_visible,name_station);
            v.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void create_CSV_files(String s){
        List<String> names = List.of(s, s+"_Orbital_param");

        for ( String name_temp :names){
        try {
            if (csvWriters.containsKey(name_temp)) {
                csvWriters.get(name_temp).close();

            }
            currentFileName = "src/main/resources/CSV_exports/real_sat/" + name_temp + ".csv";
            PrintWriter csvWriter = new PrintWriter(new FileWriter(currentFileName));
            if (name_temp.contains("_Orbital_param")){
                csvWriter.println("name_sat,a,e,i,t,firing,detected_by_GS,nom_station");
            }else{
                csvWriter.println("name_sat,x,y,z,t,firing,detected_by_GS,nom_station");
            }
            csvWriters.put(name_temp, csvWriter);
            csvWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        }

    }
    public static void closeCSV() {

        for (Map.Entry<String , PrintWriter> entry : csvWriters.entrySet()) {
            PrintWriter v = entry.getValue();
            v.close();
        }
    }
    /**
     * Méthode pour l'export des mesures de la méthode LSB en CSV
     * 
     * @param measurements l'ensemble des mesures faites par le GS qui sont
     *                     utilisées pour faire l'export
     * @param p            Paramètres du satellite
     */
    public static void export_LSB_csv(Orbiting_object p,
            SortedSet<EstimatedMeasurementBase<?>> measurements) {
        String sat = p.get_Name().replaceAll("real", "noisy");
        // Stocker les mesures par (date, station) pour les associer correctement
        Map<String, Map<AbsoluteDate, Double>> rangeByStationDate = new HashMap<>();
        Map<String, Map<AbsoluteDate, double[]>> azElByStationDate = new HashMap<>();

        // Boucle sur les measurements pour les organiser
        for (EstimatedMeasurementBase<?> estimatedMeasurement : measurements) {
            ObservedMeasurement<?> measurement = estimatedMeasurement.getObservedMeasurement();
            String type = measurement.getClass().getSimpleName();
            double[] values = measurement.getObservedValue();
            AbsoluteDate date = measurement.getDate();

            String stationKey = null;

            if (type.equals("Range")) {
                org.orekit.estimation.measurements.Range rangeMeas = (org.orekit.estimation.measurements.Range) measurement;
                stationKey = rangeMeas.getStation().getBaseFrame().getName();

                if (!rangeByStationDate.containsKey(stationKey)) {
                    rangeByStationDate.put(stationKey, new HashMap<>());
                }

                rangeByStationDate.get(stationKey).put(date, values[0]);

            } else if (type.equals("AngularAzEl")) {
                org.orekit.estimation.measurements.AngularAzEl azElMeas = (org.orekit.estimation.measurements.AngularAzEl) measurement;
                stationKey = azElMeas.getStation().getBaseFrame().getName();

                if (!azElByStationDate.containsKey(stationKey)) {
                    azElByStationDate.put(stationKey, new HashMap<>());
                }
                azElByStationDate.get(stationKey).put(date,
                        new double[] { values[0], values[1] }); // [azimuth, elevation]
            }
        }

        // deuxieme boucle passe au travers des GS pour écrire les fichiers CSV. Chaque
        // station output un CSV avec l'ensemble de ses mesures
        for (String stationKey : azElByStationDate.keySet()) {

            File csvFile = new File("src/main/resources/CSV_exports/LBS/" + sat + "_" + stationKey + "_LSB.csv");
            if (csvFile.exists()) {
                csvFile.delete();
            }
            try (FileWriter fw = new FileWriter(csvFile, true);
                    PrintWriter writer = new PrintWriter(fw);) {

                writer.println("x,y,z,t,firing,detected_by_GS");

                Map<AbsoluteDate, double[]> azElMeasurements = azElByStationDate.get(stationKey);
                Map<AbsoluteDate, Double> rangeMeasurements = rangeByStationDate.get(stationKey);

                if (rangeMeasurements == null) {
                    System.out.println("ATTENTION: Station " + stationKey +
                            " a des mesures AzEl mais pas de Range!");
                    continue;
                }

                // Pour chaque mesure AzEl de cette station
                for (AbsoluteDate date : azElMeasurements.keySet()) {
                    double[] azEl = azElMeasurements.get(date);
                    Double range = rangeMeasurements.get(date);

                    if (range == null) {
                        System.out.println("ATTENTION: Pas de Range à la date " + date +
                                " pour station " + stationKey);
                        continue;
                    }

                    // Recuperer la station au sol correspondante (a voir pour optimiser peu
                    // efficace)
                    GroundStation station = null;
                    for (EstimatedMeasurementBase<?> est : measurements) {
                        ObservedMeasurement<?> m = est.getObservedMeasurement();
                        if (m instanceof org.orekit.estimation.measurements.AngularAzEl) {
                            org.orekit.estimation.measurements.AngularAzEl azElMeas = (org.orekit.estimation.measurements.AngularAzEl) m;
                            if (azElMeas.getDate().equals(date) &&
                                    azElMeas.getStation().getBaseFrame().getName().equals(stationKey)) {
                                station = azElMeas.getStation();
                                break;
                            }
                        }
                    }

                    if (station == null) {
                        continue;
                    }

                    // Calculer XYZ
                    double[] xyz = Least_squares_batch.azElRangeToECEF(
                            azEl[0], azEl[1], range, station, date);

                    double t = date.durationFrom(Parametres.date_orekit);

                    writer.printf(Locale.US, "%.6f,%.6f,%.6f,%f,%d,%d%n",
                            xyz[0], xyz[1], xyz[2], t, 0, 1);

                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * Méthode pour la création des fichiers CSV des TLE. Tous les sats de la TLE
     * dans un seul fichier puor la visualisation
     *
     * @param states       ; Liste des SpacecraftState des TLE
     * @param selectedType : Type de TLE sélectionné dans Celestrak
     */
    public static void export_TLE_intial_position(Collection<SpacecraftState> states, My_TLE.TLEType selectedType) {
        String filename = "src/main/resources/CSV_exports/real_sat/TLE.csv";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            // Ecrire l'entête
            writer.write("x,y,z,t,firing,detected_by_GS\n");
            for (SpacecraftState state : states) {
                PVCoordinates pv = state.getPVCoordinates(FramesFactory.getEME2000());
                // double time = state.getDate().durationFrom(Parametres.date_orekit);
                String line = String.format(Locale.US, "%.2f,%.2f,%.2f,%.6f,%s,%s\n",
                        pv.getPosition().getX(), pv.getPosition().getY(), pv.getPosition().getZ(), 0.000000, 0, 0);
                writer.write(line);

            }
        } catch (IOException e) {
            System.err.println("Erreur lors de la création du fichier du sat TLE: " + e.getMessage());
        }
    }

    /** Initialize CSV files for Kalman filter estimated satellites
     * 
     * @param pList : Liste of Parametres of all the satellites
     */
    public static void export_csv_kalman_init(List<Satellite> pList) {
        for (Orbiting_object p : pList) {
            String sat = p.get_Name();
            File csvFile = new File("src/main/resources/CSV_exports/Kalman/" + sat + ".csv");
            if (csvFile.exists()) {
                csvFile.delete();
            }
            try (PrintWriter writer = new PrintWriter(csvFile)) {
                writer.println("x,y,z,t,firing,detected_by_GS");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /** Initialize CSV file for Kalman filter before detection, fill with 0 to allow smooth visualization
     * 
     * @param p : Parametres of the satellite
     */
    public static void write_csv_before_detection(Satellite p) {
        String sat = p.get_Name();
        File csvFile = new File("src/main/resources/CSV_exports/Kalman/" + sat + ".csv");
        try (FileWriter fw = new FileWriter(csvFile, true);
                PrintWriter writer = new PrintWriter(fw)) {
            writer.printf(Locale.US, "0,0,0,0,0,0%n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /** Add a step to the CSV file for Kalman filter CSV file for visualization
     * 
     * @param p : Parametres of the satellite
     * @param t : time of the step
     * @param temp_s : state vector at time t
     * @param detected_by_GS : boolean indicating if the satellite is detected by a ground station at time t
     */
    public static void export_csv_kalman_add_step(Satellite p, double t, RealVector temp_s, boolean detected_by_GS) {
        // Position
        double x = temp_s.getEntry(0);
        double y = temp_s.getEntry(1);
        double z = temp_s.getEntry(2);
        // Velocity
        Vector3D pos = new Vector3D(x, y, z);
        boolean trigger = p.is_firing(Parametres.date_orekit.shiftedBy(t));
        File csvFile = new File("src/main/resources/CSV_exports/Kalman/"
                + p.get_Name() + ".csv");
        try (FileWriter fw = new FileWriter(csvFile, true);
                PrintWriter writer = new PrintWriter(fw)) {
            writer.printf(Locale.US, "%f,%f,%f,%f,%d,%d%n", pos.getX(), pos.getY(), pos.getZ(), t, trigger ? 1 : 0,
                    detected_by_GS ? 1 : 0);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    /** Run the python script for visualization
     * 
     */
    public static void RunPythonScript() {

        // Compute the rotation angle between ITRF and EME2000 at epoch
        // This gives the Earth's orientation (GAST) at the start of propagation
        Frame itrf    = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
        Frame eme2000 = FramesFactory.getEME2000();

        Transform t           = itrf.getTransformTo(eme2000, Parametres.date_orekit);
        double[] axis         = t.getRotation().getAxis(RotationConvention.VECTOR_OPERATOR).toArray();
        double   gastAngleDeg = Math.toDegrees(t.getRotation().getAngle());

        // The Z-component of the axis tells us if the rotation is positive or negative
        if (axis[2] < 0) gastAngleDeg = -gastAngleDeg;

        // Build up the full command
        List<String> cmd = new ArrayList<>();
        cmd.add("python");
        cmd.add("src/main/java/com/example/View/Visualisation.py");
        cmd.add(String.valueOf(gastAngleDeg));   // argv[1]
        cmd.add(String.valueOf(Parametres.date_orekit.durationFrom(AbsoluteDate.J2000_EPOCH))); // argv[2] — optional, for reference
        process_builder(cmd);


    }
    public static void Python_graph_orbital_param(){
        // Build up the full command
        List<String> cmd = new ArrayList<>();
        cmd.add("python");
        cmd.add("src/main/java/com/example/View/graphs.py");
        process_builder(cmd);
    }
    public static void process_builder(List<String> cmd){
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while (( line = reader.readLine()) != null) {
                    System.out.println("[PY] " + line);
                }
            }
            int exitCode = process.waitFor();
            System.out.println("Exited with code: " + exitCode);

        } catch (IOException | InterruptedException e) {
            System.out.println("Error while launching the py 3d visualisation");
        }
    }

    public static void Python_graph_collision(){
            // Build up the full command
            List<String> cmd = new ArrayList<>();
            cmd.add("python");
            cmd.add("src/main/java/com/example/SSA/CSV_per_sat/graph_collision.py");
            process_builder(cmd);
    }
    public static void deleteAllCsvFiles() throws IOException  {
        List<String> list_folder_to_clear=new ArrayList<>();
        list_folder_to_clear.add("src/main/resources/CSV_exports/real_sat/");
        for (String folderPath:list_folder_to_clear){
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
    }
    public static void export_observation_to_csv(String name, AbsoluteDate start, AbsoluteDate end, double duration, String sat_name ) {

        String filename = "src/main/resources/EO detection/observations.csv";
        try (FileWriter fw = new FileWriter(filename, true);
             BufferedWriter bw = new BufferedWriter(fw)) {

            bw.write(name + "," + start.toString() + "," + end.toString() + "," + duration+ "," + sat_name);
            bw.newLine();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void init_observation_csv() {
        String filename = "src/main/resources/EO detection/observations.csv";
        try (FileWriter fw = new FileWriter(filename, false);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write("zone_name,start_time,end_time,duration_s,name_sat_doing_observation");
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void Python_EO_detection(){
        // Build up the full command
        List<String> cmd = new ArrayList<>();
        cmd.add("python");
        cmd.add("src/main/java/com/example/RevisitFrequency/graphs_observations.py");
        process_builder(cmd);
    }
}
