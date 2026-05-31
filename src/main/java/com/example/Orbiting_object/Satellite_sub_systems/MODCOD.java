package com.example.Orbiting_object.Satellite_sub_systems;
import com.example.Ground_stations.Ground_station;
import com.example.Ground_stations.Satcom;
import com.example.Orbiting_object.Satellite;

import java.util.*;

public class MODCOD {

    /**
     * Modulation and Coding (MODCOD) schemes with required Eb/N0
     */
    public static class modcod {
        private String name;           // e.g., "QPSK 3/4"
        private String modulation;     // BPSK, QPSK, 16-QAM, 64-QAM
        private double codeRate;       // 1/2, 2/3, 3/4, 7/8
        private int bitsPerSymbol;     // 1, 2, 4, 6
        private double requiredEbNo_dB; // Required Eb/N0 for target BER
        private double spectralEfficiency; // bits/Hz

        public modcod(String name, String modulation, double codeRate,
                      int bitsPerSymbol, double requiredEbNo_dB) {
            this.name = name;
            this.modulation = modulation;
            this.codeRate = codeRate;
            this.bitsPerSymbol = bitsPerSymbol;
            this.requiredEbNo_dB = requiredEbNo_dB;
            // Spectral efficiency = bits/symbol × code rate
            this.spectralEfficiency = bitsPerSymbol * codeRate;
        }

        // Getters
        public String getName() { return name; }
        public String getModulation() { return modulation; }
        public double getCodeRate() { return codeRate; }
        public int getBitsPerSymbol() { return bitsPerSymbol; }
        public double getRequiredEbNo_dB() { return requiredEbNo_dB; }
        public double getSpectralEfficiency() { return spectralEfficiency; }

        @Override
        public String toString() {
            return String.format("%s (Eb/N0: %.1f dB, η: %.2f bits/Hz)",
                    name, requiredEbNo_dB, spectralEfficiency);
        }
    }

    /**
     * Pre-configured MODCOD schemes (from Satcom.SignalCoding table)
     */
    public static class MODCODLibrary {
        private static final List<modcod> modcods = Arrays.asList(
                // BPSK schemes
                new modcod("BPSK 1/2", "BPSK", 0.5, 1, 5.0),
                new modcod("BPSK 2/3", "BPSK", 2.0/3.0, 1, 5.5),
                new modcod("BPSK 3/4", "BPSK", 0.75, 1, 5.9),
                new modcod("BPSK 7/8", "BPSK", 7.0/8.0, 1, 6.9),

                // QPSK schemes
                new modcod("QPSK 1/2", "QPSK", 0.5, 2, 5.0),
                new modcod("QPSK 2/3", "QPSK", 2.0/3.0, 2, 5.5),
                new modcod("QPSK 3/4", "QPSK", 0.75, 2, 5.9),
                new modcod("QPSK 7/8", "QPSK", 7.0/8.0, 2, 6.9),

                // 16-QAM schemes
                new modcod("16-QAM 1/2", "16-QAM", 0.5, 4, 8.4),
                new modcod("16-QAM 2/3", "16-QAM", 2.0/3.0, 4, 9.3),
                new modcod("16-QAM 3/4", "16-QAM", 0.75, 4, 10.2),
                new modcod("16-QAM 7/8", "16-QAM", 7.0/8.0, 4, 11.2),

                // 64-QAM schemes
                new modcod("64-QAM 2/3", "64-QAM", 2.0/3.0, 6, 12.6),
                new modcod("64-QAM 3/4", "64-QAM", 0.75, 6, 13.5),
                new modcod("64-QAM 7/8", "64-QAM", 7.0/8.0, 6, 14.5)
        );

        /**
         * Get all available MODCODs
         */
        public static List<modcod> getAllMODCODs() {
            return new ArrayList<>(modcods);
        }

        /**
         * Get MODCOD by name
         */
        public static modcod getMODCOD(String name) {
            return modcods.stream()
                    .filter(m -> m.getName().equals(name))
                    .findFirst()
                    .orElse(null);
        }

        /**
         * Get best MODCOD for given SNR (highest spectral efficiency achievable)
         */
        public static modcod getBestMODCOD(double availableSNR_dB) {
            // SNR = Eb/N0 + 10*log10(bits/symbol * code rate)
            // Rearrange: Eb/N0 = SNR - 10*log10(spectral efficiency)
            modcod best = null;
            for (modcod modcod : modcods) {
                double requiredSNR = modcod.getRequiredEbNo_dB() +
                        10 * Math.log10(modcod.getSpectralEfficiency());

                if (availableSNR_dB >= requiredSNR) {
                    // Can use this MODCOD
                    if (best == null || modcod.getSpectralEfficiency() > best.getSpectralEfficiency()) {
                        best = modcod;
                    }
                }
            }
            return best;
        }

    }

    /**
     * Calculate downlink data rate
     */
    public static class DataRateCalculator {
        private double snr_dB;                    // SNR from link budget
        private double bandwidth_MHz;             // Available bandwidth
        private modcod selectedModcod;           // Selected MODCOD scheme
        private double symbolRate_MHz;           // Symbol rate (bandwidth / (1 + roll-off))
        private double rollOffFactor;            // Typically 0.2 - 0.35
        private double dataRate_Mbps;            // Calculated data rate

        public DataRateCalculator(double snr_dB, double bandwidth_MHz) {
            this.snr_dB = snr_dB;
            this.bandwidth_MHz = bandwidth_MHz;
            this.rollOffFactor = 0.25;  // 25% roll-off

            // Calculate symbol rate
            this.symbolRate_MHz = bandwidth_MHz / (1.0 + rollOffFactor);

            // Select best MODCOD for this SNR
            this.selectedModcod = MODCODLibrary.getBestMODCOD(snr_dB);

            // Calculate data rate
            calculateDataRate();
        }

        /**
         * Calculate data rate = symbol rate × bits/symbol × code rate
         */
        private void calculateDataRate() {
            if (selectedModcod == null) {
                this.dataRate_Mbps = 0;
                return;
            }

            // Data Rate = Symbol Rate × Bits per Symbol × Code Rate
            this.dataRate_Mbps = symbolRate_MHz * selectedModcod.getBitsPerSymbol()
                    * selectedModcod.getCodeRate();
        }

        // Getters
        public double getSNR_dB() { return snr_dB; }
        public double getBandwidth_MHz() { return bandwidth_MHz; }
        public modcod getSelectedModcod() { return selectedModcod; }
        public double getSymbolRate_MHz() { return symbolRate_MHz; }
        public double getRollOffFactor() { return rollOffFactor; }
        public double getDataRate_Mbps() { return dataRate_Mbps; }

        /**
         * Get data rate in MB/s
         */
        public double getDataRate_MBps() {
            return dataRate_Mbps / 8.0;
        }

        /**
         * Get Shannon capacity (theoretical maximum)
         */
        public double getShannonCapacity_Mbps() {
            double snr_linear = Math.pow(10.0, snr_dB / 10.0);
            return bandwidth_MHz * Math.log(1.0 + snr_linear) / Math.log(2.0);
        }

        /**
         * Print detailed analysis
         */
        public void printAnalysis() {
            System.out.println("\n" + "=".repeat(80));
            System.out.println("DOWNLINK DATA RATE ANALYSIS");
            System.out.println("=".repeat(80));
            System.out.printf("SNR: %.2f dB%n", snr_dB);
            System.out.printf("Available Bandwidth: %.2f MHz%n", bandwidth_MHz);
            System.out.printf("Symbol Rate (25%% roll-off): %.2f MHz%n", symbolRate_MHz);
            System.out.println();

            if (selectedModcod != null) {
                System.out.println("SELECTED MODCOD:");
                System.out.printf("  Scheme: %s%n", selectedModcod.getName());
                System.out.printf("  Required Eb/N0: %.2f dB%n", selectedModcod.getRequiredEbNo_dB());
                System.out.printf("  Spectral Efficiency: %.3f bits/Hz%n", selectedModcod.getSpectralEfficiency());
                System.out.println();
                System.out.printf("ACHIEVED DATA RATE: %.2f Mbps (%.2f MB/s)%n",
                        dataRate_Mbps, getDataRate_MBps());
            } else {
                System.out.println("NO VIABLE MODCOD: SNR too low!");
                System.out.printf("Minimum SNR needed: %.2f dB%n",
                        MODCODLibrary.getAllMODCODs().get(0).getRequiredEbNo_dB());
            }

            System.out.printf("Shannon Capacity (theoretical max): %.2f Mbps%n", getShannonCapacity_Mbps());
            System.out.println("=".repeat(80) + "\n");
        }
    }


    /**
     * Calculate transmission time for given data volume
     */
    public static double calculateTransmissionTime_sec(
            double dataVolume_MB,
            double dataRate_Mbps) {

        if (dataRate_Mbps <= 0) return Double.POSITIVE_INFINITY;

        // Time = Data Volume × 8 bits/byte / Data Rate
        return (dataVolume_MB * 8.0) / dataRate_Mbps;
    }

    /**
     * Check if all data can be transmitted in visibility window
     */
    public static boolean canTransmitAllData(
            double dataVolume_MB,
            double visibilityDuration_sec,
            double dataRate_Mbps) {

        double transmissionTime = calculateTransmissionTime_sec(dataVolume_MB, dataRate_Mbps);
        return transmissionTime <= visibilityDuration_sec;
    }

    /**
     * Calculate how much data can be transmitted in visibility window
     */
    public static double calculateTransmittableData_MB(
            double visibilityDuration_sec,
            double dataRate_Mbps) {

        // Data = Data Rate × Time / 8 (convert bits to bytes)
        return (dataRate_Mbps * visibilityDuration_sec) / 8.0;
    }
}
