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
    private AbsoluteDate start_date;
    private double duration;
    private Satellite satellite;
    public Manoeuvre(AbsoluteDate start_date,double duration,Satellite satellite){
        this.start_date=start_date;
        this.duration=duration;
        this.triggers=new DateBasedManeuverTriggers(satellite.getPropagation_date().shiftedBy(start_date),duration);
        this.satellite=satellite;
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

}
