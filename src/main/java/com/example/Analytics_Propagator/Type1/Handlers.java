package com.example.Analytics_Propagator.Type1;

import com.example.App;
import com.example.Ground_stations.EO_detection;
import com.example.Ground_stations.Ground_station;
import com.example.Ground_stations.Satcom;
import com.example.Orbiting_object.Satellite;
import com.example.Parametres;
import com.example.View.Visulations;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.events.Action;
import org.orekit.attitudes.Attitude;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.errors.OrekitException;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.TopocentricFrame;
import org.orekit.frames.Transform;
import org.orekit.geometry.fov.FieldOfView;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.*;
import org.orekit.propagation.events.handlers.EventHandler;
import org.orekit.propagation.sampling.OrekitFixedStepHandler;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.IERSConventions;

import javax.swing.text.StyledEditorKit;
import java.util.*;

public class Handlers {
    public static Map<String, Map<String,Integer>>Map_sat_visi_per_zone = new HashMap<>();
    public static Map<String, Map<String, Map<Integer,AbsoluteDate>>> Map_sat_area_entryDate = new HashMap<>();
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

            Vector3D boresightInBody= (Vector3D) currentstate.getAdditionalData("Boresight");
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
            SpacecraftState updated_currentState= currentState.addAdditionalData("Boresight",p.getBoresight()).addAdditionalData("name",p.get_Name()).addAdditionalData("agility",p.getAgility());
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

    public static EventDetector buildAreaRevisitDetector(
            TopocentricFrame tcf,
            FieldOfView fov,
            double maxCheckingInterval,
            String name,
            int pointIndex,
            Satellite sat,
            Frame inertialFrame) {

        final FieldOfViewDetector fd = new FieldOfViewDetector(tcf, fov);
        final ElevationDetector ed = new ElevationDetector(tcf)
                .withConstantElevation(Parametres.elevation);

        return BooleanDetector.andCombine(ed, BooleanDetector.notCombine(fd))
                .withMaxCheck(maxCheckingInterval)
                .withHandler(new Area_revisit_Handler(
                        name,
                        sat,
                        inertialFrame,
                        pointIndex
                ));
    }
    public static class Area_revisit_Handler implements EventHandler {

        private final String name;
        private final Satellite sat;
        private final Frame inertialFrame;
        private final int pointIndex;
        private final List<SpacecraftState> states;

        public Area_revisit_Handler(
                String name,
                Satellite sat,
                Frame inertialFrame,int pointIndex
) {
            this.name = name;
            this.sat = sat;
            this.inertialFrame = inertialFrame;
            this.pointIndex = pointIndex;
            states=sat.get_liste_state_propa();

            // Initialize visibility counter
            Map_sat_visi_per_zone
                    .computeIfAbsent(sat.get_Name(), k -> new HashMap<>())
                    .put(name, 0);

            // Initialize entry date map
            Map_sat_area_entryDate
                    .computeIfAbsent(sat.get_Name(), k -> new HashMap<>())
                    .computeIfAbsent(name, k -> new HashMap<>());
        }

        private int getTotalPoints() {
            return 1;
            //return EO_detection.Map_area_positions.get(name).size();
        }

        @Override

        public Action eventOccurred(
                SpacecraftState s,
                EventDetector detector,
                boolean increasing) {

            AbsoluteDate date = s.getDate();
            int total = getTotalPoints();

            if (increasing) {
                if (!sat.isCurrently_observing().equals(name) && sat.isCurrently_observing() != null) {
                    return Action.CONTINUE;
                }
                sat.setCurrently_observing(name);
                // Store this point's entry date
                Map_sat_area_entryDate.get(sat.get_Name()).get(name).put(pointIndex, date);

                // Increment visible count
                int count = Map_sat_visi_per_zone.get(sat.get_Name()).getOrDefault(name, 0) + 1;
                Map_sat_visi_per_zone.get(sat.get_Name()).put(name, count);

            } else {
                int count = Map_sat_visi_per_zone.get(sat.get_Name()).getOrDefault(name, 0);

                if (count == total) {
                    // All points were visible — valid access, record it
                    AbsoluteDate startDate = Map_sat_area_entryDate.get(sat.get_Name()).get(name)
                            .values().stream()
                            .max(Comparator.comparingDouble(d -> d.durationFrom(Parametres.date_orekit)))
                            .orElse(null);

                    if (startDate != null) {
                        double duration = date.durationFrom(startDate);
                        Visulations.export_observation_to_csv(
                                name, startDate, date, duration, sat.get_Name());
                    }
                }

                // Always decrement and clean up on any exit
                Map_sat_visi_per_zone.get(sat.get_Name()).put(name, count - 1);

                // Reset fully when last point exits
                if (count - 1 == 0) {
                    Map_sat_area_entryDate.get(sat.get_Name()).get(name).clear();
                    sat.setCurrently_observing(null);
                    Map_sat_visi_per_zone.get(sat.get_Name()).put(name, 0);
                }
            }

            return Action.CONTINUE;
        }
    }
}

