package com.example.Analytics_Propagator.Type1;
import com.example.Ground_stations.*;
import com.example.Orbiting_object.*;

import java.util.*;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.linear.MatrixUtils;
import org.hipparchus.linear.RealMatrix;
import org.hipparchus.ode.events.Action;
import org.hipparchus.ode.nonstiff.AdaptiveStepsizeIntegrator;
import org.hipparchus.ode.nonstiff.DormandPrince853Integrator;
import org.orekit.attitudes.Attitude;
import org.orekit.attitudes.LofOffset;
import org.orekit.attitudes.NadirPointing;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.estimation.measurements.ObservableSatellite;
import org.orekit.estimation.sequential.ConstantProcessNoise;
import org.orekit.estimation.sequential.KalmanEstimator;
import org.orekit.estimation.sequential.KalmanEstimatorBuilder;
import org.orekit.forces.ForceModel;
import org.orekit.forces.drag.DragForce;
import org.orekit.forces.drag.IsotropicDrag;
import org.orekit.forces.gravity.HolmesFeatherstoneAttractionModel;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.gravity.potential.NormalizedSphericalHarmonicsProvider;
import org.orekit.forces.radiation.IsotropicRadiationSingleCoefficient;
import org.orekit.forces.radiation.RadiationSensitive;
import org.orekit.forces.radiation.SolarRadiationPressure;
import org.orekit.frames.*;
import org.orekit.models.earth.atmosphere.Atmosphere;
import org.orekit.models.earth.atmosphere.HarrisPriester;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.OrbitType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.ToleranceProvider;
import org.orekit.propagation.conversion.DormandPrince853IntegratorBuilder;
import org.orekit.propagation.conversion.NumericalPropagatorBuilder;
import org.orekit.propagation.events.EventDetector;
import org.orekit.propagation.events.handlers.EventHandler;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.propagation.sampling.OrekitFixedStepHandler;
import org.orekit.utils.Constants;
import org.orekit.utils.ExtendedPositionProvider;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;
import org.orekit.estimation.measurements.PV;

import com.example.Parametres;
import com.example.View.Visulations;

public class Propagator_1
{   
    // Parametres de propagation
    public static double dP = 1.0;
    public static double minStep = 0.1;
    public static double maxStep = 300.0;
    public static double initStep = 60.0;
    public static OneAxisEllipsoid one_axis_earth = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,Constants.WGS84_EARTH_FLATTENING,FramesFactory.getITRF(IERSConventions.IERS_2010, true));
    public static ExtendedPositionProvider sun = CelestialBodyFactory.getSun();
    public static Atmosphere atmosphere = new HarrisPriester(CelestialBodyFactory.getSun(), one_axis_earth); 
    
    //Definition parametres matrices Kalman 
    private static final RealMatrix processNoiseMatrix = MatrixUtils.createRealDiagonalMatrix(new double[]{
        100000, 100000, 100000,  // position [m²]   
        0.1, 0.10, 0.10   // velocity [(m/s)²]
    });
    private static final RealMatrix initialStateCovariance = MatrixUtils.createRealDiagonalMatrix(new double[]{
        100, 100, 100,   // position in m² → almost zero uncertainty
        0.001,0.001, 0.001    // velocity in (m/s)² → almost zero uncertainty
    });

    /**  Propagateur numérique avec les orbites réelles
    * @param liste_par_sats_real_orbit : liste des paramètres des satellites avec orbites réelles (sans bruit)
    **/

   public static void propagator_real_orbit(List<Satellite> liste_par_sats_real_orbit){
       String name_file="real_sats";
       Visulations.create_CSV_files(name_file);
       if (EO_detection.EO_detection){
           Visulations.init_observation_csv();
       }
    // Paramétrage du propagateur numérique

        for  (Satellite p : liste_par_sats_real_orbit){
            NumericalPropagator propagator = generic_propagator(name_file,p);
            EO_setup(p, propagator);
        }
       Visulations.closeCSV();
        
    }


    public static void propagator_TLE (List<Satellite> listSatellite){
       for (Satellite sat : listSatellite){
               NumericalPropagator propagator = generic_propagator("TLE",sat);
               EO_setup(sat, propagator);
       }
        Visulations.closeCSV();
   }

    private static void EO_setup(Satellite sat, NumericalPropagator propagator) {
        if (EO_detection.EO_detection){
            NadirPointing nadirLaw = new NadirPointing(Parametres.frame, Parametres.earth);
            propagator.setAttitudeProvider(nadirLaw);
            EO_detection.EO_usage_detection(propagator,sat);
        }
        sat.launch_manoeuvre(propagator,sat.getMotor());
        propagator.propagate(sat.getPropagation_date().shiftedBy(Parametres.duration));
    }

    public static NumericalPropagator generic_propagator(String type_propa,Satellite satellite){
        NumericalPropagator propagator = new NumericalPropagator(Propagator_1.integrator(satellite));
        propagator.setOrbitType(OrbitType.CARTESIAN);
        propagator.setInitialState(satellite.get_s_initialState());
        if (!EO_detection.EO_detection) propagator.setAttitudeProvider(new LofOffset(Parametres.frame, LOFType.VNC));
        //Ajout des forces au propagateur
        Propagator_1.add_force_propagator(propagator,satellite.getArea(),satellite.getCd(),satellite.getSrpCrossSection(), satellite.getSrpCoeff());
        // If satcom activated, we start the sequence to deal with everything linked
        if (Ground_station.satcom_activated) Satcom.satcom_station_link(propagator,satellite);
        SpacecraftState initialState = satellite.get_s_initialState().addAdditionalData("Boresight", satellite.getBoresight());
        satellite.add_state(initialState);
        Handlers.step_handler stepHandler = new Handlers.step_handler(type_propa, satellite);


        propagator.getMultiplexer().add(40, stepHandler);
       return propagator;
    }

    
    /** 
    *@param liste_par_sats_noisy_orbit : liste des paramètres des satellites avec orbites bruitées
    *@param liste_par_sats_real_orbit : liste des paramètres des satellites avec orbites réelles (sans bruit) 
    * 
    * */

    public static void propagator_noisy_orbit(List<Satellite> liste_par_sats_noisy_orbit,List<Satellite> liste_par_sats_real_orbit){
        //Creation of the initial output csv file with basic info(names of colonnes) 
        Visulations.export_csv_kalman_init(liste_par_sats_noisy_orbit);

        for (int i = 0; i < liste_par_sats_noisy_orbit.size(); i++) {
            ObservableSatellite satellite = new ObservableSatellite(0);
            Satellite pNoisy = liste_par_sats_noisy_orbit.get(i);
            Satellite pReal  = liste_par_sats_real_orbit.get(i);

            // Setup Kalman estimator
            NumericalPropagatorBuilder builder =new NumericalPropagatorBuilder(pNoisy.get_Cartesian_Orbit(), new DormandPrince853IntegratorBuilder(minStep, maxStep, dP),pNoisy.get_type_anomalie(), 1.0);
            builder =Propagator_1.add_force_propagator(builder,pNoisy.getArea(),pNoisy.getCd(),pNoisy.getSrpCrossSection(), pNoisy.getSrpCoeff());
            KalmanEstimatorBuilder kalmanBuilder = new KalmanEstimatorBuilder();
            kalmanBuilder.addPropagationConfiguration(builder, new ConstantProcessNoise( initialStateCovariance,processNoiseMatrix));
            KalmanEstimator kalman = kalmanBuilder.build();

            // process at each step of the propagation
            double measurementInterval = initStep;
            
            int j=0;
            boolean has_been_detected_too_soon_ago=false;
            boolean already_detected=false;
           
            for (double t = 0; t <= Parametres.duration; t += measurementInterval) {
                
                // Get “true” position & velocity from real orbit
                PVCoordinates truePV = pReal.get_Cartesian_Orbit().getPVCoordinates(pReal.getPropagation_date().shiftedBy(t), Parametres.frame);
                SpacecraftState currentState = new SpacecraftState(pReal.get_Cartesian_Orbit().shiftedBy(t));         
                
                //Check visibility from ground stations
                boolean gs_detected= Ground_station.hasVisibleStations(currentState,pReal.getPropagation_date().shiftedBy(t));
                // Add measurement to Kalman filter if visible from ground station , otherwise add dummy measurement
                if (gs_detected){ 
                    //If it is not the first detection, cre
                    if (!already_detected){
                        //Get the IoD-Gauss initial estimation                        
                        PVCoordinates pvG = Ground_station.getIodGaussInstance(pReal.getPropagation_date().shiftedBy(t), pReal.getPropagation_date().shiftedBy(t+120), pReal.getPropagation_date().shiftedBy(t+240), Ground_station.which_station_visible(currentState,pReal.getPropagation_date().shiftedBy(t)),satellite,pReal);
                        // Create measurement uncertainties (sigma) and weights
                        PV meas = new PV(
                                pReal.getPropagation_date().shiftedBy(t),
                            pvG.getPosition(),
                            pvG.getVelocity(),
                            1000,
                            0.1,
                            1,
                            satellite
                        );                 
                        kalman.estimationStep(meas);
                        already_detected=true;
                        continue;
                    }
                    //Case where the satellite has not been detected too soon ago 
                    if ((has_been_detected_too_soon_ago && j==0)||!has_been_detected_too_soon_ago){
                        kalman=added_noisy_value(kalman, truePV, pReal.is_firing(pReal.getPropagation_date().shiftedBy(t)), satellite, t,pReal);
                        has_been_detected_too_soon_ago=true;
                        
                    }//Case where the satellite has been detected too soon ago
                    else if(has_been_detected_too_soon_ago && j!=0){
                        kalman=add_dummy_value(kalman, satellite, truePV, t,pReal);
                        has_been_detected_too_soon_ago=true;
                    }else {
                        throw new IllegalStateException("Unexpected state in detection logic.");
                    }
                }else {
                    //Goes back to false as it will be a new station so no laps in detection
                    has_been_detected_too_soon_ago=false;
                    if (!already_detected){
                        //Files CSV before detection to have a good number of points for visualization
                        Visulations.write_csv_before_detection(pNoisy); 
                        continue;
                    }
                    kalman=add_dummy_value(kalman, satellite, truePV, t,pReal);
                    j=0;
                }                
                Visulations.export_csv_kalman_add_step(pNoisy, t, kalman.getPhysicalEstimatedState(),gs_detected);
            }
        }      
    }

    /** This method adds a noisy measurement to the Kalman filter if the conditions are met
    * @param kalman :Kalman estimator created in propagator_noisy_orbit
    * @param truePV : true PVCoordinates of the satellite at time t
    * @param trigger : boolean to know if a manoeuvre is ongoing
    * @param satellite : ObservableSatellite object
    * @param t : time of the measurement 
    **/
    private static KalmanEstimator added_noisy_value(KalmanEstimator kalman,PVCoordinates truePV, boolean trigger,ObservableSatellite satellite, double t,Satellite pReal) {
                Random rng = new Random();  
                double sigmaPosition = 1;  // meters
                double sigmaVelocity = 5;  // m/s
                double baseWeight = 1;      // weight of measurement
                Vector3D noisyPos = new Vector3D(
                    truePV.getPosition().getX() + sigmaPosition*rng.nextGaussian(),  // position noise ~1000 m
                    truePV.getPosition().getY() + sigmaPosition*rng.nextGaussian(),
                    truePV.getPosition().getZ() + sigmaPosition*rng.nextGaussian()
                );
 
                Vector3D noisyVel;
                if (trigger) {
                    noisyVel = new Vector3D(
                    truePV.getVelocity().getX() + sigmaVelocity*2*rng.nextGaussian(), // velocity noise ~50 is a manoeuvre is ongoing
                    truePV.getVelocity().getY() + sigmaVelocity*2*rng.nextGaussian(),
                    truePV.getVelocity().getZ() + sigmaVelocity*2*rng.nextGaussian());
                }else{
                    noisyVel = new Vector3D(
                    truePV.getVelocity().getX() + sigmaVelocity*rng.nextGaussian(), // velocity noise ~0.1 m/s
                    truePV.getVelocity().getY() + sigmaVelocity*rng.nextGaussian(),
                    truePV.getVelocity().getZ() + sigmaVelocity*rng.nextGaussian());
                }

                PV meas = new PV(
                        pReal.getPropagation_date().shiftedBy(t),
                    noisyPos,
                    noisyVel,
                    sigmaPosition,
                    sigmaVelocity,
                    baseWeight,
                    satellite
                );
                kalman.estimationStep(meas);
        return kalman;
    }

    
    /** This method adds a dummy measurement to the Kalman filter. The weight is set to 0 so that the filter ignores it but still performs an estimation step.
    * @param kalman :Kalman estimator created in propagator_noisy_orbit
    * @param truePV : true PVCoordinates of the satellite at time t
    * @param satellite : ObservableSatellite object
    * @param t : time of the measurement 
    **/

    private static KalmanEstimator add_dummy_value(KalmanEstimator kalman,ObservableSatellite satellite,PVCoordinates truePV, double t, Satellite  pReal) {
        
        // True position but weight =0 therefore as ignored as possible by the Kalman filter
        PV dummyMeas = new PV(
                pReal.getPropagation_date().shiftedBy(t),
            truePV.getPosition(),   
            truePV.getVelocity(),   
            10000, 
            10000,
            0.0000,    
            satellite
        );
        kalman.estimationStep(dummyMeas);
        return kalman;
    }
    

    //Sets up the integrator for the propagator
    public static AdaptiveStepsizeIntegrator integrator(Orbiting_object p) {
        Orbit o=p.get_Cartesian_Orbit();
        final double[][] tolerance = ToleranceProvider.getDefaultToleranceProvider(dP).getTolerances(o, OrbitType.CARTESIAN);
        AdaptiveStepsizeIntegrator integrator = new DormandPrince853Integrator(minStep, maxStep, tolerance[0], tolerance[1]);
        integrator.setInitialStepSize(initStep);
        return integrator;
    }



    public static NumericalPropagator add_force_propagator(NumericalPropagator propagator, double area, double cd,double srpCrossSection, double srpCoeff) {
        NormalizedSphericalHarmonicsProvider provider =GravityFieldFactory.getNormalizedProvider(10, 10);
        ForceModel holmesFeatherstone =new HolmesFeatherstoneAttractionModel(FramesFactory.getITRF(IERSConventions.IERS_2010,true),provider);
        propagator.addForceModel(holmesFeatherstone);
        DragForce drag = new DragForce(atmosphere, new IsotropicDrag(area, cd));
        propagator.addForceModel(drag);
        RadiationSensitive srpSurface = new IsotropicRadiationSingleCoefficient(srpCrossSection, srpCoeff);
        SolarRadiationPressure srp = new SolarRadiationPressure(sun,one_axis_earth,srpSurface);
        propagator.addForceModel(srp);
        if (! EO_detection.EO_detection)propagator.setAttitudeProvider(new LofOffset(Parametres.frame, LOFType.VNC));
        return propagator;
    }

    public static NumericalPropagatorBuilder add_force_propagator(NumericalPropagatorBuilder propagator, double area, double cd,double srpCrossSection, double srpCoeff) {
        NormalizedSphericalHarmonicsProvider provider =GravityFieldFactory.getNormalizedProvider(10, 10);
        ForceModel holmesFeatherstone =new HolmesFeatherstoneAttractionModel(FramesFactory.getITRF(IERSConventions.IERS_2010,true),provider);
        propagator.addForceModel(holmesFeatherstone);
        DragForce drag = new DragForce(atmosphere, new IsotropicDrag(area, cd));
        propagator.addForceModel(drag);   
        RadiationSensitive srpSurface = new IsotropicRadiationSingleCoefficient(srpCrossSection, srpCoeff);
        SolarRadiationPressure srp = new SolarRadiationPressure(sun,one_axis_earth,srpSurface);
        propagator.addForceModel(srp);
        propagator.setAttitudeProvider(new LofOffset(Parametres.frame, LOFType.VNC));
        return propagator;
    }
}
