package com.example;

import java.util.Date;

import org.hipparchus.ode.nonstiff.AdaptiveStepsizeIntegrator;
import org.hipparchus.ode.nonstiff.DormandPrince853Integrator;
import org.orekit.bodies.BodyShape;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.OrbitType;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.ToleranceProvider;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;

public class Parametres
{
    //Gestion temps
    public AbsoluteDate date_orekit = new AbsoluteDate(new Date(), TimeScalesFactory.getUTC()).shiftedBy(2*3600);
    public double duration = Constants.JULIAN_DAY;
    
    // définition de la Terre
    public Frame frame = FramesFactory.getEME2000();
    public double mu = Constants.EIGEN5C_EARTH_MU;
    public BodyShape earth = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,Constants.WGS84_EARTH_FLATTENING,FramesFactory.getITRF(IERSConventions.IERS_2010, true));

    // Definiton parametres orbitaux
    public double semi_axis = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 700e3;;
    public double eccentricity = 0.001;
    public double inclinaison = Math.toRadians(45);
    public double long_noeud_ascendant = Math.toRadians(30);
    public double arg_periastre = Math.toRadians(45);
    public double anomalie = Math.toRadians(60);
    public PositionAngleType type_anomalie = PositionAngleType.MEAN;
    Orbit orbit_kepl = new KeplerianOrbit(semi_axis, eccentricity, inclinaison,  long_noeud_ascendant,  arg_periastre, anomalie, type_anomalie, frame, date_orekit, mu);
        

    // Parametres satellite
    public double area = 1.0;    // m^2
    public double cd   = 2.2;
    public double srpCrossSection = 2;   // m²
    public double srpCoeff        = 1.30;
        
    //Seul de detection altitude
    public Double Detectionaltitude =Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 699e3;
       


    //Inital state of the satellite
    public SpacecraftState s_initialState = new SpacecraftState(orbit_kepl);
   


    public AdaptiveStepsizeIntegrator integrator() {
        // Define the numerical integrator
        double dP = 0.001;
        double minStep = 0.1;   // seconds
        double maxStep = 100; // seconds
        double initStep = 10.0; // seconds
        final double[][] tolerance = ToleranceProvider.getDefaultToleranceProvider(dP).getTolerances(orbit_kepl, OrbitType.KEPLERIAN);
        AdaptiveStepsizeIntegrator integrator = new DormandPrince853Integrator(minStep, maxStep, tolerance[0], tolerance[1]);
        integrator.setInitialStepSize(initStep);
        return integrator;
    }

    
}
