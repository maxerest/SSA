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
import org.orekit.propagation.events.BooleanDetector;
import org.orekit.propagation.events.InterSatDirectViewDetector;
import org.orekit.propagation.events.RelativeDistanceDetector;
import org.orekit.time.AbsoluteDate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Intersatellite_links {
    public static boolean ISL_activated=false;
    public static final Map<Integer, ISL_data> ISL_DATA = new LinkedHashMap<>();


    public static void Detector_2_sats(Satellite satellite1, Satellite satellite2) {
        double maxDistanceM = 2_000_000.0; // example
        Handlers.IntersatelliteLinksHandler linkHandler =new Handlers.IntersatelliteLinksHandler(satellite1,satellite2);
        InterSatDirectViewDetector losDetector =
                new InterSatDirectViewDetector(
                        (OneAxisEllipsoid) Parametres.earth,
                        satellite2.getEphemeris()
                )
                        .withMaxCheck(30.0)
                        .withThreshold(0.1);

        RelativeDistanceDetector distanceDetector =
                new RelativeDistanceDetector(satellite2.getEphemeris(),maxDistanceM)
                        .withMaxCheck(30.0)
                        .withThreshold(0.1);
        BooleanDetector linkDetector = BooleanDetector.andCombine(losDetector, BooleanDetector.notCombine(distanceDetector))
                        .withHandler(linkHandler);

        satellite1.getEphemeris().addEventDetector(linkDetector);
        // Distance calculation every 1 second
        double step = 1.0;

        satellite1.getEphemeris()
                .getMultiplexer()
                .add(step, state1 -> {

                    if (!linkHandler.isLinkActive()) {
                        return;
                    }

                    Frame frame = state1.getFrame();

                    Vector3D p1 = state1.getPosition(frame);

                    Vector3D p2 = satellite2.getEphemeris().getPosition(state1.getDate(), frame);

                    double distanceM = Vector3D.distance(p1, p2);
                    int linkId = linkHandler.getCurrentLinkId();
                    ISL_data data = ISL_DATA.computeIfAbsent(linkId, id -> new ISL_data(satellite2.get_Name()));

                    data.addDistance(state1.getDate(),distanceM);

                });
    }



    public static void ISL_initialization(){
        Satellite sat1 = App.liste_par_sats_real_orbit.getFirst();
        if (App.liste_par_sats_real_orbit.size() < 2) {
            return;
        }
        for (int i = 1; i < App.liste_par_sats_real_orbit.size(); i++) {
                Satellite sat2 = App.liste_par_sats_real_orbit.get(i);
                Detector_2_sats(sat1, sat2);
        }

        for (Satellite sat : App.liste_par_sats_real_orbit) {
            boolean hasISLDetector = sat.getEphemeris().getEventDetectors().stream()
                    .anyMatch(detector ->
                            detector.getHandler() instanceof Handlers.IntersatelliteLinksHandler
                    );

            if (hasISLDetector) {
                sat.getEphemeris().propagate(
                        sat.getEphemeris().getMinDate(),
                        sat.getEphemeris().getMaxDate()
                );
            }
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


}
