package com.example.Analytics_Propagator.Type1;

import com.example.Ground_stations.EO_detection;
import com.example.Ground_stations.Ground_station;
import com.example.Ground_stations.Satcom;
import com.example.Orbiting_object.Satellite;
import com.example.Parametres;
import com.example.View.Visulations;
import org.hipparchus.geometry.Space;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.hipparchus.util.FastMath;
import org.orekit.attitudes.Attitude;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.files.ccsds.utils.lexical.ParseToken;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.TopocentricFrame;
import org.orekit.frames.Transform;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.EventDetector;
import org.orekit.propagation.events.handlers.EventHandler;
import org.orekit.propagation.sampling.OrekitFixedStepHandler;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.IERSConventions;

import javax.sound.midi.SysexMessage;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

public class Handlers {
    // Calculation of the angle from the satellite to the ground station once it is detected
    public static class SlewComputingHandler implements EventHandler {
        private final Frame inertialFrame;
        private final TopocentricFrame stationFrame;
        private final String station_name;
        private final List<SpacecraftState> list_spacecraftStates;  // <-- added

        public SlewComputingHandler(Frame inertialFrame,
                                    TopocentricFrame stationFrame,
                                    String station_name,
                                    List<SpacecraftState> list_spacecraftStates) {  // <-- added
            this.inertialFrame   = inertialFrame;
            this.stationFrame    = stationFrame;
            this.station_name    = station_name;
            this.list_spacecraftStates  = list_spacecraftStates;
        }

        @Override
        public Action eventOccurred(SpacecraftState s, EventDetector detector, boolean increasing) {
            if (!increasing) return Action.CONTINUE;

            // Look up the enriched state (which has "angle" attached) closest to this event date
            SpacecraftState currentstate = list_spacecraftStates.stream()
                    .min(Comparator.comparingDouble(st ->
                            Math.abs(st.getDate().durationFrom(s.getDate()))))
                    .orElse(s);  // fallback to raw state if list is empty

            Vector3D boresightInBody= (Vector3D) currentstate.getAdditionalData("angle");
            System.out.println(boresightInBody);
            Attitude attitude = currentstate.getAttitude();
            Vector3D boresightInertial = attitude.getRotation()
                    .applyInverseTo(boresightInBody);

            Vector3D satPosition = currentstate.getPosition(inertialFrame);

            Transform bodyToInertial = stationFrame.getParentShape()
                    .getBodyFrame()
                    .getTransformTo(inertialFrame, currentstate.getDate());

            Vector3D stationInertial = bodyToInertial.transformPosition(
                    stationFrame.getCartesianPoint());

            Vector3D toStation = stationInertial
                    .subtract(satPosition)
                    .normalize();

            double slewAngleRad = Vector3D.angle(boresightInertial, toStation);
            double slewAngleDeg = Math.toDegrees(slewAngleRad);

            Vector3D slewAxis = Vector3D.crossProduct(boresightInertial, toStation);
            if (slewAxis.getNorm() > 1e-10) {
                slewAxis = slewAxis.normalize();
            }

            System.out.println("Slew to " + station_name
                    + " | angle: " + slewAngleDeg + "° | axis: " + slewAxis);

            return Action.CONTINUE;
        }
    }
    public static class step_handler implements OrekitFixedStepHandler {
        private final Satellite p;
        private final String type_propa;
        public step_handler(String typepropa, Satellite p) {
            this.p = p;
            this.type_propa= typepropa;
        }

        public void handleStep(SpacecraftState currentState) {
            // add the needed information to SpacecraftState for EO observation
            SpacecraftState updated_currentState= currentState.addAdditionalData("angle",p.getBoresight()).addAdditionalData("name",p.get_Name());
            p.add_state(updated_currentState);

            boolean trigger = p.is_firing(updated_currentState);
            Frame itrf = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
            Vector3D pos =updated_currentState.getPVCoordinates(itrf).getPosition();
            //Vector3D pos =updated_currentState.getPVCoordinates().getPosition();
            //For each ground station visible during this propagation step, calculate the link budget between the GS and the sat
            if (Ground_station.satcom_activated){
                List<Ground_station.GroundStation_physical> list_GS_visible=Ground_station.get_list_visible_GS(updated_currentState);
                for (Ground_station.GroundStation_physical GS:list_GS_visible ){
                    System.out.println(GS.getName()+" - Budget link at time "+updated_currentState.getDate().toString() +" : "+ Satcom.calculate_budget_link(GS,p));
                }
            };
            Visulations.update_CSV_xyz_realsat(type_propa,p.get_Name(),pos,updated_currentState.getDate(),trigger,Ground_station.hasVisibleStations(updated_currentState,updated_currentState.getDate()), Optional.ofNullable(Ground_station.which_station_visible(updated_currentState, updated_currentState.getDate()))
                    .map(Ground_station.GroundStation_physical::getName)
                    .orElse(""));
            Visulations.update_csv_orbital_realsat(type_propa,p.get_Name(),updated_currentState.getOrbit(), updated_currentState.getDate(),trigger);
        }

    }
    public static class ZoneObservationContext {
        public Map<Integer, AbsoluteDate> pointEntryTimes = new HashMap<>();
        public Map<Integer, AbsoluteDate> pointExitTimes  = new HashMap<>();

        public ZoneObservationContext(int size) {
            // size no longer needed but kept for API compatibility
        }

        public void resetPass() {
            pointEntryTimes.clear();
            pointExitTimes.clear();
        }
    }

    public static class Area_revisit_Handler implements EventHandler {
        private final String name;
        private final int pointIndex;
        private final ZoneObservationContext context;
        private final List<SpacecraftState> list_spacecraftStates;
        private final Frame inertialFrame;

        public Area_revisit_Handler(String name, List<SpacecraftState> list_spacecraftStates,
                                    Frame inertialFrame, int pointIndex, ZoneObservationContext context) {
            this.name = name;
            this.pointIndex = pointIndex;
            this.context = context;
            this.list_spacecraftStates = list_spacecraftStates;
            this.inertialFrame = inertialFrame;
        }
        public SpacecraftState getfull_Spacecraftstate (SpacecraftState spacecraftState) {
            return list_spacecraftStates.stream()
                    .min(Comparator.comparingDouble(st ->
                            Math.abs(st.getDate().durationFrom(spacecraftState.getDate()))))
                    .orElse(spacecraftState);
        }
        @Override
        public Action eventOccurred(SpacecraftState s, EventDetector detector, boolean increasing) {
            if (increasing) {
                SpacecraftState nearest = getfull_Spacecraftstate(s);
                Vector3D boresightInBody = (Vector3D) nearest.getAdditionalData("angle");
                Attitude attitude = nearest.getAttitude();
                Vector3D boresightInertial = attitude.getRotation().applyInverseTo(boresightInBody);
                Vector3D satPosition = nearest.getPosition(inertialFrame);

                // Convert this point to inertial frame
                Transform itrfToInertial = Parametres.earth.getBodyFrame()
                        .getTransformTo(inertialFrame, nearest.getDate());
                GeodeticPoint point = EO_detection.Map_area_positions.get(name).get(pointIndex);
                Vector3D pointITRF = Parametres.earth.transform(point);
                Vector3D pointInertial = itrfToInertial.transformPosition(pointITRF);

                Vector3D toPoint = pointInertial.subtract(satPosition).normalize();
                double slewAngleRad = Vector3D.angle(boresightInertial, toPoint);

                if (slewAngleRad > Parametres.elevation) {
                    return Action.CONTINUE; // point not reachable, don't record entry
                }

                context.pointEntryTimes.put(pointIndex, s.getDate());

            } else {
                context.pointExitTimes.put(pointIndex, s.getDate());
                tryFinalizePass(s);
            }
            return Action.CONTINUE;
        }

        private void tryFinalizePass(SpacecraftState s) {
            int total = EO_detection.Map_area_positions.get(name).size();

            // Not all points have exited yet
            if (context.pointExitTimes.size() < total) return;
            // Not all points were ever visible this pass
            if (context.pointEntryTimes.size() < total) return;

            // Latest entry = when the LAST point became visible
            AbsoluteDate windowStart = context.pointEntryTimes.values().stream()
                    .max(Comparator.naturalOrder())
                    .orElseThrow();

            // Earliest exit = when the FIRST point leaves visibility
            AbsoluteDate windowEnd = context.pointExitTimes.values().stream()
                    .min(Comparator.naturalOrder())
                    .orElseThrow();

            double duration = windowEnd.durationFrom(windowStart);
            SpacecraftState nearest = getfull_Spacecraftstate(s);

            if (duration > 0) {
                Visulations.export_observation_to_csv(
                        name, windowStart, windowEnd, duration,
                        (String) nearest.getAdditionalData("name")
                );
            }

            context.resetPass();
        }
        }
    }

