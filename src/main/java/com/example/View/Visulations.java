package com.example.View;
import com.example.Parametres;
import com.example.Analytics_Propagator.Type1.Propagator_1;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Locale;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.propagation.sampling.OrekitFixedStepHandler;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;

public class Visulations {
     public static void export_csv(NumericalPropagator propagator, AbsoluteDate StartDate) {
        // TODO Auto-generated method stub
        File csvFile = new File("orbit.csv");
        if (csvFile.exists()) {
            csvFile.delete();
        }
        try (PrintWriter writer = new PrintWriter(csvFile)) {
            writer.println("x,y,z,t");
            propagator.getMultiplexer().add(60,new Propagator_1.Propagation_step());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    

    public static void RunPythonScript() {
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "src/main/java/com/example/View/Visualisation.py");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            int exitCode = process.waitFor();
            System.out.println("Exited with code: " + exitCode);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    
}
