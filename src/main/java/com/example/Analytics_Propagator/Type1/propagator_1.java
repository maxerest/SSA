package com.example.Analytics_Propagator.Type1;
import com.example.*;
import com.example.View.Visulations;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Locale;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.nonstiff.AdaptiveStepsizeIntegrator;
import org.hipparchus.ode.nonstiff.DormandPrince853Integrator;
import org.orekit.attitudes.LofOffset;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.utils.Constants;
import org.orekit.forces.ForceModel;
import org.orekit.forces.drag.DragForce;
import org.orekit.forces.drag.IsotropicDrag;
import org.orekit.forces.gravity.HolmesFeatherstoneAttractionModel;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.gravity.potential.NormalizedSphericalHarmonicsProvider;
import org.orekit.forces.radiation.IsotropicRadiationSingleCoefficient;
import org.orekit.forces.radiation.RadiationSensitive;
import org.orekit.forces.radiation.SolarRadiationPressure;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.LOFType;
import org.orekit.models.earth.atmosphere.Atmosphere;
import org.orekit.models.earth.atmosphere.HarrisPriester;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.OrbitType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.ToleranceProvider;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.propagation.sampling.OrekitFixedStepHandler;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.ExtendedPositionProvider;
import org.orekit.propagation.events.EventDetector;
import org.orekit.propagation.events.handlers.EventHandler;

import java.io.File;
public class Propagator_1
{


        public AdaptiveStepsizeIntegrator integrator(Parametres p) {
        // Define the numerical integrator
        double dP = 0.001;
        double minStep = 0.1;   // seconds
        double maxStep = 100; // seconds
        double initStep = 10.0; // seconds
        Orbit o=p.get_Orbit();
        final double[][] tolerance = ToleranceProvider.getDefaultToleranceProvider(dP).getTolerances(o, OrbitType.KEPLERIAN);
        AdaptiveStepsizeIntegrator integrator = new DormandPrince853Integrator(minStep, maxStep, tolerance[0], tolerance[1]);
        integrator.setInitialStepSize(initStep);
        
        return integrator;
    }


    public static NumericalPropagator add_force_propagator(NumericalPropagator propagator, double area, double cd,double srpCrossSection, double srpCoeff) {
        NormalizedSphericalHarmonicsProvider provider =GravityFieldFactory.getNormalizedProvider(10, 10);
        ForceModel holmesFeatherstone =new HolmesFeatherstoneAttractionModel(FramesFactory.getITRF(IERSConventions.IERS_2010,true),provider);

        propagator.addForceModel(holmesFeatherstone);
        OneAxisEllipsoid earth = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                                       Constants.WGS84_EARTH_FLATTENING,
                                       FramesFactory.getITRF(IERSConventions.IERS_2010, true));
        
        Atmosphere atmosphere = new HarrisPriester(CelestialBodyFactory.getSun(), earth);    
        
        DragForce drag = new DragForce(atmosphere, new IsotropicDrag(area, cd));
        propagator.addForceModel(drag);   

        RadiationSensitive srpSurface = new IsotropicRadiationSingleCoefficient(srpCrossSection, srpCoeff);

        ExtendedPositionProvider sun = CelestialBodyFactory.getSun();
        SolarRadiationPressure srp = new SolarRadiationPressure(sun,earth,srpSurface);
        propagator.addForceModel(srp);
        propagator.setAttitudeProvider(new LofOffset(Parametres.frame, LOFType.VNC));
        return propagator;
    }

    public static class Propagation_step implements OrekitFixedStepHandler {
        private final String sat;
        private final Parametres p;
        public Propagation_step(String sat,Parametres p) {
            this.sat = sat;
            this.p = p;
        }

    
        public void handleStep(SpacecraftState currentState) {
            boolean triger = p.manoeuvre.getTriggers().isFiring(currentState.getDate(), null);
            File csvFile = new File("C:\\Users\\maxen\\Desktop\\Java\\ssa\\temp\\SSA\\src\\main\\java\\com\\example\\View\\"+sat+".csv");
            Vector3D pos = currentState.getPVCoordinates().getPosition();
            try (FileWriter fw = new FileWriter(csvFile, true);
             PrintWriter writer = new PrintWriter(fw)) {
                writer.printf(Locale.US, "%f,%f,%f,%f,%d%n", pos.getX(), pos.getY(), pos.getZ(),currentState.getDate().durationFrom(new AbsoluteDate(new Date(), TimeScalesFactory.getUTC()).shiftedBy(2*3600)), triger ? 1 : 0);
            } catch (IOException e) {
                e.printStackTrace();
            }         
        }
       
    }
    
    public static class Altitude_limit implements EventHandler{
            private double Detectionaltitude;
            private Parametres p;
            private NumericalPropagator propagator;
    
            public Altitude_limit(Parametres p, NumericalPropagator propagator) {
                this.Detectionaltitude = p.Detectionaltitude;
                this.p = p;
                this.propagator = propagator;
            }
        
            public org.hipparchus.ode.events.Action eventOccurred(final SpacecraftState s, final EventDetector detector, final boolean increasingdouble) {
                
                System.out.println("Altitude reached "+(Detectionaltitude-Constants.WGS84_EARTH_EQUATORIAL_RADIUS)+"km at "+String.format("%.2f",s.getDate().durationFrom(p.get_Date())/3600)+"h after the start, stopping propagation.");
                Visulations.export_csv(propagator, p);
                return org.hipparchus.ode.events.Action.STOP;
            }
        }
    

    
        
}
