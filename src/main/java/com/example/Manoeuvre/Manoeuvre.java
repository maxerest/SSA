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
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;

import org.orekit.forces.maneuvers.Maneuver;



import com.example.Parametres;

public class Manoeuvre {

    public static NumericalPropagator Moteur_1(Parametres p, NumericalPropagator propagator ){
            final Vector3D direction = new Vector3D(FastMath.toRadians(-7.4978),
                                                    FastMath.toRadians(351));
            final AttitudeProvider attitudeOverride =
                            new FrameAlignedProvider(new Rotation(direction, Vector3D.PLUS_I), Parametres.frame);

            // maneuver will start at a known date and stop after a known duration
            final AbsoluteDate firingDate = new AbsoluteDate(Parametres.date_orekit.shiftedBy(300));
            final double duration = 360;
            final ManeuverTriggers triggers = new DateBasedManeuverTriggers(firingDate, duration);

            // maneuver has constant thrust
            final double thrust = 800;
            final double isp    = 318;
            final PropulsionModel propulsionModel =
                            new BasicConstantThrustPropulsionModel(thrust, isp,
                                                                   Vector3D.MINUS_I,
                                                                   "apogee-engine");

            // build maneuver and add it to the propagator as a new force model
            propagator.addForceModel(new Maneuver(null, triggers, propulsionModel));

        return propagator;
    }
}
