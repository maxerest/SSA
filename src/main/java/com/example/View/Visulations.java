package com.example.View;
import com.example.Analytics_Propagator.Type1.Propagator_1;
import com.example.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

import java.util.ArrayList;
import java.util.List;

public class Visulations {
     public static void export_csv(NumericalPropagator propagator, Parametres p) {
        // TODO Auto-generated method stub
        String sat= p.get_Name();
        File csvFile = new File("src/main/java/com/example/View/"+sat+".csv");
        if (csvFile.exists()) {
            csvFile.delete();
        }
        try (PrintWriter writer = new PrintWriter(csvFile)) {
            writer.println("x,y,z,t,firing");
            propagator.getMultiplexer().add(60,new Propagator_1.Propagation_step(sat,p));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    

    public static void RunPythonScript(List<Parametres> sats) {
        try {
            // Build up the full command
            List<String> cmd = new ArrayList<>();
            cmd.add("python");
            cmd.add("src/main/java/com/example/View/Visualisation.py");
    
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
