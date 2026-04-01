package com.example.Ground_stations;

import com.example.Orbiting_object.Satellite;
import org.orekit.propagation.SpacecraftState;

import java.util.Map;
import java.util.Set;

public class Satcom {
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
        // Perte de free path
        double pathLossDb = free_path_loss_calculation(GS,S);

        //double totalLossesDb = pathLossDb + rainAttenuationDb + gaseousAttenuationDb + miscLossesDb;
        //to delete when it is done
        double totalLossesDb = pathLossDb  + miscLossesDb;
        //Calculate recevied power
        double eirpDbm = calculateEIRP(S.getMap_parametres_antennes().get(name));
        double receivedPowerDbm = eirpDbm - totalLossesDb + GS.getAntenna_gain();

        // Noise power (separate calculation, in dBm)
        //double noisePowerDbm = Noise_power_calculation(GS);

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
            String name = S.getMap_parametres_antennes().keySet().iterator().next();
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
        return 10.0 * Math.log10(noisePowerW * 1000.0);
    }
    private static double calculateSNR(Ground_station.GroundStation_physical GS,double  receivedPowerDbm) {
        double noisePowerDbm = Noise_power_calculation(GS);
        return receivedPowerDbm - noisePowerDbm;
    }

    private static double depointing_loss(double depointing,double teta3dB){
        return 12*(depointing/teta3dB);
    }



    public static class SignalCoding {
        private double Rs ;  // Symbol rate Mbit/s
        private double code_rate ;
        private double Rc;  // Code rate
        private double spectral_efficency = 0.7;
        private double Bandwidth;
        private String modulation;
        private double redundant_bits;
        private double M;   // Modulation order

        // Constructor to initialize calculated values
        public SignalCoding() {
            this.Rs=2.048;
            this.code_rate=7.0/8.0;
            this.Rc = Rs / code_rate;
            this.Bandwidth = Rc / spectral_efficency;
            this.modulation= "BPSK";
        }
        public SignalCoding(double Rs,double code_rate,String modulation) {
            this.Rs=Rs;
            this.code_rate=code_rate;
            this.Rc = Rs / code_rate;
            this.Bandwidth = Rc / spectral_efficency;
            this.modulation=modulation;
        }

        // Getter for Bandwidth
        public double getBandwidth() {
            return Bandwidth;
        }

        // Map: Modulation Type → (BER Level → Ec/N0 value)
        private static final Map<String, Map<String, Double>> MODULATION_BER_TABLE = Map.ofEntries(
                Map.entry("BPSK", Map.ofEntries(
                        Map.entry("BEP_1E_3", 6.8),
                        Map.entry("BEP_1E_4", 8.4),
                        Map.entry("BEP_1E_5", 9.6),
                        Map.entry("BEP_1E_6", 10.5),
                        Map.entry("BEP_1E_7", 11.3),
                        Map.entry("BEP_1E_8", 12.0),
                        Map.entry("BEP_1E_9", 12.6)
                )),
                Map.entry("QPSK", Map.ofEntries(
                        Map.entry("BEP_1E_3", 7.4),
                        Map.entry("BEP_1E_4", 8.8),
                        Map.entry("BEP_1E_5", 9.9),
                        Map.entry("BEP_1E_6", 10.8),
                        Map.entry("BEP_1E_7", 11.5),
                        Map.entry("BEP_1E_8", 12.2),
                        Map.entry("BEP_1E_9", 12.8)
                )),
                Map.entry("DE_BPSK", Map.ofEntries(
                        Map.entry("BEP_1E_3", 7.9),
                        Map.entry("BEP_1E_4", 9.3),
                        Map.entry("BEP_1E_5", 10.3),
                        Map.entry("BEP_1E_6", 11.2),
                        Map.entry("BEP_1E_7", 11.9),
                        Map.entry("BEP_1E_8", 12.5),
                        Map.entry("BEP_1E_9", 13.0)
                )),
                Map.entry("DE_QPSK", Map.ofEntries(
                        Map.entry("BEP_1E_3", 9.2),
                        Map.entry("BEP_1E_4", 10.7),
                        Map.entry("BEP_1E_5", 11.9),
                        Map.entry("BEP_1E_6", 12.8),
                        Map.entry("BEP_1E_7", 13.6),
                        Map.entry("BEP_1E_8", 14.3),
                        Map.entry("BEP_1E_9", 14.9)
                ))
        );

        // Get Required Ec/N0 for given modulation and BER
        public double getEcNo(String modulationType, String berLevel) {
            Map<String, Double> berMap = MODULATION_BER_TABLE.get(modulationType);
            if (berMap == null) {
                return -10.0;  // Modulation type not found
            }
            return berMap.getOrDefault(berLevel, -10.0);
        }

        // Get all available modulation types
        public Set<String> getAvailableModulations() {
            return MODULATION_BER_TABLE.keySet();
        }
    }
}
