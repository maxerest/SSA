package com.example.ISL;

import com.example.Analytics_Propagator.Type1.Handlers;
import com.example.App;
import com.example.Ground_stations.Satcom;
import com.example.Orbiting_object.Satellite;
import com.example.Orbiting_object.Satellite_sub_systems.ISL_antenna;
import com.example.Parametres;
import com.example.View.Visulations;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.BooleanDetector;
import org.orekit.propagation.events.InterSatDirectViewDetector;
import org.orekit.propagation.events.RelativeDistanceDetector;
import org.orekit.time.AbsoluteDate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Intersatellite_links {
    public static boolean ISL_activated=false;
    public static final Map<Integer, ISL_data> ISL_DATA = new LinkedHashMap<>();
    private static int nextLinkId = 0;
    private static final int MAX_ISL = 4;
    private static final Map<String, Integer> activeLinks = new LinkedHashMap<>();
    private static List<Handlers.IntersatelliteLinksHandler> handlers = new ArrayList<>();

    private static void Detector_2_sats(Satellite satellite1, Satellite satellite2, List<Handlers.IntersatelliteLinksHandler> handlers) {

        double maxDistanceM = 2_000_000.0;
        Handlers.IntersatelliteLinksHandler linkHandler = new Handlers.IntersatelliteLinksHandler(satellite1, satellite2);

        handlers.add(linkHandler);

        InterSatDirectViewDetector losDetector =
                new InterSatDirectViewDetector(
                        (OneAxisEllipsoid) Parametres.earth,
                        satellite2.getEphemeris()
                )
                        .withMaxCheck(30.0)
                        .withThreshold(0.1);


        RelativeDistanceDetector distanceDetector =
                new RelativeDistanceDetector(
                        satellite2.getEphemeris(),
                        maxDistanceM
                )
                        .withMaxCheck(30.0)
                        .withThreshold(0.1);


        BooleanDetector linkDetector =
                BooleanDetector.andCombine(
                                losDetector,
                                BooleanDetector.notCombine(
                                        distanceDetector
                                )
                        )
                        .withHandler(linkHandler);

        satellite1.getEphemeris().addEventDetector(linkDetector);
    }



    public static void ISL_initialization() {

        if (App.liste_par_sats_real_orbit.size() < 2) {
            return;
        }

        Satellite sat1 = App.liste_par_sats_real_orbit.getFirst();


        /*
         * Build possible ISL detectors.
         */
        for (int i = 1;
             i < App.liste_par_sats_real_orbit.size();
             i++) {

            Satellite sat2 =
                    App.liste_par_sats_real_orbit.get(i);

            Detector_2_sats(
                    sat1,
                    sat2,
                    handlers
            );
        }


        /*
         * ONLY ONE callback every second.
         *
         * This callback decides which four links
         * are actually active.
         */
        sat1.getEphemeris()
                .getMultiplexer()
                .add(
                        1.0,
                        state -> updateClosestLinks(
                                sat1,
                                state,
                                handlers
                        )
                );


        sat1.getEphemeris().propagate(
                sat1.getEphemeris().getMinDate(),
                sat1.getEphemeris().getMaxDate()
        );


        /*
         * Close anything still active at simulation end.
         */
        AbsoluteDate finalDate =
                sat1.getEphemeris().getMaxDate();

        for (String target :
                new ArrayList<>(activeLinks.keySet())) {

            closeLink(
                    sat1,
                    target,
                    finalDate
            );
        }
        System.out.println("[Java] Intersatellite links done");
    }

    public static class ISL_data {

        private final String name_target;
        private final Map<AbsoluteDate, Double> distances = new LinkedHashMap<>();
        private AbsoluteDate startdate;
        private AbsoluteDate enddate;
        public ISL_data(String name_target) {
            this.name_target = name_target;
        }

        public void addDistance(
                AbsoluteDate date,
                double distance) {

            distances.put(date, distance);
        }

        public String getName_target() {
            return name_target;
        }

        public Map<AbsoluteDate, Double> getDistances() {
            return distances;
        }

        public AbsoluteDate getStartdate() {
            return startdate;
        }

        public AbsoluteDate getEnddate() {
            return enddate;
        }
        public void setStartdate(AbsoluteDate startdate) {
            this.startdate = startdate;
        }

        public void setEnddate(AbsoluteDate enddate) {
            this.enddate = enddate;
        }
    }
    private static double calculateDataRate(Satellite sat, ISL_data data){
        double c = 299_792_458.0;
        ISL_antenna antenna = sat.getISL_antenna();
        double bandwidth = antenna.getBandwidth()*1e6;
        double fpls = Satcom.free_path_loss_calculation(antenna.getFrequency(),average_distance(data));
        double receivedPowerDbm =antenna.getTxPowerDbm()+antenna.getGain()+antenna.getGain()-fpls-2; // 2 is for lossesDb not sure about the value
        double noisePowerDbm = -174.0 + 10.0 * Math.log10(bandwidth) + antenna.getNoiseFigure();
        double snrDb = receivedPowerDbm - noisePowerDbm;

        double snrLinear = Math.pow(10.0, snrDb / 10.0);

        // bits/s
        return  antenna.getEfficiency()
                * bandwidth
                * (Math.log(1.0 + snrLinear) / Math.log(2.0));


    }
    private static double average_distance(ISL_data data1) {
        return  data1.getDistances().values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
    public static double calculateLink(Satellite sourceSat, int linkId) {

        ISL_data data = ISL_DATA.get(linkId);

        if (data == null || data.getDistances().isEmpty()) {
            return 0.0;
        }

        double averageDistance = average_distance(data);

        double dataRate = calculateDataRate(sourceSat, data);

        double duration = data.enddate.durationFrom(data.startdate);

        // dataRate assumed to be bits/s
        // result returned in GB
        return dataRate * duration / 8.0 / 1e9;
    }

    private static class ISLCandidate {

        Satellite satellite;
        Handlers.IntersatelliteLinksHandler handler;
        double distance;

        ISLCandidate(
                Satellite satellite,
                Handlers.IntersatelliteLinksHandler handler,
                double distance) {

            this.satellite = satellite;
            this.handler = handler;
            this.distance = distance;
        }
    }
    private static void updateClosestLinks(
            Satellite sourceSat,
            SpacecraftState sourceState,
            List<Handlers.IntersatelliteLinksHandler> handlers) {

        AbsoluteDate date = sourceState.getDate();
        Frame frame = sourceState.getFrame();

        Vector3D sourcePosition =
                sourceState.getPosition(frame);

        List<ISLCandidate> candidates =
                new java.util.ArrayList<>();


        /*
         * Find every satellite which currently satisfies
         * LOS + maximum distance conditions.
         */
        for (Handlers.IntersatelliteLinksHandler handler : handlers) {

            if (!handler.isAvailable()) {
                continue;
            }

            Satellite target =
                    handler.getTargetSatellite();

            Vector3D targetPosition =
                    target.getEphemeris()
                            .getPosition(date, frame);

            double distance =
                    Vector3D.distance(
                            sourcePosition,
                            targetPosition
                    );

            candidates.add(
                    new ISLCandidate(
                            target,
                            handler,
                            distance
                    )
            );
        }


        /*
         * Closest first.
         */
        candidates.sort(java.util.Comparator.comparingDouble(candidate -> candidate.distance));

        /*
         * Keep maximum 4.
         */
        List<ISLCandidate> selected = candidates.stream().limit(MAX_ISL).toList();
        java.util.Set<String> selectedNames = selected.stream().map(c -> c.satellite.get_Name()).collect(java.util.stream.Collectors.toSet());


        /*
         * Close links which are no longer among the 4 closest.
         */
        for (String targetName :
                new java.util.ArrayList<>(activeLinks.keySet())) {

            if (!selectedNames.contains(targetName)) {
                closeLink(sourceSat, targetName, date);
            }
        }


        /*
         * Open new links / record distances.
         */
        for (ISLCandidate candidate : selected) {

            String targetName = candidate.satellite.get_Name();

            Integer linkId = activeLinks.get(targetName);


            /*
             * New ISL
             */
            if (linkId == null) {
                linkId = openLink(sourceSat, candidate.satellite, date);
            }


            /*
             * Add current distance sample.
             */
            ISL_data data = ISL_DATA.get(linkId);

            if (data != null) {data.addDistance(date, candidate.distance);}
        }
    }
    private static int openLink(
            Satellite sourceSat,
            Satellite targetSat,
            AbsoluteDate date) {

        int linkId = ++nextLinkId;

        ISL_data data =
                new ISL_data(
                        targetSat.get_Name()
                );

        data.setStartdate(date);

        ISL_DATA.put(
                linkId,
                data
        );

        activeLinks.put(
                targetSat.get_Name(),
                linkId
        );

        System.out.println(
                "[ISL] OPEN "
                        + sourceSat.get_Name()
                        + " -> "
                        + targetSat.get_Name()
        );

        return linkId;
    }
    private static void closeLink(
            Satellite sourceSat,
            String targetName,
            AbsoluteDate date) {

        Integer linkId =
                activeLinks.remove(targetName);

        if (linkId == null) {
            return;
        }

        ISL_data data =
                ISL_DATA.get(linkId);

        if (data == null) {
            return;
        }

        data.setEnddate(date);

        double duration =
                date.durationFrom(
                        data.getStartdate()
                );

        double transmittedData =
                calculateLink(
                        sourceSat,
                        linkId
                );

        Visulations.export_ISL_to_csv(
                sourceSat.get_Name(),
                targetName,
                data.getStartdate(),
                date,
                duration,
                transmittedData
        );

        System.out.println(
                "[ISL] CLOSE "
                        + sourceSat.get_Name()
                        + " -> "
                        + targetName
        );
    }
}
