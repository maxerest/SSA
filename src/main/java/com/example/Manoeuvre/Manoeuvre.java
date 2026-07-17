package com.example.Manoeuvre;
import com.example.Analytics_Propagator.Type1.Handlers;
import com.example.Orbiting_object.Satellite_sub_systems.Motors;
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
import org.orekit.propagation.events.*;
import org.orekit.propagation.events.handlers.StopOnIncreasing;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

import org.orekit.forces.maneuvers.Maneuver;



import com.example.Parametres;
import com.example.Orbiting_object.*;

import java.util.LinkedList;
import java.util.List;

public class Manoeuvre {

    public Manoeuvre(AbsoluteDate start_date,double duration,Satellite satellite){

    }

    /**
     * Launch the manoeuvre within the propagator
     * @param propagator : NumericalPropagator where the manoeuvre is added
     */


    // From motor, direction , and the event detector, it creates the maneuvre and returns it for the propagator
    public void new_manoeuvre_creation(Satellite s, Vector3D direction, NumericalPropagator propagator, double duration) {

        final EventDetector apsideDetector =
                new ApsideDetector(s.get_keplerian_Orbit())
                        .withHandler(new Handlers.maneuver_handler(s, propagator, true, direction, duration));

        propagator.addEventDetector(apsideDetector);
    }

}
