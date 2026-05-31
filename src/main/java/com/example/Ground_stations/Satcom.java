package com.example.Ground_stations;

import com.example.Analytics_Propagator.Type1.Handlers;
import com.example.Orbiting_object.Satellite;
import com.example.Orbiting_object.Satellite_sub_systems.Antenna;
import com.example.Orbiting_object.Satellite_sub_systems.MODCOD;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.ElevationDetector;
import org.orekit.propagation.events.EventDetector;
import org.orekit.propagation.numerical.NumericalPropagator;

public class Satcom {
    public static boolean activated_satcom;
    // === CONSTANTS ===
    private static final double BOLTZMANN = 1.38064852e-23; // J/K
    private static final double C = 299792458.0; // m/s
    private static final double EARTH_RADIUS = 6371000.0; // meters

    public static double calculate_budget_link(Ground_station.GroundStation_physical GS, Satellite S,Antenna antenna){
      /*  // Calculate atmospheric losses
        double rainAttenuationDb = calculateRainAttenuation(frequencyGhz, elevationAngleDeg, rainRatePercentile);
        double gaseousAttenuationDb = calculateGaseousAbsorption(frequencyGhz, elevationAngleDeg);*/

        // Miscellaneous losses (polarization, coupling, etc.)
        double miscLossesDb = 1.0;
        // Perte de free path
        double pathLossDb = free_path_loss_calculation(GS,S,antenna);

        //double totalLossesDb = pathLossDb + rainAttenuationDb + gaseousAttenuationDb + miscLossesDb;
        //to delete when it is done
        double totalLossesDb = pathLossDb  + miscLossesDb;
        //Calculate recevied power
        double eirpDbm = calculateEIRP(antenna);
        double receivedPowerDbm = eirpDbm - totalLossesDb + GS.getAntenna_gain();

        // Noise power (separate calculation, in dBm)
        //double noisePowerDbm = Noise_power_calculation(GS);
        // SNR (signal minus noise)
        return calculateSNR(GS,receivedPowerDbm);

    }
    /**
     * Calculate EIRP
     */
    private static double calculateEIRP(Antenna antenna) {
        return antenna.getTxPowerDbm() + antenna.getGain();
    }

    private static double free_path_loss_calculation (Ground_station.GroundStation_physical GS, Satellite S,Antenna antenna){
            SpacecraftState Sc_state=S.get_liste_state_propa().getLast();
            double distance = GS.getBaseFrame().getPosition(Sc_state.getDate(),GS.getBaseFrame()).distance(Sc_state.getPosition());
            double frequencyHz = antenna.getFrequency() * 1e9;
            return 20.0 * Math.log10(4.0 * Math.PI * distance / (C / frequencyHz));
        }

    private static double Noise_power_calculation (Ground_station.GroundStation_physical GS){
        // TODO get dynamic access to temperature at the given GS location
        double temperatureK = 290;

        // === RECEIVER PARAMETERS ===
        double noiseFigureDb = GS.getNoiseFigureDb();
        double noiseBandwidthMhz = GS.getNoiseBandwidthMhz();
        double bandwidthHz = noiseBandwidthMhz * 1e6;
        double noiseFactor = Math.pow(10.0, noiseFigureDb / 10.0);
        double noisePowerW = BOLTZMANN * temperatureK * bandwidthHz * noiseFactor;
        return 10.0 * Math.log10(noisePowerW * 1000.0);
    }
    private static double calculateSNR(Ground_station.GroundStation_physical GS,double  receivedPowerDbm) {
        double noisePowerDbm = Noise_power_calculation(GS);
        return receivedPowerDbm - noisePowerDbm;
    }

    private static double depointing_loss(double depointing,double teta3dB){
        return 12*(depointing/teta3dB);
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

    public static double calculateDataRate_Mbps(double snr_dB, double bandwidth_MHz) {
        // Get satellite's fixed MODCOD
        //MODCOD.modcod modcod = sat.getMODCOD();
        //get best modcod
        MODCOD.modcod modcod = MODCOD.MODCODLibrary.getBestMODCOD(snr_dB);

        if (modcod == null) {
            return 0;
        }

        // Roll-off factor (typical for satellite systems)
        double rollOffFactor = 0.25;

        // Symbol rate = Bandwidth / (1 + roll-off)
        double symbolRate_MHz = bandwidth_MHz / (1.0 + rollOffFactor);

        // Data Rate = Symbol Rate × Bits per Symbol × Code Rate

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
