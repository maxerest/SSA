package com.example.Analytics_Propagator.Type1;

import com.example.Ground_stations.EO_detection;
import com.example.Ground_stations.Ground_station;
import com.example.Ground_stations.Satcom;
import com.example.Orbiting_object.Satellite;
import com.example.Parametres;
import com.example.View.Visulations;
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
            SpacecraftState updated_currentState= currentState.addAdditionalData("angle",p.getBoresight());
            boolean trigger = p.is_firing(updated_currentState);
            Frame itrf = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
            //Vector3D pos =updated_currentState.getPVCoordinates(itrf).getPosition();
            Vector3D pos =updated_currentState.getPVCoordinates().getPosition();
            p.add_state(updated_currentState);
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
        public boolean currently_observing = false;
        public AbsoluteDate start_date_observation = null;
        public int currentPointIndex = 0;
    }

    public static class Area_revisit_Handler implements EventHandler{
        private String name;
        private final Frame inertialFrame;
        private final List<SpacecraftState> list_spacecraftStates;
        private  AbsoluteDate start_date_observation;
        private int point_number_in_zone;
        private final ZoneObservationContext context;

        public Area_revisit_Handler(String name, List<SpacecraftState> list_spacecraftStates, Frame inertialFrame, int number_in_zone, ZoneObservationContext context) {
            this.name=name;
            this.list_spacecraftStates = list_spacecraftStates;
            this.inertialFrame = inertialFrame;
            this.point_number_in_zone=number_in_zone;
            this.context = context;
        }
        @Override
        public Action eventOccurred(SpacecraftState s, EventDetector detector, boolean increasing) {
            if (increasing && !context.currently_observing) {
                return this.increasing(s);
            } else if (!increasing && context.currently_observing) {
                return this.decreasing(s);
            }
            return Action.CONTINUE;
        }
        private Action increasing (SpacecraftState s) {
            SpacecraftState currentstate = list_spacecraftStates.stream()
                    .min(Comparator.comparingDouble(st ->
                            Math.abs(st.getDate().durationFrom(s.getDate()))))
                    .orElse(s);  // fallback to raw state if list is empty

            Vector3D boresightInBody= (Vector3D) currentstate.getAdditionalData("angle");
            Attitude attitude = currentstate.getAttitude();
            Vector3D boresightInertial = attitude.getRotation()
                    .applyInverseTo(boresightInBody);

            Vector3D satPosition = currentstate.getPosition(inertialFrame);

            // Convert Earth body frame → inertial once for all points
            Transform bodyToInertial = Parametres.earth.getBodyFrame()
                    .getTransformTo(inertialFrame, currentstate.getDate());
            for (GeodeticPoint point : EO_detection.Map_area_positions.get(name)) {
                // Convert geodetic point to inertial frame
                Frame itrf = Parametres.earth.getBodyFrame(); // this IS your ITRF

                Transform itrfToInertial = itrf.getTransformTo(inertialFrame, currentstate.getDate());

                Vector3D pointITRF = Parametres.earth.transform(point); // geodetic → Cartesian in ITRF
                Vector3D pointInertial = itrfToInertial.transformPosition(pointITRF);   // geodetic → Cartesian ECEF → inertial

                Vector3D toPoint = pointInertial
                        .subtract(satPosition)
                        .normalize();

                double slewAngleRad = Vector3D.angle(boresightInertial, toPoint);
                double slewAngleDeg = Math.toDegrees(slewAngleRad);

                if (slewAngleRad > Math.toRadians(60)) {
                    return  Action.CONTINUE;
                }
            }
            context.currently_observing = true;
            EO_detection.Map_area_history.get(name).get(point_number_in_zone).put(s.getDate(), null);
            context.start_date_observation=s.getDate();
            return  Action.CONTINUE;
        }
        private Action decreasing(SpacecraftState s) {

            if (context.start_date_observation == null) return Action.CONTINUE;
            List<TreeMap<AbsoluteDate, AbsoluteDate>> pointHistories = EO_detection.Map_area_history.get(name);
            pointHistories.get(context.currentPointIndex).put(context.start_date_observation, s.getDate());
            context.currentPointIndex++;

            boolean allComplete = pointHistories.stream()
                    .allMatch(ph -> !ph.isEmpty() && ph.get(ph.lastKey()) != null);

            if (allComplete) {
                double minDuration = pointHistories.stream()
                        .mapToDouble(ph -> ph.get(ph.lastKey()).durationFrom(ph.lastKey()))
                        .min()
                        .orElse(0.0);
                Visulations.export_observation_to_csv(name, context.start_date_observation, s.getDate(), minDuration);
                context.currentPointIndex = 0;
                context.currently_observing = false;
                context.start_date_observation = null;
            }
            return Action.CONTINUE;
        }
        }
    }

