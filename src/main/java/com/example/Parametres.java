package com.example;

import java.util.Date;

import org.hipparchus.util.FastMath;
import org.orekit.bodies.BodyShape;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;


public class Parametres
{
    //Gestion temps, both values are imported from the congif bridge
    public static AbsoluteDate date_orekit;
    public static double duration;

    // définition de la Terre
    public static Frame frame = FramesFactory.getEME2000();
    public static double mu = Constants.EIGEN5C_EARTH_MU;
    public static BodyShape earth = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,Constants.WGS84_EARTH_FLATTENING,FramesFactory.getITRF(IERSConventions.IERS_2010, true));
    
    // Ground Station elevation limit
    public static double elevation =  FastMath.toRadians(10.0);

    
}
