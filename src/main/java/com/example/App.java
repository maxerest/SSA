package com.example;

import com.example.Analytics_Propagator.Type1.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.orekit.data.DataProvider;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.nonstiff.AdaptiveStepsizeIntegrator;
import org.hipparchus.ode.nonstiff.DormandPrince853Integrator;
import org.hipparchus.util.FastMath;
import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;
import org.orekit.forces.ForceModel;
import org.orekit.forces.gravity.HolmesFeatherstoneAttractionModel;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.gravity.potential.NormalizedSphericalHarmonicsProvider;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.analytical.KeplerianPropagator;
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
import org.orekit.utils.IERSConventions;

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
        // définition de l'orbite
        // exemple d'orbite keplerienne
        double semi_axis = 36000e3;
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
        double maxStep = 1000; // seconds
        double initStep = 60.0; // seconds
        // Get the tolerances for the selected orbit type
        final double[][] tolerance = ToleranceProvider.getDefaultToleranceProvider(dP).getTolerances(orbit_kepl, OrbitType.KEPLERIAN);
        AdaptiveStepsizeIntegrator integrator = new DormandPrince853Integrator(minStep, maxStep, tolerance[0], tolerance[1]);
        integrator.setInitialStepSize(initStep);
        NumericalPropagator propagator = new NumericalPropagator(integrator);
        propagator.setOrbitType(OrbitType.KEPLERIAN);
        NormalizedSphericalHarmonicsProvider provider =
        GravityFieldFactory.getNormalizedProvider(10, 10);
        ForceModel holmesFeatherstone =new HolmesFeatherstoneAttractionModel(FramesFactory.getITRF(IERSConventions.IERS_2010,true),provider);
        propagator.addForceModel(holmesFeatherstone);
        propagator.setInitialState(initialState);
        


        
        //SpacecraftState etat_fin_propagation= propagator.propagate(date_fin_propagation);
        export_csv(propagator, date_orekit);
        SpacecraftState finalState = propagator.propagate(new AbsoluteDate(date_orekit, duration));
        RunPythonScript();
        
    }

    private static void export_csv(NumericalPropagator propagator, AbsoluteDate StartDate) {
        // TODO Auto-generated method stub
        File csvFile = new File("orbit.csv");
        if (csvFile.exists()) {
            csvFile.delete();
        }
        
        try (PrintWriter writer = new PrintWriter(csvFile)) {
            writer.println("x,y,z");
            propagator.getMultiplexer().add(60, new Propagation_step(){
                @Override
                public void handleStep(SpacecraftState currentState,PrintWriter writer) {
                    Vector3D pos = currentState.getPVCoordinates().getPosition();
                    writer.printf(Locale.US, "%f,%f,%f%n", pos.getX(), pos.getY(), pos.getZ());
                }
            });
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
            KeplerianOrbit o = (KeplerianOrbit) OrbitType.KEPLERIAN.convertType(currentState.getOrbit());
            Vector3D pos = currentState.getPVCoordinates().getPosition();
            File csvFile = new File("orbit.csv");
            try (FileWriter fw = new FileWriter(csvFile, true);
             PrintWriter writer = new PrintWriter(fw)) {
                writer.printf(Locale.US, "%f,%f,%f%n", pos.getX(), pos.getY(), pos.getZ());
            } catch (IOException e) {
                e.printStackTrace();
            }         
        }
        public void handleStep(SpacecraftState currentState,PrintWriter writer) {
            
            KeplerianOrbit o = (KeplerianOrbit) OrbitType.KEPLERIAN.convertType(currentState.getOrbit());
            System.out.format(Locale.US, "%s %12.3f %10.8f %10.6f %10.6f %10.6f %10.6f%n",
                              currentState.getDate(),
                              o.getA(), o.getE(),
                              FastMath.toDegrees(o.getI()),
                              FastMath.toDegrees(o.getPerigeeArgument()),
                              FastMath.toDegrees(o.getRightAscensionOfAscendingNode()),
                              FastMath.toDegrees(o.getTrueAnomaly()));          
        }
    }
}


