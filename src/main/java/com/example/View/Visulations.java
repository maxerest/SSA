package com.example.View;
import com.example.Analytics_Propagator.Type1.Propagator_1;
import com.example.Analytics_Propagator.Least_squares_batch;
import com.example.Parametres;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.linear.RealVector;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import org.orekit.estimation.measurements.EstimatedMeasurementBase;
import org.orekit.estimation.measurements.GroundStation;
import org.orekit.estimation.measurements.ObservedMeasurement;
import org.hipparchus.util.FastMath;
public class Visulations {
     // Method for the real satellite data export to CSV
     public static void export_csv(NumericalPropagator propagator, Parametres p) {
        String sat= p.get_Name();
        File csvFile = new File("src/main/java/com/example/View/"+sat+".csv");
        if (csvFile.exists()) {
            csvFile.delete();
        }
        try (PrintWriter writer = new PrintWriter(csvFile)) {
            writer.println("x,y,z,t,firing,detected_by_GS");
            propagator.getMultiplexer().add(60,new Propagator_1.Propagation_step(sat,p));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void export_LSB_csv(Parametres p,SortedSet<EstimatedMeasurementBase<?>> measurements) {
        String sat= p.get_Name();
        File csvFile = new File("src/main/java/com/example/View/"+sat+"_LSB.csv");
        if (csvFile.exists()) {
            csvFile.delete();
        }
        try (PrintWriter writer = new PrintWriter(csvFile)) {
            writer.println("x,y,z,t,firing,detected_by_GS");
            Double lastRange = null;
            Double lastAzimuth = null;
            Double lastElevation = null;

                // Here we assume that if a measurement exists, it was detected by a GS
                // Compter les mesures par station
                 for (EstimatedMeasurementBase<?> estimatedMeasurement : measurements) {
                ObservedMeasurement<?> measurement = estimatedMeasurement.getObservedMeasurement();
                String type = measurement.getClass().getSimpleName();
                double[] values = measurement.getObservedValue();
                AbsoluteDate lastDate = measurement.getDate();
                
                double[] xyz= new double[3];
                // Identifier la station qui a fait la mesure
                GroundStation station = null;
                if (type.equals("Range")) {
                    org.orekit.estimation.measurements.Range rangeMeas = 
                        (org.orekit.estimation.measurements.Range) measurement;
                    station = rangeMeas.getStation();
                    lastRange = values[0];
                    
                } else if (type.equals("AngularAzEl")) {
                    org.orekit.estimation.measurements.AngularAzEl azElMeas = 
                        (org.orekit.estimation.measurements.AngularAzEl) measurement;
                    station = azElMeas.getStation();
                    lastAzimuth = values[0];
                    lastElevation = values[1];
                }
                
                
                
                if (type.equals("Range")) {
                    
                } else if (type.equals("AngularAzEl")) {
                    
                    if (lastRange != null ) {
                        
                        xyz = Least_squares_batch.azElRangeToECEF(lastAzimuth, lastElevation, 
                                                       lastRange, station, lastDate);
                                writer.printf(Locale.US, "%.6f,%.6f,%.6f,%f,%d,%d%n",
                                xyz[0],xyz[1],xyz[2], lastDate.durationFrom(Parametres.date_orekit), 0, 1);
                    }
                
            }
            

            };
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // Initialize CSV for estimated satellite data
    public static void export_csv_kalman_init(Parametres p) {
        String sat= p.get_Name();
        File csvFile = new File("src/main/java/com/example/View/"+sat+".csv");
        if (csvFile.exists()) {
            csvFile.delete();
        }
        try (PrintWriter writer = new PrintWriter(csvFile)) {
            writer.println("x,y,z,t,firing,detected_by_GS");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    // Files the CSV with zeros data before detection from GS for estimated satellite
    public static void write_csv_before_detection(Parametres p){
        String sat= p.get_Name();
        File csvFile = new File("src/main/java/com/example/View/"+sat+".csv");
        try (FileWriter fw = new FileWriter(csvFile, true);
            PrintWriter writer = new PrintWriter(fw)) {
            writer.printf(Locale.US, "0,0,0,0,0,0%n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void export_csv_kalman_add_step(Parametres p,double t, RealVector temp_s,boolean detected_by_GS) {
                // Position
                double x = temp_s.getEntry(0);
                double y = temp_s.getEntry(1);
                double z = temp_s.getEntry(2);
                // Velocity
                Vector3D pos = new Vector3D(x, y, z);
                boolean triger = p.manoeuvre.getTriggers().isFiring(Parametres.date_orekit.shiftedBy(t), null);
                File csvFile = new File("C:\\Users\\maxen\\Desktop\\Java\\ssa\\temp\\SSA\\src\\main\\java\\com\\example\\View\\"+p.get_Name()+".csv");
                try (FileWriter fw = new FileWriter(csvFile, true);
                        PrintWriter writer = new PrintWriter(fw)) {
                        writer.printf(Locale.US, "%f,%f,%f,%f,%d,%d%n", pos.getX(), pos.getY(), pos.getZ(),t, triger ? 1 : 0,detected_by_GS? 1:0);
                    } catch (IOException e) {
                    e.printStackTrace();
                    }
                
            }
    

    

    public static void RunPythonScript(List<Parametres> sats,List<Parametres> sats2) {
        try {
            // Build up the full command
            List<String> cmd = new ArrayList<>();
            cmd.add("python");
            cmd.add("src/main/java/com/example/View/Visualisation.py");
            sats.addAll(sats2);
            // Add one argument per satellite (e.g., its name or CSV path)
            for (Parametres p : sats) {
                cmd.add(p.get_Name()); // or build path: "src/main/java/com/example/View/" + p.get_Name() + ".csv"
            }
    
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
