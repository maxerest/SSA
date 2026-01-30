package com.example.Manoeuvre;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.jetbrains.annotations.Contract;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.attitudes.FrameAlignedProvider;
import org.orekit.attitudes.LofOffset;
import org.orekit.forces.maneuvers.ImpulseManeuver;
import org.orekit.forces.maneuvers.propulsion.BasicConstantThrustPropulsionModel;
import org.orekit.forces.maneuvers.propulsion.PropulsionModel;
import org.orekit.forces.maneuvers.trigger.DateBasedManeuverTriggers;
import org.orekit.forces.maneuvers.trigger.ManeuverTriggers;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.LOFType;
import org.orekit.propagation.events.EnablingPredicate;
import org.orekit.propagation.events.EventDetector;
import org.orekit.propagation.events.EventEnablingPredicateFilter;
import org.orekit.propagation.events.NodeDetector;
import org.orekit.propagation.events.handlers.StopOnIncreasing;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

import org.orekit.forces.maneuvers.Maneuver;



import com.example.Parametres;
import com.example.Orbiting_object.*;

import java.util.LinkedList;
import java.util.List;

public class Manoeuvre {
    private  ManeuverTriggers triggers;
    private static List<Motor> liste_motor=new LinkedList<>();
    private double start_date;
    private double duration;

    public Manoeuvre(double start_date,double duration){
        this.start_date=start_date;
        this.duration=duration;
        this.triggers=new DateBasedManeuverTriggers(Parametres.date_orekit.shiftedBy(start_date),duration);
    }

    public ManeuverTriggers getTriggers() {
        return triggers;
    }
    /**
     * Launch the manoeuvre within the propagator
     * @param propagator : NumericalPropagator where the manoeuvre is added
     */
    public void launch_manoeuvre(double ISP,double thrust, NumericalPropagator propagator){
        //Change those values to set the direction of the thrust
        final AttitudeProvider attitudeProvider = new LofOffset( Parametres.frame, LOFType.LVLH);
        final Vector3D direction = new Vector3D(FastMath.toRadians(0),
                FastMath.toRadians(0));

        final AttitudeProvider attitudeOverride =
                new FrameAlignedProvider(new Rotation(direction, Vector3D.PLUS_J), Parametres.frame);
        final PropulsionModel propulsionModel =
                new BasicConstantThrustPropulsionModel(thrust, ISP,
                        Vector3D.PLUS_I,
                        "test manoeuvre");
        // build maneuver and add it to the propagator as a new force model
        propagator.addForceModel(new Maneuver(attitudeOverride, triggers, propulsionModel));

        
    }
    // Builder class

    public static class Motor{
        private String name;
        private double ISP;
        private double thrust;

        public enum type_motor {
            motor_1("Moteur_1", 240, 425),
            motor_2("Moteur_2", 1000, 0.2),
            motor_3("Moteur_3", 3000, 0.1);
            private final String name;
            private final double ISP;
            private final double thrust;

            type_motor(String name, double ISP, double thrust) {
                this.name = name;
                this.ISP = ISP;
                this.thrust = thrust;
            }

            public String getName() {
                return name;
            }

            public double getISP() {
                return ISP;
            }

            public double getThrust() {
                return thrust;
            }

        }
        Motor(String name,double ISP,double thrust){
            this.name=name;
            this.ISP=ISP;
            this.thrust=thrust;
        }

        public static void initialize_list(){
            for (type_motor t_motor : type_motor.values()){
                liste_motor.add(new Motor(t_motor.getName(),t_motor.getISP(),t_motor.getThrust()));
            }
        }
        public static void add_motor(String name, double ISP, double thrust){
            liste_motor.add(new Motor(name,ISP,thrust));
        }

        public static double getthrust(String name){
            for (Motor motor : liste_motor) {
                if (motor.name.equals(name)) {
                    return motor.thrust;
                }
            }
            return 0.0;
        }

        public static double getISP(String name){
            for (Motor motor : liste_motor) {
                if (motor.name.equals(name)) {
                    return motor.ISP;
                }
            }
            return 0;
        }

    }

}
