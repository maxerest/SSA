package com.example.Analytics_Propagator;

import java.util.SortedSet;
import com.example.Parametres;
import com.example.Analytics_Propagator.Type1.Propagator_1;
import com.example.Orbiting_object.*;

import org.orekit.estimation.measurements.generation.GatheringSubscriber;
import org.orekit.estimation.measurements.generation.Generator;
import org.orekit.estimation.measurements.generation.RangeBuilder;
import org.orekit.estimation.measurements.generation.AngularAzElBuilder;
import org.orekit.estimation.measurements.generation.EventBasedScheduler;
import org.orekit.estimation.measurements.EstimatedMeasurementBase;
import org.orekit.estimation.measurements.GroundStation;
import org.orekit.estimation.measurements.ObservableSatellite;
import org.orekit.orbits.OrbitType;
import org.orekit.propagation.conversion.DormandPrince853IntegratorBuilder;
import org.orekit.propagation.conversion.ODEIntegratorBuilder;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.propagation.events.ElevationDetector;
import org.orekit.estimation.measurements.generation.SignSemantic;
import org.orekit.time.FixedStepSelector;
import org.orekit.time.TimeScalesFactory;
import org.hipparchus.util.FastMath;
import org.orekit.estimation.measurements.AngularAzEl;
import org.orekit.estimation.measurements.Range;
import org.hipparchus.random.GaussianRandomGenerator;
import org.hipparchus.random.JDKRandomGenerator;
import org.hipparchus.random.RandomGenerator;
import org.hipparchus.linear.MatrixUtils;
import org.hipparchus.linear.RealMatrix;
import org.hipparchus.random.CorrelatedRandomVectorGenerator;

public class Least_squares_batch {

    public static ODEIntegratorBuilder integratorBuilder() {
        double minStep = Propagator_1.minStep;
        double maxStep = Propagator_1.maxStep;
        double positionTolerance = 1.0;
        return new DormandPrince853IntegratorBuilder(minStep, maxStep, positionTolerance);
    }

    /**
     * Création des mesures Range, Azimuth et Elevation avec vérification de
     * visibilité
     * 
     * @param p               Paramètres du satellite
     * @param groundStations  Liste de toutes les stations au sol
     * @param measurementStep Intervalle entre les mesures (en secondes)
     */
    
    public static SortedSet<EstimatedMeasurementBase<?>> least_squares_estimation(
            Orbiting_object p,
            java.util.List<GroundStation> groundStations,
            double measurementStep) {

        // Création du propagateur spécifique en reprenant les mêmes paramètres que pour
        // la propagation du satellite
        NumericalPropagator propagator = new NumericalPropagator(Propagator_1.integrator(p));
        propagator.setOrbitType(OrbitType.CARTESIAN);
        propagator.setInitialState(p.get_s_initialState());

        // Ajout des forces au modèle
        Propagator_1.add_force_propagator(propagator, p.get_area(), p.get_cd(),
                p.get_srpCrossSection(), p.get_srpCoeff());

        // Création du générateur de mesures
        Generator generator = new Generator();
        generator.addPropagator(propagator, p.get_Name());
        // Créer un ObservableSatellite pour les builders
        ObservableSatellite satellite = new ObservableSatellite(0);
        GatheringSubscriber subscriber = new GatheringSubscriber();
        generator.addSubscriber(subscriber);
        RandomGenerator randomGenerator = new JDKRandomGenerator();
        GaussianRandomGenerator gaussianGenerator = new GaussianRandomGenerator(randomGenerator);
        GaussianRandomGenerator gaussianGeneratorAzEl = new GaussianRandomGenerator(randomGenerator);

        // ========== AJOUTER UN SCHEDULER POUR CHAQUE STATION ==========
        for (GroundStation groundStation : groundStations) {
            RealMatrix covarianceMatrix = MatrixUtils.createRealMatrix(1, 1);
            covarianceMatrix.setEntry(0, 0, 1.0); // variance = 1.0
            CorrelatedRandomVectorGenerator noiseSource = new CorrelatedRandomVectorGenerator(
                    covarianceMatrix,
                    1e-1, // small singular value threshold
                    gaussianGenerator);
            ElevationDetector elevationDetector = new ElevationDetector(groundStation.getBaseFrame())
                    .withConstantElevation(Parametres.elevation);

            // SCHEDULER RANGE basé sur la visibilité
            RangeBuilder rangeBuilder = new RangeBuilder(
                    noiseSource,
                    groundStation,
                    true, // two-way measurement
                    10,
                    1.0,
                    satellite);

            EventBasedScheduler<Range> rangeScheduler = new EventBasedScheduler<>(
                    rangeBuilder,
                    new FixedStepSelector(measurementStep, TimeScalesFactory.getUTC()),
                    propagator,
                    elevationDetector,
                    SignSemantic.FEASIBLE_MEASUREMENT_WHEN_POSITIVE);

            generator.addScheduler(rangeScheduler);

            RealMatrix covarianceMatrixAzEl = MatrixUtils.createRealMatrix(2, 2);
            covarianceMatrixAzEl.setEntry(0, 0, 0.000003); // Az variance
            covarianceMatrixAzEl.setEntry(0, 1, 0.000003); // El variance
            covarianceMatrixAzEl.setEntry(1, 0, 0.000003);
            covarianceMatrixAzEl.setEntry(1, 1, 0.000003);

            CorrelatedRandomVectorGenerator noiseSourceAzEl = new CorrelatedRandomVectorGenerator(
                    covarianceMatrixAzEl, 1e-12, gaussianGeneratorAzEl);
            AngularAzElBuilder azElBuilder = new AngularAzElBuilder(
                    noiseSourceAzEl,
                    groundStation,
                    new double[] { FastMath.toRadians(0.01), FastMath.toRadians(0.01) },
                    new double[] { 1.0, 1.0 },
                    satellite);

            EventBasedScheduler<AngularAzEl> azElScheduler = new EventBasedScheduler<>(
                    azElBuilder,
                    new FixedStepSelector(measurementStep, TimeScalesFactory.getUTC()),
                    propagator,
                    elevationDetector,
                    SignSemantic.FEASIBLE_MEASUREMENT_WHEN_POSITIVE);

            generator.addScheduler(azElScheduler);
        }

        // Génération des mesures
        generator.generate(Parametres.date_orekit,
                Parametres.date_orekit.shiftedBy(Parametres.duration));

        // Récupération des mesures
        SortedSet<EstimatedMeasurementBase<?>> measurements = subscriber.getGeneratedMeasurements();
        return measurements;
    }

    /**
     * Convert Azimuth/Elevation/Range to ECEF coordinates
     */
    public static double[] azElRangeToECEF(double azimuth, double elevation,
            double range, GroundStation groundStation,
            org.orekit.time.AbsoluteDate date) {

        // ATTENTION: L'ordre est EAST, NORTH, UP 
        double cosEl = FastMath.cos(elevation);
        double sinEl = FastMath.sin(elevation);
        double cosAz = FastMath.cos(azimuth);
        double sinAz = FastMath.sin(azimuth);

        // X = East, Y = North, Z = Up
        double east = range * cosEl * sinAz; // X axis
        double north = range * cosEl * cosAz; // Y axis
        double up = range * sinEl; // Z axis

        org.hipparchus.geometry.euclidean.threed.Vector3D topoPosition = new org.hipparchus.geometry.euclidean.threed.Vector3D(
                east, north, up);

        // 2. Transformer vers ECEF
        org.orekit.frames.TopocentricFrame topoFrame = groundStation.getBaseFrame();
        org.hipparchus.geometry.euclidean.threed.Vector3D satellitePosition = topoFrame
                .getTransformTo(Parametres.frame, date)
                .transformPosition(topoPosition);

        return new double[] {
                satellitePosition.getX(),
                satellitePosition.getY(),
                satellitePosition.getZ()
        };
    }
}