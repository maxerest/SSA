package com.example.Analytics_Propagator;

import java.util.SortedSet;
import com.example.Parametres;
import com.example.Analytics_Propagator.Type1.Propagator_1;
import org.orekit.estimation.measurements.generation.GatheringSubscriber;
import org.orekit.estimation.measurements.generation.Generator;
import org.orekit.estimation.measurements.generation.RangeBuilder;
import org.orekit.estimation.measurements.generation.AngularAzElBuilder;
import org.orekit.estimation.measurements.generation.ContinuousScheduler;
import org.orekit.estimation.measurements.EstimatedMeasurementBase;
import org.orekit.estimation.measurements.GroundStation;
import org.orekit.estimation.measurements.ObservableSatellite;
import org.orekit.orbits.OrbitType;
import org.orekit.propagation.conversion.DormandPrince853IntegratorBuilder;
import org.orekit.propagation.conversion.ODEIntegratorBuilder;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.FixedStepSelector;
import org.orekit.time.TimeScalesFactory;
import org.hipparchus.util.FastMath;

public class Least_squares_batch {

    public static ODEIntegratorBuilder integratorBuilder() {
        double minStep = Propagator_1.minStep;     // seconds
        double maxStep = Propagator_1.maxStep;     // seconds
        double positionTolerance = 1.0;            // meters
        return new DormandPrince853IntegratorBuilder(minStep, maxStep, positionTolerance);
    }
    
    /**
     * Création des mesures Range, Azimuth et Elevation
     * @param p Paramètres du satellite
     * @param groundStation Station au sol pour les mesures
     * @param measurementStep Intervalle entre les mesures (en secondes)
     */
    public static SortedSet<EstimatedMeasurementBase<?>> least_squares_estimation(
            Parametres p, 
            GroundStation groundStation, 
            double measurementStep) {
        
        // Création du propagateur
        NumericalPropagator propagator = new NumericalPropagator(Propagator_1.integrator(p));
        propagator.setOrbitType(OrbitType.CARTESIAN);
        propagator.setInitialState(p.s_initialState);
        
        // Ajout des forces au modèle
        Propagator_1.add_force_propagator(propagator, p.get_area(), p.get_cd(), 
                                         p.get_srpCrossSection(), p.get_srpCoeff());
        
        // Création du générateur de mesures
        Generator generator = new Generator();
        
        // Ajouter le propagateur avec un nom (String)
        generator.addPropagator(propagator, p.get_Name());
        
        // Créer un ObservableSatellite pour les builders
        ObservableSatellite satellite = new ObservableSatellite(0);
        
        GatheringSubscriber subscriber = new GatheringSubscriber();
        generator.addSubscriber(subscriber);
        
        // ========== SCHEDULER 1: RANGE (Distance) ==========
        RangeBuilder rangeBuilder = new RangeBuilder(
            null,  // random generator (null = pas de bruit)
            groundStation,  // station au sol directement
            false,
            1.0,   // sigma (précision en mètres)
            1.0,   // base weight
            satellite  // le satellite à observer
        );
        
        generator.addScheduler(new ContinuousScheduler<>(
            rangeBuilder,
            new FixedStepSelector(measurementStep, TimeScalesFactory.getUTC())
        ));
        
        // ========== SCHEDULER 2: AZIMUTH & ELEVATION ==========
        AngularAzElBuilder azElBuilder = new AngularAzElBuilder(
            null,  // random generator (null = pas de bruit)
            groundStation,  // station au sol directement
            new double[]{FastMath.toRadians(0.01), FastMath.toRadians(0.01)},  // sigma [az, el] en radians
            new double[]{1.0, 1.0},  // base weights [az, el]
            satellite  // le satellite à observer
        );
        
        generator.addScheduler(new ContinuousScheduler<>(
            azElBuilder,
            new FixedStepSelector(measurementStep, TimeScalesFactory.getUTC())
        ));
        
        // Génération des mesures
        generator.generate(Parametres.date_orekit, 
                          Parametres.date_orekit.shiftedBy(Parametres.duration));
        
        // Récupération des mesures
        SortedSet<EstimatedMeasurementBase<?>> measurements = subscriber.getGeneratedMeasurements();
        
        System.out.println("========== MESURES GÉNÉRÉES ==========");
        System.out.println("Nombre total de mesures: " + measurements.size());
        System.out.println("Période: " + Parametres.duration + " secondes");
        System.out.println("Intervalle: " + measurementStep + " secondes");
        System.out.println("Station: " + groundStation.getBaseFrame().getName());
        // Extraire la dernière mesure de chaque type
        if (!measurements.isEmpty()) {
            Double lastRange = null;
            Double lastAzimuth = null;
            Double lastElevation = null;
           
            // Parcourir les mesures pour trouver les dernières valeurs
            for (EstimatedMeasurementBase<?> measurement : measurements) {
                double[] values = measurement.getObservedValue();
            
                double lat = 0;
                double lon = 0;
                double alt = 0; // default altitude
            
                if (values.length >= 2) {
                    // assume first two values are latitude and longitude in degrees
                    lat = values[0];
                    lon = values[1];
                } else {
                    alt=values[0];
                    continue;
                }
                double[] xyz = latLonToECEF(lat, lon, alt);
                System.out.println("ECEF: " + xyz[0] + ", " + xyz[1] + ", " + xyz[2]+" t:"+measurement.getDate());
            }
        }
        return measurements;
    }
    public static double[] latLonToECEF(double latDeg, double lonDeg, double altKm) {
        double R =6370137.0; // Earth's radius in meters
        double lat = latDeg;
        double lon = lonDeg;
        double r = R + altKm; // convert km to meters
        double x = r * Math.cos(lat) * Math.cos(lon);
        double y = r * Math.cos(lat) * Math.sin(lon);
        double z = r * Math.sin(lat);
        return new double[]{x, y, z};
    }
}