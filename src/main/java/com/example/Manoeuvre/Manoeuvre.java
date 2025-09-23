package com.example.Manoeuvre;

import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.attitudes.FrameAlignedProvider;
import org.orekit.forces.maneuvers.propulsion.BasicConstantThrustPropulsionModel;
import org.orekit.forces.maneuvers.propulsion.PropulsionModel;
import org.orekit.forces.maneuvers.trigger.DateBasedManeuverTriggers;
import org.orekit.forces.maneuvers.trigger.ManeuverTriggers;
import org.orekit.propagation.conversion.NumericalPropagatorBuilder;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

import org.orekit.forces.maneuvers.Maneuver;



import com.example.Parametres;

public class Manoeuvre {
    private  ManeuverTriggers triggers;
    private double thrust = 800;
    private double isp    = 318;
    private AbsoluteDate firingDate;
    private double duration;

    public Manoeuvre(Builder builder) { 
        this.triggers=new DateBasedManeuverTriggers(builder.firingDate,builder.duration);
    }
    public void lancement_manoeuvre(Parametres p, NumericalPropagator propagator){
            double[] params_motor;
            if (p.get_Type_moteur()==1){
                params_motor = motor_1();
            }
            else if (p.get_Type_moteur()==2){
                params_motor = motor_2();
            }else {
                throw new IllegalArgumentException("Unknown moteur: " + p.get_Type_moteur());
            }
            this.thrust = params_motor[0];
            this.isp    = params_motor[1];
            final Vector3D direction = new Vector3D(FastMath.toRadians(0),
                                                    FastMath.toRadians(0));
            final AttitudeProvider attitudeOverride =
                            new FrameAlignedProvider(new Rotation(direction, Vector3D.PLUS_I), Parametres.frame);

            final PropulsionModel propulsionModel =
                            new BasicConstantThrustPropulsionModel(thrust, isp,
                                                                   Vector3D.MINUS_I,
                                                                   "apogee-engine");
            //triggers.isFiring(null, null);
            // build maneuver and add it to the propagator as a new force model
            propagator.addForceModel(new Maneuver(attitudeOverride, triggers, propulsionModel));
        
    }
    public void lancement_manoeuvre(Parametres p, NumericalPropagatorBuilder propagator){
        double[] params_motor;
        if (p.get_Type_moteur()==1){
            params_motor = motor_1();
        }
        else if (p.get_Type_moteur()==2){
            params_motor = motor_2();
        }else {
            throw new IllegalArgumentException("Unknown moteur: " + p.get_Type_moteur());
        }
        this.thrust = params_motor[0];
        this.isp    = params_motor[1];
        final Vector3D direction = new Vector3D(FastMath.toRadians(-7.4978),
                                                FastMath.toRadians(351));
        final AttitudeProvider attitudeOverride =
                        new FrameAlignedProvider(new Rotation(direction, Vector3D.PLUS_I), Parametres.frame);

        final PropulsionModel propulsionModel =
                        new BasicConstantThrustPropulsionModel(thrust, isp,
                                                               Vector3D.MINUS_I,
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
    private double[]  motor_1() {
        this.thrust = 8000;
        this.isp    = 3180;
        return new double[] {this.thrust,this.isp};
    }
    private double[]  motor_2() {
        this.thrust = 3000;
        this.isp    = 3180;
        return new double[] {this.thrust,this.isp};
    }
    public ManeuverTriggers getTriggers() {
        return triggers;
    }
    
}
