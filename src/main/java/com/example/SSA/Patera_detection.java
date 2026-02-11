package com.example.SSA;

import com.example.Orbiting_object.Satellite;
import com.example.Parametres;
import org.hipparchus.geometry.Space;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.linear.MatrixUtils;
import org.hipparchus.linear.RealMatrix;
import org.hipparchus.util.FastMath;
import org.orekit.propagation.SpacecraftState;
import org.orekit.ssa.collision.shorttermencounter.probability.twod.Patera2005;
import org.orekit.ssa.metrics.ProbabilityOfCollision;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Patera_detection extends Patera2005 {

    public Patera_detection(){
    }
    public static boolean check_collision(SpacecraftState s1, SpacecraftState s2){
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
        double collisionRadius = 15000 + 15000; //TODO check the radius


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

            if (pCollision.getValue()>0.2){
                System.out.println("\nProbability of Collision: " + pCollision.getValue()+" at time "+s1.getDate()+", "+(s1.getDate().durationFrom(Parametres.date_orekit)) +" seconds after the start ");
                return true;
            }
            return false; // change with thresholdcheck
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
    public static boolean verification_post_propagation(List<Satellite> list_sat){
        boolean colision;
        for (int i = 0; i < list_sat.size(); i++) {
            for (int j = i + 1; j < list_sat.size(); j++) {
                Satellite sat1 = list_sat.get(i);
                Satellite sat2 = list_sat.get(j);
                for(int k=0;k<sat1.get_liste_state_propa().size();k++){
                    for(int l=0;l<sat2.get_liste_state_propa().size();l++){
                        if(check_collision(sat1.get_liste_state_propa().get(k),sat2.get_liste_state_propa().get(l))){
                            return true;
                        };
                    }
                }
            }
        }
    return false;
    }
}

