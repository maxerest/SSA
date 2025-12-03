package com.example.View;

import com.example.TLE.My_TLE;
import com.example.Analytics_Propagator.Type1.Propagator_1;
import com.example.Analytics_Propagator.Least_squares_batch;
import com.example.Parametres;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.linear.RealVector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import org.orekit.estimation.measurements.EstimatedMeasurementBase;
import org.orekit.estimation.measurements.GroundStation;
import org.orekit.estimation.measurements.ObservedMeasurement;
import org.orekit.frames.FramesFactory;

public class Visulations {

    /**
     * Création objets satellites pour les orbites réelle de propoagation
     * 
     * @param propagator propgateur utilisé pour la simulation qui sera utilisée
     *                   pour l'export
     * @param p          Paramètres du satellite qui sera utilisé dans le propagator
     *                   pour faire un export csv
     */

    public static void export_csv(NumericalPropagator propagator, Parametres p) {
        String sat = p.get_Name();
        File csvFile = new File("src/main/java/com/example/View/" + sat + ".csv");
        if (csvFile.exists()) {
            csvFile.delete();
        }
        try (PrintWriter writer = new PrintWriter(csvFile)) {
            writer.println("x,y,z,t,firing,detected_by_GS");
            propagator.getMultiplexer().add(60, new Propagator_1.Propagation_step(sat, p));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Méthode pour l'export des mesures de la méthode LSB en CSV
     * 
     * @param measurements l'ensemble des mesures faites par le GS qui sont
     *                     utilisées pour faire l'export
     * @param p            Paramètres du satellite
     */
    public static void export_LSB_csv(Parametres p,
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

            File csvFile = new File("src/main/java/com/example/View/" + sat + "_" + stationKey + "_LSB.csv");
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
    public static void export_TLE_intial_position(List<SpacecraftState> states, My_TLE.TLEType selectedType) {
        String filename = "src/main/java/com/example/View/TLE_sat_" + selectedType + ".csv";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            // Ecrire l'entête
            writer.write("x,y,z,t,firing,detected_by_GS\n");

            for (SpacecraftState state : states) {
                PVCoordinates pv = state.getPVCoordinates(FramesFactory.getGCRF());
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
    public static void export_csv_kalman_init(List<Parametres> pList) {
        for (Parametres p : pList) {
            String sat = p.get_Name();
            File csvFile = new File("src/main/java/com/example/View/" + sat + ".csv");
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
    public static void write_csv_before_detection(Parametres p) {
        String sat = p.get_Name();
        File csvFile = new File("src/main/java/com/example/View/" + sat + ".csv");
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
    public static void export_csv_kalman_add_step(Parametres p, double t, RealVector temp_s, boolean detected_by_GS) {
        // Position
        double x = temp_s.getEntry(0);
        double y = temp_s.getEntry(1);
        double z = temp_s.getEntry(2);
        // Velocity
        Vector3D pos = new Vector3D(x, y, z);
        boolean triger = p.manoeuvre.getTriggers().isFiring(Parametres.date_orekit.shiftedBy(t), null);
        File csvFile = new File("C:\\Users\\maxen\\Desktop\\Java\\ssa\\temp\\SSA\\src\\main\\java\\com\\example\\View\\"
                + p.get_Name() + ".csv");
        try (FileWriter fw = new FileWriter(csvFile, true);
                PrintWriter writer = new PrintWriter(fw)) {
            writer.printf(Locale.US, "%f,%f,%f,%f,%d,%d%n", pos.getX(), pos.getY(), pos.getZ(), t, triger ? 1 : 0,
                    detected_by_GS ? 1 : 0);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    /** Run the python script for visualization
     * 
     */
    public static void RunPythonScript() {
        try {
            // Build up the full command
            List<String> cmd = new ArrayList<>();
            cmd.add("python");
            cmd.add("src/main/java/com/example/View/Visualisation.py");

            // Pass the whole list into ProcessBuilder
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[PY] " + line);
                }
            }

            int exitCode = process.waitFor();
            System.out.println("Exited with code: " + exitCode);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
    }

}
