package com.example.Analytics_Propagator;

import java.util.SortedSet;
import com.example.Parametres;
import com.example.Analytics_Propagator.Type1.Propagator_1;
import org.orekit.estimation.measurements.generation.GatheringSubscriber;
import org.orekit.estimation.measurements.generation.Generator;
import org.orekit.estimation.measurements.generation.RangeBuilder;
import org.orekit.estimation.measurements.generation.AngularAzElBuilder;
import org.orekit.estimation.measurements.generation.EventBasedScheduler;
import org.orekit.estimation.measurements.EstimatedMeasurementBase;
import org.orekit.estimation.measurements.GroundStation;
import org.orekit.estimation.measurements.ObservableSatellite;
import org.orekit.estimation.measurements.ObservedMeasurement;
import org.orekit.orbits.OrbitType;
import org.orekit.propagation.conversion.DormandPrince853IntegratorBuilder;
import org.orekit.propagation.conversion.ODEIntegratorBuilder;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.propagation.events.ElevationDetector;
import org.orekit.estimation.measurements.generation.SignSemantic;
import org.orekit.time.FixedStepSelector;
import org.orekit.time.TimeScalesFactory;
import org.hipparchus.util.FastMath;

public class Least_squares_batch {
    
    public static ODEIntegratorBuilder integratorBuilder() {
        double minStep = Propagator_1.minStep;
        double maxStep = Propagator_1.maxStep;
        double positionTolerance = 1.0;
        return new DormandPrince853IntegratorBuilder(minStep, maxStep, positionTolerance);
    }
    
    /**
     * Création des mesures Range, Azimuth et Elevation avec vérification de visibilité
     * @param p Paramètres du satellite
     * @param groundStations Liste de toutes les stations au sol
     * @param measurementStep Intervalle entre les mesures (en secondes)
     */
    public static SortedSet<EstimatedMeasurementBase<?>> least_squares_estimation(
            Parametres p, 
            java.util.List<GroundStation> groundStations, 
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
        generator.addPropagator(propagator, p.get_Name());
        
        // Créer un ObservableSatellite pour les builders
        ObservableSatellite satellite = new ObservableSatellite(0);
        
        GatheringSubscriber subscriber = new GatheringSubscriber();
        generator.addSubscriber(subscriber);
        
        // ========== AJOUTER UN SCHEDULER POUR CHAQUE STATION ==========
        System.out.println("Configuration des stations au sol avec vérification de visibilité:");
        for (GroundStation groundStation : groundStations) {
            //System.out.println("  - Station: " + groundStation.getBaseFrame().getName());
            
            // Détecteur d'élévation : génère des mesures uniquement quand élévation > 5°
            ElevationDetector elevationDetector = new ElevationDetector(groundStation.getBaseFrame())
                .withConstantElevation(FastMath.toRadians(5.0));  // 5° au-dessus de l'horizon
            
            // SCHEDULER RANGE basé sur la visibilité
            RangeBuilder rangeBuilder = new RangeBuilder(
                null,
                groundStation,
                true,  // two-way measurement
                1.0,
                1.0,
                satellite
            );
            
            EventBasedScheduler<org.orekit.estimation.measurements.Range> rangeScheduler =
                new EventBasedScheduler<>(
                    rangeBuilder,
                    new FixedStepSelector(measurementStep, TimeScalesFactory.getUTC()),
                    propagator,
                    elevationDetector,
                    SignSemantic.FEASIBLE_MEASUREMENT_WHEN_POSITIVE
                );
            
            generator.addScheduler(rangeScheduler);
            
            // SCHEDULER AZIMUTH & ELEVATION basé sur la visibilité
            AngularAzElBuilder azElBuilder = new AngularAzElBuilder(
                null,
                groundStation,
                new double[]{FastMath.toRadians(0.01), FastMath.toRadians(0.01)},
                new double[]{1.0, 1.0},
                satellite
            );
            
            EventBasedScheduler<org.orekit.estimation.measurements.AngularAzEl> azElScheduler =
                new EventBasedScheduler<>(
                    azElBuilder,
                    new FixedStepSelector(measurementStep, TimeScalesFactory.getUTC()),
                    propagator,
                    elevationDetector,
                    SignSemantic.FEASIBLE_MEASUREMENT_WHEN_POSITIVE
                );
            
            generator.addScheduler(azElScheduler);
        }
        
        System.out.println("Total de stations configurées: " + groundStations.size());
        System.out.println("Élévation minimale: 5°");
        
        // Génération des mesures
        generator.generate(Parametres.date_orekit, 
                          Parametres.date_orekit.shiftedBy(Parametres.duration));
        
        // Récupération des mesures
        SortedSet<EstimatedMeasurementBase<?>> measurements = subscriber.getGeneratedMeasurements();
        
        System.out.println("\n========== MESURES GÉNÉRÉES ==========");
        System.out.println("Nombre total de mesures: " + measurements.size());
        System.out.println("Période: " + Parametres.duration + " secondes");
        System.out.println("Intervalle: " + measurementStep + " secondes");
        
        // Compter les mesures par station
        java.util.Map<String, Integer> measurementsByStation = new java.util.HashMap<>();
        
        // Extraire les mesures par station
        if (!measurements.isEmpty()) {
            Double lastRange = null;
            Double lastAzimuth = null;
            Double lastElevation = null;
            org.orekit.time.AbsoluteDate lastDate = null;
            String lastStation = null;
            
            for (EstimatedMeasurementBase<?> estimatedMeasurement : measurements) {
                ObservedMeasurement<?> measurement = estimatedMeasurement.getObservedMeasurement();
                String type = measurement.getClass().getSimpleName();
                double[] values = measurement.getObservedValue();
                lastDate = measurement.getDate();
                
                // Identifier la station qui a fait la mesure
                GroundStation station = null;
                if (type.equals("Range")) {
                    org.orekit.estimation.measurements.Range rangeMeas = 
                        (org.orekit.estimation.measurements.Range) measurement;
                    station = rangeMeas.getStation();
                    lastRange = values[0];
                } else if (type.equals("AngularAzEl")) {
                    org.orekit.estimation.measurements.AngularAzEl azElMeas = 
                        (org.orekit.estimation.measurements.AngularAzEl) measurement;
                    station = azElMeas.getStation();
                    lastAzimuth = values[0];
                    lastElevation = values[1];
                }
                
                if (station != null) {
                    lastStation = station.getBaseFrame().getName();
                    measurementsByStation.put(lastStation, 
                        measurementsByStation.getOrDefault(lastStation, 0) + 1);
                }
                
                //System.out.println("Type: " + type + " | Station: " + lastStation + 
                //                 " | Date: " + lastDate);
                
                if (type.equals("Range")) {
                    //System.out.println("  Range: " + lastRange/1000.0 + " km");
                } else if (type.equals("AngularAzEl")) {
                    //System.out.println("  Az: " + FastMath.toDegrees(lastAzimuth) + "°, " +
                    //                 "El: " + FastMath.toDegrees(lastElevation) + "°");
                    
                    if (lastRange != null && station != null) {
                        double[] xyz = azElRangeToECEF(lastAzimuth, lastElevation, 
                                                       lastRange, station, lastDate);
                        //System.out.println("  → ECEF: X=" + xyz[0] / 1000.0 + " km, " +
                        //        "Y=" + xyz[1] / 1000.0 + " km, " +
                        //        "Z=" + xyz[2] / 1000.0 + " km");
                    }
                }
            }
            
            // Afficher le résumé par station
            System.out.println("\n========== RÉSUMÉ PAR STATION ==========");
            for (java.util.Map.Entry<String, Integer> entry : measurementsByStation.entrySet()) {
                System.out.println("Station " + entry.getKey() + ": " + 
                                 entry.getValue() + " mesures");
            }
        }
        
        return measurements;
    }
    
    /**
     * Convert Azimuth/Elevation/Range to ECEF coordinates
     */
    public static double[] azElRangeToECEF(double azimuth, double elevation, 
                                            double range, GroundStation groundStation,
                                            org.orekit.time.AbsoluteDate date) {
        // Get topocentric position (North, East, Up)
        double cosEl = FastMath.cos(elevation);
        double north = range * cosEl * FastMath.cos(azimuth);
        double east = range * cosEl * FastMath.sin(azimuth);
        double up = range * FastMath.sin(elevation);
        
        org.hipparchus.geometry.euclidean.threed.Vector3D topoPosition = 
            new org.hipparchus.geometry.euclidean.threed.Vector3D(north, east, up);
        
        // Transform to inertial frame
        org.orekit.frames.TopocentricFrame topoFrame = groundStation.getBaseFrame();
        org.hipparchus.geometry.euclidean.threed.Vector3D ecefPosition = 
            topoFrame.getTransformTo(Parametres.frame, date).transformPosition(topoPosition);
        
        return new double[]{ecefPosition.getX(), ecefPosition.getY(), ecefPosition.getZ()};
    }
}