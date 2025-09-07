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


public class Visulations {
     public static void export_csv(NumericalPropagator propagator, Parametres p) {
        // TODO Auto-generated method stub
        String sat= p.get_Name();
        File csvFile = new File("src/main/java/com/example/View/"+sat+".csv");
        if (csvFile.exists()) {
            csvFile.delete();
        }
        try (PrintWriter writer = new PrintWriter(csvFile)) {
            writer.println("x,y,z,t");
            propagator.getMultiplexer().add(60,new Propagator_1.Propagation_step(sat));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    

    public static void RunPythonScript(Parametres p) {
        try {
            String sat= p.get_Name();
            ProcessBuilder pb = new ProcessBuilder("python", "src/main/java/com/example/View/Visualisation.py",sat);
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
