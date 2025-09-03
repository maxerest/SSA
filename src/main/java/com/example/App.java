package com.example;

import com.example.Analytics_Propagator.Type1.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.orekit.data.DataProvider;
import org.hipparchus.geometry.Space;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.nonstiff.AdaptiveStepsizeIntegrator;
import org.hipparchus.ode.nonstiff.DormandPrince853Integrator;
import org.hipparchus.util.FastMath;
import org.orekit.bodies.BodyShape;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.propagation.events.handlers.EventHandler.Action;
import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;
import org.orekit.forces.ForceModel;
import org.orekit.forces.gravity.HolmesFeatherstoneAttractionModel;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.gravity.potential.NormalizedSphericalHarmonicsProvider;
import org.orekit.forces.radiation.IsotropicRadiationSingleCoefficient;
import org.orekit.forces.radiation.RadiationSensitive;
import org.orekit.forces.radiation.SolarRadiationPressure;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.models.earth.atmosphere.Atmosphere;
import org.orekit.models.earth.atmosphere.HarrisPriester;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.analytical.KeplerianPropagator;
import org.orekit.propagation.events.AltitudeDetector;
import org.orekit.propagation.events.EventDetector;
import org.orekit.propagation.events.handlers.EventHandler;
import org.orekit.propagation.events.handlers.*;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.propagation.sampling.OrekitFixedStepHandler;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.OrbitType;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.propagation.Propagator;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.ToleranceProvider;
import org.orekit.utils.Constants;
import org.orekit.utils.ExtendedPositionProvider;
import org.orekit.utils.IERSConventions;
import org.orekit.forces.drag.DragForce;
import org.orekit.forces.drag.IsotropicDrag;


import java.util.Date;
import java.util.Locale;
/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        final File orekitData = new File("C:\\Users\\maxen\\Desktop\\Java\\ssa\\temp\\SSA");
        final DataProvider dirCrawler = new DirectoryCrawler(orekitData);
        DataContext.getDefault().getDataProvidersManager().addProvider(dirCrawler);

        //définition des parametres initiaux
        Frame frame = FramesFactory.getEME2000();
        AbsoluteDate date_orekit = new AbsoluteDate(new Date(), TimeScalesFactory.getUTC()).shiftedBy(2*3600);
        double mu = Constants.EIGEN5C_EARTH_MU;
        BodyShape earth = new OneAxisEllipsoid(
        Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
        Constants.WGS84_EARTH_FLATTENING,
        FramesFactory.getITRF(IERSConventions.IERS_2010, true)
);
        // définition de l'orbite
        // exemple d'orbite keplerienne
        double semi_axis = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 700e3;;
        double eccentricity = 0.001;
        double inclinaison = Math.toRadians(45);
        double long_noeud_ascendant = Math.toRadians(30);
        double arg_periastre = Math.toRadians(45);
        double anomalie = Math.toRadians(60);
        PositionAngleType type_anomalie = PositionAngleType.MEAN;
        double duration = Constants.JULIAN_DAY;
        // création de l'orbite
        Orbit orbit_kepl = new KeplerianOrbit(semi_axis, eccentricity, inclinaison,  long_noeud_ascendant,  arg_periastre, anomalie, type_anomalie, frame, date_orekit, mu);
        SpacecraftState initialState = new SpacecraftState(orbit_kepl);
        // Define the numerical integrator
        double dP = 0.001;
        double minStep = 0.1;   // seconds
        double maxStep = 10; // seconds
        double initStep = 60.0; // seconds
        // Get the tolerances for the selected orbit type
        final double[][] tolerance = ToleranceProvider.getDefaultToleranceProvider(dP).getTolerances(orbit_kepl, OrbitType.KEPLERIAN);
        AdaptiveStepsizeIntegrator integrator = new DormandPrince853Integrator(minStep, maxStep, tolerance[0], tolerance[1]);
        integrator.setInitialStepSize(initStep);
        NumericalPropagator propagator = new NumericalPropagator(integrator);
        propagator.setOrbitType(OrbitType.KEPLERIAN);
        add_force_propagator(propagator);
        propagator.setInitialState(initialState);
        Double Detectionaltitude =699000.0;
        AltitudeDetector altitudeDetector = new AltitudeDetector(Detectionaltitude,earth).withHandler(new EventHandler(){
        @Override
        public org.hipparchus.ode.events.Action eventOccurred(final SpacecraftState s, final EventDetector detector, final boolean increasing) {
        System.out.println("Altitude reached "+Detectionaltitude+"km at "+String.format("%.2f",s.getDate().durationFrom(date_orekit)/3600)+"h after the start, stopping propagation.");
            return org.hipparchus.ode.events.Action.STOP;
        }});
        propagator.addEventDetector(altitudeDetector);
        //SpacecraftState etat_fin_propagation= propagator.propagate(date_fin_propagation);
        export_csv(propagator, date_orekit);
        SpacecraftState finalState = propagator.propagate(new AbsoluteDate(date_orekit, duration));
        RunPythonScript();
        System.err.println(finalState.getPosition(frame).getNorm());
        System.err.println(finalState.getDate().durationFrom(date_orekit)/(3600*24));
    }


    private static NumericalPropagator add_force_propagator(NumericalPropagator propagator ) {
        NormalizedSphericalHarmonicsProvider provider =GravityFieldFactory.getNormalizedProvider(10, 10);
        ForceModel holmesFeatherstone =new HolmesFeatherstoneAttractionModel(FramesFactory.getITRF(IERSConventions.IERS_2010,true),provider);
        propagator.addForceModel(holmesFeatherstone);
        OneAxisEllipsoid earth = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                                       Constants.WGS84_EARTH_FLATTENING,
                                       FramesFactory.getITRF(IERSConventions.IERS_2010, true));
        
        Atmosphere atmosphere = new HarrisPriester(CelestialBodyFactory.getSun(), earth);    
        double area = 1.0;    // m^2
        double cd   = 2.2;
        DragForce drag = new DragForce(atmosphere, new IsotropicDrag(area, cd));
        propagator.addForceModel(drag);   
        
        double srpCrossSection = 2;   // m²
        double srpCoeff        = 1.30;
        RadiationSensitive srpSurface = new IsotropicRadiationSingleCoefficient(srpCrossSection, srpCoeff);

        ExtendedPositionProvider sun = CelestialBodyFactory.getSun();
        SolarRadiationPressure srp = new SolarRadiationPressure(sun,earth,srpSurface);
        propagator.addForceModel(srp);
        return propagator;
    }


    private static void export_csv(NumericalPropagator propagator, AbsoluteDate StartDate) {
        // TODO Auto-generated method stub
        File csvFile = new File("orbit.csv");
        if (csvFile.exists()) {
            csvFile.delete();
        }
        try (PrintWriter writer = new PrintWriter(csvFile)) {
            writer.println("x,y,z,t");
            propagator.getMultiplexer().add(60, new Propagation_step());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    

    private static void RunPythonScript() {
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "src/main/java/com/example/View/Visualisation.py");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            int exitCode = process.waitFor();
            System.out.println("Exited with code: " + exitCode);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
    private static class Propagation_step implements OrekitFixedStepHandler {
 
        public void handleStep(SpacecraftState currentState) {

            File csvFile = new File("orbit.csv");
            Vector3D pos = currentState.getPVCoordinates().getPosition();
            try (FileWriter fw = new FileWriter(csvFile, true);
             PrintWriter writer = new PrintWriter(fw)) {
                writer.printf(Locale.US, "%f,%f,%f,%f%n", pos.getX(), pos.getY(), pos.getZ(),currentState.getDate().durationFrom(new AbsoluteDate(new Date(), TimeScalesFactory.getUTC()).shiftedBy(2*3600)));
            } catch (IOException e) {
                e.printStackTrace();
            }         
        }
    }
}


