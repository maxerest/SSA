package com.example.Orbiting_object;
import com.example.Parametres;
import org.orekit.frames.FramesFactory;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.Constants;
import org.orekit.orbits.KeplerianOrbit;

import org.orekit.orbits.CartesianOrbit;

public class Orbiting_object {
    // Definiton parametres orbitaux
    protected String nom_sat;
    private double mass;
    private double semi_axis;
    private double eccentricity;
    private double inclinaison;
    private double long_noeud_ascendant;
    private double arg_periastre;
    private double anomalie;
    private PositionAngleType type_anomalie;
    private Orbit orbit_kepl;
    private Orbit orbit_cart;
    private Double Detectionaltitude =Constants.WGS84_EARTH_EQUATORIAL_RADIUS +100e6;
    //Inital state of the satellite
    private SpacecraftState s_initialState;

    protected Orbiting_object(Builder builder) {
        this.nom_sat = builder.nom_sat;
        this.mass = builder.mass;
        this.semi_axis = builder.semi_axis;
        this.eccentricity = builder.eccentricity;
        this.inclinaison = builder.inclinaison;
        this.long_noeud_ascendant = builder.long_noeud_ascendant;
        this.arg_periastre = builder.arg_periastre;
        this.anomalie = builder.anomalie;
        this.type_anomalie = builder.type_anomalie;
        this.Detectionaltitude = builder.Detectionaltitude;
        this.orbit_kepl=new KeplerianOrbit(
                this.semi_axis,
                this.eccentricity,
                this.inclinaison,
                this.long_noeud_ascendant,
                this.arg_periastre,
                this.anomalie,
                this.type_anomalie,
                FramesFactory.getEME2000(),
                Parametres.date_orekit,
                Constants.EGM96_EARTH_MU);
        this.orbit_cart= new CartesianOrbit(orbit_kepl);
        this.s_initialState=builder.s_initialState;
    }
    // Builder class
    public static class Builder {
        protected String nom_sat = "Placeholder";
        private double mass = 2500;
        private double semi_axis = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 700e3;
        private double eccentricity = 0.001;
        private double inclinaison = Math.toRadians(45);
        private double long_noeud_ascendant = Math.toRadians(30);
        private double arg_periastre = Math.toRadians(45);
        private double anomalie = Math.toRadians(60);
        private PositionAngleType type_anomalie = PositionAngleType.MEAN;
        public Double Detectionaltitude = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 100000e3;
        private SpacecraftState s_initialState;


        // Builder methods
        public Builder nom_sat(String name) { this.nom_sat = name; return this; }
        public Builder mass(double mass) { this.mass = mass; return this; }
        public Builder semi_axis(double sa) { this.semi_axis = sa; return this; }
        public Builder eccentricity(double e) { this.eccentricity = e; return this; }
        public Builder inclinaison(double i) { this.inclinaison = i; return this; }
        public Builder long_noeud_ascendant(double lna) { this.long_noeud_ascendant = lna; return this; }
        public Builder arg_periastre(double arg) { this.arg_periastre = arg; return this; }
        public Builder anomalie(double a) { this.anomalie = a; return this; }
        public Builder type_anomalie(PositionAngleType t) { this.type_anomalie = t; return this; }
        public Builder Detectionaltitude(Double d) { this.Detectionaltitude = d; return this; }
        public Builder s_initialState(SpacecraftState s) { this.s_initialState = s; return this; }
        public Orbiting_object build() { return new Orbiting_object(this); }
    }

    public Orbit get_keplerian_Orbit(){
        return orbit_kepl;
    }
    public Orbit get_Cartesian_Orbit(){return orbit_cart;}

    public double get_mass(){
        return mass;
    }
    public double get_semi_axis(){
        return semi_axis;
    }
    public double get_eccentricity(){
        return eccentricity;
    }
    public double get_inclinaison(){
        return inclinaison;
    }
    public double get_long_noeud_ascendant(){
        return long_noeud_ascendant;
    }
    public double get_arg_periastre(){
        return arg_periastre;
    }
    public double get_anomalie(){
        return anomalie;
    }
    public PositionAngleType get_type_anomalie(){
        return type_anomalie;
    }
    public String get_Name(){
        return nom_sat;
    }
    public double get_Mass(){
        return mass;
    }
    public Double get_Detectionaltitude(){
        return Detectionaltitude;
    }
    public SpacecraftState get_s_initialState(){return s_initialState;}
}
