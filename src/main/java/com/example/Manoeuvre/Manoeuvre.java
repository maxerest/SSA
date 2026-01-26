package com.example.Manoeuvre;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.jetbrains.annotations.Contract;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.attitudes.FrameAlignedProvider;
import org.orekit.forces.maneuvers.propulsion.BasicConstantThrustPropulsionModel;
import org.orekit.forces.maneuvers.propulsion.PropulsionModel;
import org.orekit.forces.maneuvers.trigger.DateBasedManeuverTriggers;
import org.orekit.forces.maneuvers.trigger.ManeuverTriggers;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

import org.orekit.forces.maneuvers.Maneuver;



import com.example.Parametres;
import com.example.Orbiting_object.*;

public class Manoeuvre {
    private  ManeuverTriggers triggers;
    private double thrust;
    private double isp;
    @Contract(value = " -> new", mutates = "this")
    private double  []  motor_1() {
        this.thrust = 1000;
        this.isp    =2000;
        return new double[] {this.thrust,this.isp};
    }
    private double[]  motor_2() {
        this.thrust = 0.2;
        this.isp    = 3180;
        return new double[] {this.thrust,this.isp};
    }
    public ManeuverTriggers getTriggers() {
        return triggers;
    }
    public Manoeuvre(Builder builder) { 
        this.triggers=new DateBasedManeuverTriggers(builder.firingDate,builder.duration);
    }
    /**
     * Launch the manoeuvre within the propagator
     * @param p : Parametres of the satellite
     * @param propagator : NumericalPropagator where the manoeuvre is added
     */
    public void lancement_manoeuvre(Satellite p, NumericalPropagator propagator){
            double[] params_motor;
            if (p.getType_moteur()==1){
                params_motor = motor_1();
            }
            else if (p.getType_moteur()==2){
                params_motor = motor_2();
            }else {
                throw new IllegalArgumentException("Unknown moteur: " + p.getType_moteur());
            }
            this.thrust = params_motor[0];
            this.isp    = params_motor[1];
            //Change those values to set the direction of the thrust
            final Vector3D direction = new Vector3D(FastMath.toRadians(0),
                                                    FastMath.toRadians(0));
            final AttitudeProvider attitudeOverride =
                            new FrameAlignedProvider(new Rotation(direction, Vector3D.PLUS_I), Parametres.frame);

            final PropulsionModel propulsionModel =
                            new BasicConstantThrustPropulsionModel(thrust, isp,
                                                                   Vector3D.PLUS_I,
                                                                   "apogee-engine");
            // build maneuver and add it to the propagator as a new force model
            propagator.addForceModel(new Maneuver(attitudeOverride, triggers, propulsionModel));
        
    }
    
    // Builder class
    public static class Builder {
        AbsoluteDate firingDate;
        private double duration;
        public Builder() {}
        public Builder firingDate(AbsoluteDate firingDate) {this.firingDate = firingDate; return this; }
        public Builder duration(double duration) {this.duration = duration; return this; }
        public Manoeuvre build() { return new Manoeuvre(this); }
    }
    
    
}
