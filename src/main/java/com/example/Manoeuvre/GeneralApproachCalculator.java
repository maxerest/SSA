package com.example.Manoeuvre;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.nonstiff.DormandPrince853Integrator;
import org.orekit.control.heuristics.lambert.*;
import org.orekit.forces.ForceModel;
import org.orekit.forces.gravity.NewtonianAttraction;
import org.orekit.frames.Frame;
import org.orekit.frames.LOFType;
import org.orekit.frames.Transform;
import org.orekit.propagation.Propagator;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.ToleranceProvider;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;
import org.orekit.forces.maneuvers.Maneuver;

public class GeneralApproachCalculator {

    public static class ApproachResult {

        // Both Delta-V vectors are expressed in inertialFrame
        public final Vector3D deltaV1;
        public final Vector3D deltaV2;
        public final double totalDeltaV;

        public final AbsoluteDate dateManoeuvre1;
        public final AbsoluteDate dateManoeuvre2;

        public ApproachResult(Vector3D dv1, Vector3D dv2, AbsoluteDate startDate, AbsoluteDate endDate) {
            this.deltaV1 = dv1;
            this.deltaV2 = dv2;
            this.totalDeltaV = dv1.getNorm() + dv2.getNorm();
            this.dateManoeuvre1 = startDate;
            this.dateManoeuvre2 = endDate;
        }
    }

    public static ApproachResult calculateGeneralApproachDeltaV(
            SpacecraftState chaserState,
            SpacecraftState targetState,
            Propagator targetPropagator,
            NumericalPropagator chaserPropagator,
            Vector3D finalPositionQSW,
            Vector3D finalVelocityQSW,
            double transferTime,
            int nRevolutions,
            boolean posigrade) {

        if (transferTime <= 0) {
            throw new IllegalArgumentException(
                    "Transfer time must be positive");
        }

        if (!chaserState.isOrbitDefined()) {
            throw new IllegalArgumentException(
                    "Chaser state must define an orbit");
        }

        AbsoluteDate t0 = chaserState.getDate();

        if (Math.abs(targetState.getDate()
                .durationFrom(t0)) > 1e-6) {
            throw new IllegalArgumentException(
                    "Initial dates must match");
        }

        Frame inertialFrame = chaserState.getFrame();

        if (!inertialFrame.isPseudoInertial()) {
            throw new IllegalArgumentException(
                    "A pseudo-inertial frame is required");
        }

        AbsoluteDate tf = t0.shiftedBy(transferTime);

        // 1. Initial chaser state in the inertial frame
        PVCoordinates chaserInitialPV =
                chaserState.getPVCoordinates(inertialFrame);

        // 2. Propagate target to arrival time
        SpacecraftState targetFinal =
                targetPropagator.propagate(tf);

        PVCoordinates targetFinalPV =
                targetFinal.getPVCoordinates(inertialFrame);

        // 3. Build target QSW frame at arrival
        Transform inertialToQSW =
                LOFType.QSW.transformFromInertial(
                        tf,
                        targetFinalPV
                );

        // Desired relative position and velocity
        PVCoordinates desiredRelativePV =
                new PVCoordinates(
                        finalPositionQSW,
                        finalVelocityQSW
                );

        // Convert desired arrival state to inertial
        PVCoordinates desiredFinalPV =
                inertialToQSW.getInverse()
                        .transformPVCoordinates(desiredRelativePV);

        // 4. Lambert boundary conditions
        LambertBoundaryConditions boundary =
                new LambertBoundaryConditions(
                        t0,
                        chaserInitialPV.getPosition(),
                        tf,
                        desiredFinalPV.getPosition(),
                        inertialFrame
                );

        // 5. Initial Keplerian Lambert solution
        double mu = chaserState.getOrbit().getMu();

        LambertSolver lambert = new LambertSolver(mu);
        LambertBoundaryVelocities initialGuess =
                lambert.solve(
                        posigrade,
                        nRevolutions,
                        boundary
                );

        if (initialGuess == null) {
            throw new IllegalStateException(
                    "No Lambert solution for these conditions");
        }

        // 6. Differential correction using numerical dynamics
        chaserPropagator.setInitialState(chaserState);

        LambertDifferentialCorrector corrector = new LambertDifferentialCorrector(boundary);

        corrector.setInitialMass(chaserState.getMass());
        corrector.setMaxIter(15);
        corrector.setPositionTolerance(1);
        LambertBoundaryVelocities corrected = corrector.solve(chaserPropagator, initialGuess.getInitialVelocity());

        if (corrected == null) {
            throw new IllegalStateException("Differential correction did not converge");
        }

        // 7. Initial manoeuvre
        Vector3D deltaV1 = corrected.getInitialVelocity().subtract(chaserInitialPV.getVelocity());

        // 8. Braking manoeuvre at tf
        Vector3D deltaV2 = desiredFinalPV.getVelocity().subtract(corrected.getTerminalVelocity());

        return new ApproachResult(deltaV1, deltaV2,t0,tf);
    }

    // Result of the automatic Lambert search
    public static class BestApproachResult {

        public final ApproachResult manoeuvres;

        public final double transferTime;
        public final int nRevolutions;
        public final boolean posigrade;

        public final double burnDuration1;
        public final double burnDuration2;

        public final AbsoluteDate burnDate1;
        public final AbsoluteDate burnDate2;

        public BestApproachResult(
                ApproachResult manoeuvres,
                double transferTime,
                int nRevolutions,
                boolean posigrade,
                double burnDuration1,
                double burnDuration2,
                AbsoluteDate burnDate1,
                AbsoluteDate burnDate2) {

            this.manoeuvres = manoeuvres;
            this.transferTime = transferTime;
            this.nRevolutions = nRevolutions;
            this.posigrade = posigrade;

            this.burnDuration1 = burnDuration1;
            this.burnDuration2 = burnDuration2;

            this.burnDate1 = burnDate1;
            this.burnDate2 = burnDate2;
        }
    }



    public static BestApproachResult findBestApproach(
            SpacecraftState chaserState,
            SpacecraftState targetState,
            Propagator targetPropagator,
            NumericalPropagator correctionPropagator,
            Vector3D finalPositionQSW,
            Vector3D finalVelocityQSW,
            double minTransferTime,
            double maxTransferTime,
            double timeStep,
            int maxRevolutions,
            double thrust,
            double isp) {
        if (!(thrust > 0) || !(isp > 0) ||
                !(chaserState.getMass() > 0) ||
                !(minTransferTime > 0) ||
                maxTransferTime < minTransferTime ||
                !(timeStep > 0) ||
                maxRevolutions < 0 ||
                !Double.isFinite(thrust) ||
                !Double.isFinite(isp)) {

            throw new IllegalArgumentException(
                    "Invalid search or propulsion parameters");
        }

        BestApproachResult best = null;
        double bestDeltaV = Double.POSITIVE_INFINITY;

        double mass0 = chaserState.getMass();
        double ve = isp * Constants.G0_STANDARD_GRAVITY;

        int tested = 0;
        int converged = 0;
        int rejectedByMotor = 0;
        int feasible = 0;

        SpacecraftState originalState = correctionPropagator.getInitialState();
        double steps = Math.floor((maxTransferTime - minTransferTime) / timeStep
        );
        int totalSteps = (int) steps + 1;
        int progressInterval = totalSteps > 1000 ? 5 : 10;
        int past_milestone=0;
        try {


            for (double i = 0; i <= steps; i++) {
                double transferTime = minTransferTime + i * timeStep;

                for (int nRev = 0;
                     nRev <= maxRevolutions;
                     nRev++) {

                    for (boolean posigrade :
                            new boolean[]{true, false}) {

                        tested++;

                        try {

                            ApproachResult result =
                                    calculateGeneralApproachDeltaV(
                                            chaserState,
                                            targetState,
                                            targetPropagator,
                                            correctionPropagator,
                                            finalPositionQSW,
                                            finalVelocityQSW,
                                            transferTime,
                                            nRev,
                                            posigrade
                                    );

                            if (result == null || !Double.isFinite(result.totalDeltaV)) {
                                continue;
                            }

                            double dv1 = result.deltaV1.getNorm();
                            double dv2 = result.deltaV2.getNorm();

                            if (!Double.isFinite(dv1) || !Double.isFinite(dv2)) {
                                continue;
                            }

                            converged++;

                            // =====================================
                            // 1. Calculate real burn durations
                            // =====================================

                            double duration1 = mass0 * ve / thrust * (-Math.expm1(-dv1 / ve));

                            double massAfterBurn1 = mass0 * Math.exp(-dv1 / ve);

                            double duration2 = massAfterBurn1 * ve / thrust * (-Math.expm1(-dv2 / ve));

                            if (!Double.isFinite(duration1) ||
                                    !Double.isFinite(duration2)) {
                                continue;
                            }

                            // =====================================
                            // 2. Motor feasibility verification
                            // =====================================

                            AbsoluteDate burn1Start = result.dateManoeuvre1;
                            AbsoluteDate burn1End = burn1Start.shiftedBy(duration1);

                            AbsoluteDate burn2Start = result.dateManoeuvre2;
                            AbsoluteDate burn2End = burn2Start.shiftedBy(duration2);

                            // Check 1: First burn must end before second burn starts
                            boolean noOverlap = burn1End.compareTo(burn2Start) <= 0;


                            // Final feasibility
                            boolean feasibleMotor =
                                    Double.isFinite(duration1) &&
                                            Double.isFinite(duration2) &&
                                            duration1 >= 0 &&
                                            duration2 >= 0 &&
                                            noOverlap;

                            if (!feasibleMotor) {

                                rejectedByMotor++;

                                System.out.printf(
                                        "REJECTED: T=%.0f s | " +
                                                "nRev=%d | posigrade=%b | " +
                                                "DV=%.3f m/s | " +
                                                "Burn1=%.1f s | Burn2=%.1f s%n",
                                        transferTime,
                                        nRev,
                                        posigrade,
                                        result.totalDeltaV,
                                        duration1,
                                        duration2
                                );

                                continue;
                            }

                            feasible++;

                            // =====================================
                            // 4. Select lowest Delta-V
                            // =====================================

                            if (result.totalDeltaV < bestDeltaV) {

                                bestDeltaV = result.totalDeltaV;

                                //It has to start at the chosen time which is defined by the chaser state
                                AbsoluteDate date1 = chaserState.getDate();
                                AbsoluteDate arrivalDate = date1.shiftedBy(transferTime);

                                // Burn 2 ends at nominal arrival
                                AbsoluteDate date2 = arrivalDate.shiftedBy(-duration2);

                                best = new BestApproachResult(
                                        result,
                                        transferTime,
                                        nRev,
                                        posigrade,
                                        duration1,
                                        duration2,
                                        date1,
                                        date2
                                );
                                System.out.println("[JAVA] New best approach found DV: "+result.totalDeltaV);
                            }

                        } catch (RuntimeException e) {
                                    e.getMessage();

                        } finally {
                            correctionPropagator.clearMatricesComputation();

                            correctionPropagator.resetInitialState(originalState);
                        }
                    }
                }
                // Progress update
                int completed = (int) i + 1;
                int progress = (int) (100.0 * completed / totalSteps);
                //if nextProgress
                if(progress >= past_milestone+progressInterval) {

                    System.out.printf(
                            "Lambert search: %d%% (%d/%d steps)%n",
                            past_milestone+progressInterval, completed, totalSteps
                    );
                    past_milestone+=progressInterval;
                }
            }


        } finally {
            correctionPropagator.clearMatricesComputation();
            correctionPropagator.resetInitialState(originalState);
        }

        System.out.println("========== SEARCH RESULTS ==========");
        System.out.println("Configurations tested: " + tested);
        System.out.println("Converged: " + converged);
        System.out.println("Rejected by motor: " + rejectedByMotor);
        System.out.println("Feasible solutions: " + feasible);

        if (best == null) {
            throw new IllegalStateException("No motor-feasible Lambert approach found. Try longer transfers, higher thrust, or a continuous low-thrust approach model.");
        }

        System.out.println("========== BEST APPROACH ==========");
        System.out.println("Transfer time: " + best.transferTime);
        System.out.println("Revolutions: " + best.nRevolutions);
        System.out.println("Posigrade: " + best.posigrade);

        System.out.println("DeltaV1: " + best.manoeuvres.deltaV1);
        System.out.println("DeltaV2: " + best.manoeuvres.deltaV2);
        System.out.println("Total DeltaV: " + bestDeltaV + " m/s");

        System.out.println("Burn 1 start: " + best.burnDate1);
        System.out.println("Burn 1 duration: " + best.burnDuration1 + " s");
        System.out.println("Burn 2 start: " + best.burnDate2);
        System.out.println("Burn 2 duration: " + best.burnDuration2 + " s");

        return best;
    }

    public static NumericalPropagator createCorrectionPropagator(
            NumericalPropagator original,
            SpacecraftState initialState) {

        // Integration parameters
        double minStep = 0.01;
        double maxStep = 300.0;
        double positionTolerance = 0.1;

        double[][] tolerances =
                ToleranceProvider
                        .getDefaultToleranceProvider(positionTolerance)
                        .getTolerances(
                                initialState.getOrbit(),
                                original.getOrbitType(),
                                original.getPositionAngleType()
                        );

        DormandPrince853Integrator integrator =
                new DormandPrince853Integrator(
                        minStep,
                        maxStep,
                        tolerances[0],
                        tolerances[1]
                );

        NumericalPropagator correctionPropagator =
                new NumericalPropagator(
                        integrator,
                        original.getAttitudeProvider()
                );

        correctionPropagator.setOrbitType(original.getOrbitType());
        correctionPropagator.setPositionAngleType(original.getPositionAngleType());
        correctionPropagator.setInitialState(initialState);
        // Copy relevant force models
        for (ForceModel force : original.getAllForceModels()) {
            // Central attraction is automatically handled.
            if (force instanceof NewtonianAttraction) {
                continue;
            }
            correctionPropagator.addForceModel(force);
        }
        return correctionPropagator;
    }

}
