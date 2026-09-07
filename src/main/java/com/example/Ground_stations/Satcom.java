package com.example.Ground_stations;

import com.example.Analytics_Propagator.Type1.Handlers;
import com.example.Orbiting_object.Satellite;
import com.example.Orbiting_object.Satellite_sub_systems.Antenna;
import com.example.Orbiting_object.Satellite_sub_systems.MODCOD;
import org.hipparchus.util.FastMath;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.ElevationDetector;
import org.orekit.propagation.events.EventDetector;
import org.orekit.propagation.numerical.NumericalPropagator;

import java.util.List;


public class Satcom {
    public static boolean activated_satcom;
    // === CONSTANTS ===
    private static final double BOLTZMANN = 1.38064852e-23; // J/K
    private static final double C = 299792458.0; // m/s
    private static final double EARTH_RADIUS = 6371000; // meters
    public record RainCoeff(
            double minFreqGHz,
            double maxFreqGHz,
            double k,
            double alpha) {
    }
    private static final List<RainCoeff> COEFFS = List.of(
            new RainCoeff( 0.0,  8.0, 0.0001, 1.00), // C-band
            new RainCoeff( 8.0, 12.0, 0.0050, 1.15), // X-band
            new RainCoeff(12.0, 18.0, 0.0180, 1.22), // Ku-band
            new RainCoeff(18.0, 30.0, 0.0750, 1.12), // Ka-band
            new RainCoeff(30.0, 75.0, 0.2200, 1.05)  // V-band
    );
    public static double calculate_budget_link(
            Ground_station.GroundStation_physical GS,
            Satellite S,
            Antenna antenna) {

        SpacecraftState state =
                S.get_liste_state_propa().getLast();

        // Correct GS -> satellite slant range
        double distance =
                state.getPVCoordinates(GS.getBaseFrame())
                        .getPosition()
                        .getNorm();

        double pathLossDb =
                free_path_loss_calculation(distance, antenna);

        double miscLossesDb = 1.0;

        double pointingLossDb =
                depointing_loss(
                        FastMath.toRadians(0.01),
                        FastMath.toRadians(antenna.getteta3dB())
                );

        double rainLossDb = 0.0;

        double elevationRad =
                GS.getBaseFrame()
                        .getTrackingCoordinates(
                                state.getPosition(),
                                state.getFrame(),
                                state.getDate())
                        .getElevation();

        if (GS.israining() && elevationRad > 0) {

            rainLossDb =
                    rain_loss(
                            antenna.getFrequency(),
                            GS.getRain_rate(),
                            elevationRad
                    );
        }

        double totalLossesDb =
                pathLossDb
                        + miscLossesDb
                        + pointingLossDb
                        + rainLossDb;

        double eirpDbm =
                calculateEIRP(antenna);

        double eirpDbw =
                eirpDbm - 30.0;

        double bandwidthHz =
                antenna.getBandwidth() * 1e6;

        double bandwidthDb =
                10.0 * Math.log10(bandwidthHz);

        double gt =
                GS.get_system_GT();

        double cn0 =
                eirpDbw
                        - totalLossesDb
                        + gt
                        + 228.6;

        double snr =
                cn0
                        - bandwidthDb;

        System.out.println("======= LINK BUDGET =======");
        System.out.printf("Distance       : %.2f km%n",
                distance / 1000.0);
        System.out.printf("Elevation      : %.2f deg%n",
                FastMath.toDegrees(elevationRad));

        System.out.printf("TX power       : %.2f dBm%n",
                antenna.getTxPowerDbm());
        System.out.printf("TX gain        : %.2f dBi%n",
                antenna.getGain());
        System.out.printf("EIRP           : %.2f dBW%n",
                eirpDbw);

        System.out.printf("FSPL           : %.2f dB%n",
                pathLossDb);
        System.out.printf("Pointing loss  : %.2f dB%n",
                pointingLossDb);
        System.out.printf("Rain loss      : %.2f dB%n",
                rainLossDb);
        System.out.printf("Misc loss      : %.2f dB%n",
                miscLossesDb);

        System.out.printf("G/T            : %.2f dB/K%n",
                gt);

        System.out.printf("Bandwidth      : %.2f MHz%n",
                antenna.getBandwidth());
        System.out.printf("Bandwidth term : %.2f dB%n",
                bandwidthDb);

        System.out.printf("C/N0           : %.2f dB-Hz%n",
                cn0);
        System.out.printf("C/N            : %.2f dB%n",
                snr);

        System.out.println("===========================");

        return snr;
    }

    private static double rain_loss(double antenna_frequency,double rain_rate,double angle) {
        RainCoeff coeff = getCoeff(antenna_frequency);
        double distance =5; // KM where rain is applied
        double pathLengthKm =
                distance /
                        Math.sin(angle);
        return coeff.k()
                * Math.pow(rain_rate, coeff.alpha())*pathLengthKm;
    }

    public static RainCoeff getCoeff(double frequencyGHz) {

        return COEFFS.stream()
                .filter(c -> frequencyGHz >= c.minFreqGHz()
                        && frequencyGHz < c.maxFreqGHz())
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unsupported frequency: " + frequencyGHz + " GHz"));
    }
    /**
     * Calculate EIRP
     */
    private static double calculateEIRP(Antenna antenna) {
        return antenna.getTxPowerDbm() + antenna.getGain();
    }

    private static double free_path_loss_calculation (double distance,Antenna antenna){
            double frequencyHz = antenna.getFrequency() * 1e9;
            return 20.0 * Math.log10(4.0 * Math.PI * distance / (C / frequencyHz));
        }

    private static double calculateSNRFromGT(
            Ground_station.GroundStation_physical GS,
            double eirpDbm,
            double totalLossesDb) {

        double gtDbPerK = GS.get_system_GT();

        double bandwidthHz =
                GS.getNoiseBandwidthMhz() * 1e6;

        // Convert EIRP dBm -> dBW
        double eirpDbw = eirpDbm - 30.0;

        // Boltzmann constant in dBW/K/Hz
        // approximately -228.60 dBW/K/Hz
        double boltzmannDb =
                10.0 * Math.log10(BOLTZMANN);

        // C/N0 in dB-Hz
        double cn0DbHz =
                eirpDbw
                        - totalLossesDb
                        + gtDbPerK
                        - boltzmannDb;

        // C/N over receiver bandwidth
        return cn0DbHz
                - 10.0 * Math.log10(bandwidthHz);
    }

    private static double depointing_loss(double depointing,double teta3dB){
        return 12*Math.pow(depointing/teta3dB,2);
    }



    public static void satcom_station_link(NumericalPropagator propagator, Satellite sat){
        final double maxcheck  = 60.0;
        final double threshold =  0.001;
        for (Ground_station.GroundStation_physical GS : Ground_station.liste_GS  ){
            final EventDetector station_visibility =
                    new ElevationDetector(maxcheck, threshold, GS.getBaseFrame())
                            .withConstantElevation(GS.elevation_mask)
                            .withHandler(new Handlers.satcom_handler(sat, GS.name));
            propagator.addEventDetector(station_visibility);
        }
    }

    public static double calculateDataRate_Mbps(
            double snr_dB,
            double bandwidth_MHz) {

        double rollOffFactor = 0.25;

        MODCOD.modcod modcod =
                MODCOD.MODCODLibrary.getBestMODCOD(
                        snr_dB,
                        rollOffFactor
                );

        if (modcod == null) {
            return 0.0;
        }

        double symbolRate_MHz =
                bandwidth_MHz / (1.0 + rollOffFactor);

        return symbolRate_MHz
                * modcod.getBitsPerSymbol()
                * modcod.getCodeRate();
    }
    public static double calculateTransmittableData_MB(Satellite sat, double duration_sec,String GS_name) {
        // Find the ground station object
        Ground_station.GroundStation_physical GS = Ground_station.liste_GS.stream()
                .filter(g -> g.getName() != null && g.getName().equals(GS_name))
                .findFirst()
                .orElse(null);

        try {

            // Get bandwidth from antenna parameters
            Antenna antenna = sat.getMap_parametres_antennes()
                    .values()
                    .stream()
                    .findFirst()
                    .orElse(null);
            double bandwidth_MHz = antenna != null ? antenna.getBandwidth() : 50.0;
            GS.setNoiseBandwidthMhz(bandwidth_MHz);

            // Calculate SNR from link budget
            double snr_dB = Satcom.calculate_budget_link(GS, sat,antenna);
            System.out.println(snr_dB);

            // Calculate data rate based on SNR and MODCOD
            double dataRate_Mbps = Satcom.calculateDataRate_Mbps(snr_dB, bandwidth_MHz);

            // Calculate transmittable data: (Data Rate in Mbps × Duration in sec) / 8
            return (dataRate_Mbps * duration_sec) / 8.0;

        } catch (Exception e) {
            System.err.println("Error calculating transmittable data: " + e.getMessage());
            return 0;
        }
    }
}
