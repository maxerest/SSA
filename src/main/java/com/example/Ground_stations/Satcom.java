package com.example.Ground_stations;

import com.example.Orbiting_object.Satellite;
import org.orekit.propagation.SpacecraftState;

public  class Satcom {
    // === CONSTANTS ===
    private static final double BOLTZMANN = 1.38064852e-23; // J/K
    private static final double C = 299792458.0; // m/s
    private static final double EARTH_RADIUS = 6371000.0; // meters

    public static double calculate_budget_link(Ground_station.GroundStation_physical GS, Satellite S){
        String name = "Antenna 1";



      /*  // Calculate atmospheric losses
        double rainAttenuationDb = calculateRainAttenuation(frequencyGhz, elevationAngleDeg, rainRatePercentile);
        double gaseousAttenuationDb = calculateGaseousAbsorption(frequencyGhz, elevationAngleDeg);*/

        // Miscellaneous losses (polarization, coupling, etc.)
        double miscLossesDb = 1.0;

        //double totalLossesDb = pathLossDb + rainAttenuationDb + gaseousAttenuationDb + miscLossesDb;
        //Calculate losses
        double pathLossDb = free_path_loss_calculation(GS,S);
        double totalLossesDb = pathLossDb  + miscLossesDb;
        //Calculate recevied power
        double eirpDbm = calculateEIRP(S.getMap_parametres_antennes().get(name));
        double receivedPowerDbm = eirpDbm - totalLossesDb + GS.getAntenna_gain();

        // Noise power (separate calculation, in dBm)
        double noisePowerDbm = Noise_power_calculation(GS);

        // SNR (signal minus noise)
        return calculateSNR(GS,receivedPowerDbm);

    }
    /**
     * Calculate EIRP
     */
    private static double calculateEIRP(Satellite.AntennaParameters antenna) {
        return antenna.getTxPowerDbm() + antenna.getGain();
    }

    private static double free_path_loss_calculation (Ground_station.GroundStation_physical GS, Satellite S){
            //Name of the antenna to be used for the calculation, might be interesting to be able to change it in the futur.
            String name = "Antenna 1";

            //Distance between the GS and the satellite
            SpacecraftState Sc_state=S.get_liste_state_propa().getLast();
            double distance = GS.getBaseFrame().getPosition(Sc_state.getDate(),GS.getBaseFrame()).distance(Sc_state.getPosition());
            double frequencyHz = S.getMap_parametres_antennes().get(name).getFrequency() * 1e9;
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
        double noisePowerDbm = 10.0 * Math.log10(noisePowerW * 1000.0);
        return noisePowerDbm;
    }
    private static double calculateSNR(Ground_station.GroundStation_physical GS,double  receivedPowerDbm) {
        double noisePowerDbm = Noise_power_calculation(GS);
        return receivedPowerDbm - noisePowerDbm;
    }
}
