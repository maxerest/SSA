package com.example.Manoeuvre;

import com.example.App;
import com.example.Orbiting_object.Satellite;
import com.example.Parametres;
import com.example.View.Visulations;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.json.JSONObject;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.forces.maneuvers.ImpulseManeuver;
import org.orekit.propagation.Propagator;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.DateDetector;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.attitudes.FrameAlignedProvider;
import java.util.Comparator;
import java.util.List;
import org.orekit.propagation.BoundedPropagator;
import org.orekit.propagation.EphemerisGenerator;

public class Rendez_vous {
private final Satellite sat_target;
private final Satellite sat_chaser;
private final AbsoluteDate start_date;
private List<Double> manoeuvres;

    public Rendez_vous(String message) {
        String json = message.startsWith("APPROACH_CREATE:")
                ? message.substring("APPROACH_CREATE:".length())
                : message;
        JSONObject payload = new JSONObject(json);
        // Extract satellite names
        String chaserName = payload.getString("chaserSatName");
        String targetName = payload.getString("targetSatName");
        String triggerType = payload.getString("triggerType");
        double startTime = payload.getDouble("triggerValue");

        if (!Double.isFinite(startTime) || startTime < 0) {
            throw new IllegalArgumentException(
                    "[JAVA] Invalid approach start time"
            );
        }

        sat_target = App.liste_par_sats_real_orbit.stream()
                .filter(sat -> sat.get_Name().equals(targetName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "[JAVA] Target satellite not found: " + targetName
                ));
        sat_chaser = App.liste_par_sats_real_orbit.stream()
                .filter(sat -> sat.get_Name().equals(chaserName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "[JAVA] Chaser satellite not found: " + chaserName
                ));


        if (chaserName.equals(targetName)) {
            throw new IllegalArgumentException(
                    "[JAVA] Chaser and target must be different"
            );
        }
        // Convert simulation seconds to AbsoluteDate
        start_date = Parametres.date_orekit.shiftedBy(startTime);

        calculate_best_manoeuvres();
    }

    public void calculate_best_manoeuvres() {
        System.out.println("[JAVA] Starting Approach calculation");

        SpacecraftState chaserState = sat_chaser.get_liste_state_propa().stream()
                .min(Comparator.comparingDouble(
                        state -> Math.abs(
                                state.getDate().durationFrom(start_date)
                        )
                )) .orElseThrow(() ->
                        new IllegalArgumentException(
                                "[JAVA] No spacecraft states available for chaser"
                        )
                );
        SpacecraftState targetState =sat_target.get_liste_state_propa().stream()
                .min(Comparator.comparingDouble(
                        state -> Math.abs(
                                state.getDate().durationFrom(start_date)
                        )
                )).orElseThrow(() ->
                        new IllegalArgumentException(
                                "[JAVA] No spacecraft states available for target"
                        )
                );
        NumericalPropagator correctionPropagator = GeneralApproachCalculator.createCorrectionPropagator(sat_chaser.getPropagator(), chaserState);
        double timeDifference = Math.abs(chaserState.getDate().durationFrom(targetState.getDate()));

        if (timeDifference > 1e-3) {
            System.out.println(
                    "[JAVA] Warning: states are not synchronized. " +
                            "Time difference = " + timeDifference + " s"
            );
        }
        // Propagators configured for the respective spacecraft
        Propagator targetPropagator = sat_target.getEphemeris();
        NumericalPropagator chaserPropagator =sat_chaser.getPropagator() ;

        // Holding point 100 m behind the target
        Vector3D finalPositionQSW =
                new Vector3D(0, -100, 0);

        Vector3D finalVelocityQSW = Vector3D.ZERO;

        double thrust = sat_chaser.getMotor().getThrust();
        double isp = sat_chaser.getMotor().getISP();
        // Target orbital period in seconds
        double orbitalPeriod = targetState.getOrbit().getKeplerianPeriod();

        // Estimated maximum number of revolutions
        int maxRevolutions =2;//(int) Math.ceil(86400 / orbitalPeriod) + 1;
        GeneralApproachCalculator.BestApproachResult best =
                GeneralApproachCalculator.findBestApproach(
                        chaserState,
                        targetState,
                        targetPropagator,
                        correctionPropagator,
                        finalPositionQSW,
                        finalVelocityQSW,
                        60,     // Minimum time
                        86400,    // Maximum time
                        900.0,      // Step
                        maxRevolutions,          // Maximum revolutions// to adapt with the orbit of the sat
                        thrust,
                        isp
                );

        System.out.println("[JAVA] Launching general approach");
        double transferTime = best.transferTime;

        new_approach_manoeuvres(
                best.manoeuvres,
                chaserState,
                best.transferTime
        );

        System.out.println("Best transfer duration: " + transferTime);
        System.out.println("Best DeltaV: " + best.manoeuvres.totalDeltaV);

        updateSatelliteCSV(sat_chaser);
    }



    // To add inside Rendez_vous
    public void new_approach_manoeuvres(
            GeneralApproachCalculator.ApproachResult result,
            SpacecraftState chaserStateAtStart,
            double transferTime) {

        NumericalPropagator propagator =
                sat_chaser.getPropagator();

        double isp = sat_chaser.getMotor().getISP();

        // Use the exact dates from the Lambert calculation.
        AbsoluteDate date1 = result.dateManoeuvre1;
        AbsoluteDate date2 = result.dateManoeuvre2;

        // Ensure the user-requested date matches the calculated one.
        if (Math.abs(date1.durationFrom(start_date)) > 1e-6) {
            throw new IllegalStateException(
                    "[JAVA] Lambert start date differs from requested start_date"
            );
        }

        if (Math.abs(date2.durationFrom(
                date1.shiftedBy(transferTime))) > 1e-6) {
            throw new IllegalStateException(
                    "[JAVA] Lambert arrival date does not match transferTime"
            );
        }

        // Lambert delta-V vectors are inertial.
        // Align spacecraft axes with that inertial frame.
        AttitudeProvider attitude =
                new FrameAlignedProvider(
                        chaserStateAtStart.getFrame()
                );

        // First impulse
        if (result.deltaV1.getNorm() > 0) {

            propagator.addEventDetector(
                    new ImpulseManeuver(
                            new DateDetector(date1),
                            attitude,
                            result.deltaV1,
                            isp
                    )
            );
        }

        // Second impulse
        if (result.deltaV2.getNorm() > 0) {

            propagator.addEventDetector(
                    new ImpulseManeuver(
                            new DateDetector(date2),
                            attitude,
                            result.deltaV2,
                            isp
                    )
            );
        }

        System.out.println("========== IMPULSIVE APPROACH ==========");
        System.out.println("Burn 1 date: " + date1);
        System.out.println("Burn 1 deltaV: " + result.deltaV1);
        System.out.println("Burn 2 date: " + date2);
        System.out.println("Burn 2 deltaV: " + result.deltaV2);
        System.out.println("Total deltaV: " + result.totalDeltaV);
    }




    private void updateSatelliteCSV(Satellite satellite) {

        NumericalPropagator propagator =
                satellite.getPropagator();

        AbsoluteDate simulationStart =
                satellite.get_s_initialState().getDate();

        AbsoluteDate simulationEnd =
                Parametres.date_orekit.shiftedBy(
                        Parametres.duration
                );

        if (simulationEnd.compareTo(simulationStart) <= 0) {
            throw new IllegalArgumentException(
                    "Invalid simulation dates"
            );
        }

        System.out.println(
                "[JAVA] Regenerating trajectory for " + satellite.get_Name()
        );

        // 1. Check existing step handlers
        System.out.println(
                "Step handlers: " +
                        propagator.getMultiplexer().getHandlers().size()
        );

        // 2. Reset propagator to original state
        // The registered Maneuver force models remain.
        propagator.resetInitialState(
                satellite.get_s_initialState()
        );

        // 3. Remove old states from satellite history
        satellite.get_liste_state_propa().clear();

        // 4. Close the CSV writer
        try {
            Visulations.csvWriters.get("real_sats").close();

            // 5. Delete the satellite's old rows
            Visulations.deleteSatelliteRows(
                    satellite.get_Name()
            );

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to update CSV for " + satellite.get_Name(),
                    e
            );
        }

        // 6. Reopen CSV in APPEND mode
        Visulations.reopen_CSV_writer_append();

        // 7. Generate an ephemeris from the new propagation
        EphemerisGenerator generator =
                propagator.getEphemerisGenerator();

        // 8. Propagate through the complete simulation
        SpacecraftState finalState =
                propagator.propagate(simulationEnd);

        // 9. Save the updated ephemeris
        BoundedPropagator newEphemeris =
                generator.getGeneratedEphemeris();

        satellite.setEphemeris(newEphemeris);

        // 10. Flush the writer
        try {
            Visulations.csvWriters
                    .get("real_sats")
                    .flush();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "CSV flush failed", e
            );
        }
    }


}

