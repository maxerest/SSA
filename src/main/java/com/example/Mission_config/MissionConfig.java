package com.example.Mission_config;

import org.orekit.time.AbsoluteDate;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of the mission configuration produced by the configurator UI.
 * Passed from ConfiguratorUI → App.main() before propagation starts.
 */
public class MissionConfig {

    // ── Mission-level flags ────────────────────────────────────────────────
    public final boolean satcomEnabled;
    public final boolean eoDetectionEnabled;

    // ── Simulation epoch ───────────────────────────────────────────────────
    public final AbsoluteDate epochIso;   // ISO-8601, e.g. "2025-06-01T00:00:00Z"

    // ── Satellites ─────────────────────────────────────────────────────────
    public final List<SatConfig> satellites;

    public MissionConfig(boolean satcomEnabled,
                         boolean eoDetectionEnabled,
                         AbsoluteDate  epochIso,
                         List<SatConfig> satellites) {
        this.satcomEnabled      = satcomEnabled;
        this.eoDetectionEnabled = eoDetectionEnabled;
        this.epochIso           = epochIso;
        this.satellites         = List.copyOf(satellites);
    }

    // ──────────────────────────────────────────────────────────────────────
    public static class SatConfig {

        // Identity
        public final String name;

        // Orbital elements
        public final double mass;           // kg
        public final double semiAxis;       // m  (Earth radius + altitude)
        public final double eccentricity;
        public final double inclination;    // rad
        public final double raan;           // rad
        public final double argPerigee;     // rad
        public final double trueAnomaly;    // rad

        // Sub-systems — key = category label (e.g. "PROPULSION"), value = component name
        // Empty string means "none selected" for that category.
        public final Map<String, String> subsystems;

        public SatConfig(String name,
                         double mass, double semiAxis, double eccentricity,
                         double inclination, double raan, double argPerigee, double trueAnomaly,
                         Map<String, String> subsystems) {
            this.name         = name;
            this.mass         = mass;
            this.semiAxis     = semiAxis;
            this.eccentricity = eccentricity;
            this.inclination  = inclination;
            this.raan         = raan;
            this.argPerigee   = argPerigee;
            this.trueAnomaly  = trueAnomaly;
            this.subsystems   = Map.copyOf(subsystems);
        }

        /** Convenience: retrieve the selected motor name (PROPULSION category). */
        public String motorName() {
            return subsystems.getOrDefault("PROPULSION", "");
        }

        @Override
        public String toString() {
            return String.format(
                    "SatConfig{name='%s', h=%.0f km, i=%.2f deg, subsystems=%s}",
                    name,
                    (semiAxis - 6_378_137.0) / 1_000.0,
                    Math.toDegrees(inclination),
                    subsystems
            );
        }
    }

    @Override
    public String toString() {
        return String.format("MissionConfig{satcom=%b, eo=%b, epoch='%s', sats=%d}",
                satcomEnabled, eoDetectionEnabled, epochIso, satellites.size());
    }
}