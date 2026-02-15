package com.example.SSA;

import com.example.Orbiting_object.Satellite;
import com.example.Parametres;
import com.example.View.Visulations;
import org.hipparchus.geometry.Space;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.linear.MatrixUtils;
import org.hipparchus.linear.RealMatrix;
import org.hipparchus.util.FastMath;
import org.orekit.propagation.SpacecraftState;
import org.orekit.ssa.collision.shorttermencounter.probability.twod.Patera2005;
import org.orekit.ssa.metrics.ProbabilityOfCollision;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class Patera_detection extends Patera2005 {
    public Patera_detection(){
    }
    public double check_collision(SpacecraftState s1, SpacecraftState s2){
        Vector3D sat_1_pos=s1.getPosition();
        Vector3D sat_1_vel=s1.getVelocity();

        Vector3D satellite_2_pos =s2.getPosition();

        Vector3D sat_2_vel=s2.getVelocity();
        Vector3D relativePos = satellite_2_pos.subtract(sat_1_pos);
        Vector3D relativeVel = sat_2_vel.subtract(sat_1_vel);

        // Position uncertainty (1-sigma) in meters
        double posUncertainty1 = 0.1;  // TODO change this to be dynamic
        double posUncertainty2 = 0.1;  // TODO change this to be dynamic

        // Combined position covariance (diagonal matrix for simplicity)
        double[][] covarianceArray = {
                {posUncertainty1 * posUncertainty1 + posUncertainty2 * posUncertainty2, 0, 0},
                {0, posUncertainty1 * posUncertainty1 + posUncertainty2 * posUncertainty2, 0},
                {0, 0, posUncertainty1 * posUncertainty1 + posUncertainty2 * posUncertainty2}
        };
        RealMatrix covariance = MatrixUtils.createRealMatrix(covarianceArray);
        double xm = relativePos.getX();  // relative position in x
        double ym = relativePos.getY();
        double sigmaX = FastMath.sqrt(covariance.getEntry(0, 0));
        double sigmaY = FastMath.sqrt(covariance.getEntry(1, 1));
        double collisionRadius = 100000; //TODO check the radius


        try {

            // Create Patera2005 calculator
            Patera2005 patera2005 = new Patera2005();

            // Calculate probability of collision
            ProbabilityOfCollision pCollision = patera2005.compute(
                    xm,              // relative x position
                    ym,              // relative y position
                    sigmaX,          // x position uncertainty
                    sigmaY,          // y position uncertainty
                    collisionRadius  // combined collision radius
            );
            return pCollision.getValue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
    public void verification_post_propagation(Satellite s, List<Satellite> list_sat) {
        double percentage_collision;
        for (Satellite sat2 : list_sat) {
            if (!sat2.equals(s)) {
                for (int k = 0; k < s.get_liste_state_propa().size(); k++) {
                    // Only compare with the state at the same index
                    percentage_collision=check_collision(s.get_liste_state_propa().get(k), sat2.get_liste_state_propa().get(k));
                    s.add_map_percentage_collison(sat2.get_Name(),s.get_liste_state_propa().get(k).getDate(),percentage_collision);

                }
            }
        }
    }
    public void print_csv_detection(Satellite s){
        File csvFile = new File("src/main/java/com/example/SSA/CSV_per_sat/" + s.get_Name() + "_collision.csv");
        try (PrintWriter writer = new PrintWriter(csvFile)) {
            writer.println("t,nom_autre_sat,% collision");
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (FileWriter fw = new FileWriter(csvFile, true);
             PrintWriter writer = new PrintWriter(fw)) {
            s.getMap_pourcentage_collision().forEach((nomSat, mapCollisions) -> {
            mapCollisions.forEach((l, percentage) -> {
                writer.printf(Locale.US, "%.2f,%s,%.2f%n",
                        (double) l.durationFrom(Parametres.date_orekit), nomSat, percentage);
            });
        });
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void check_per_sat_collision (List<Satellite> list_sat){
        Patera_detection pat =new Patera_detection();
        for (Satellite s : list_sat){

            pat.verification_post_propagation(s, list_sat);
            pat.print_csv_detection(s);
        }
        Visulations.Python_graph_collision();
    }

}

