package com.example;

import java.util.Date;

import org.hipparchus.util.FastMath;
import org.orekit.attitudes.Attitude;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.bodies.BodyShape;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.TopocentricFrame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;

import com.example.Manoeuvre.Manoeuvre;

public class Parametres
{   
    public String nom_sat;
    //Gestion temps
    public static AbsoluteDate date_orekit = new AbsoluteDate(new Date(), TimeScalesFactory.getUTC()).shiftedBy(2*3600);
    public static double duration =Constants.JULIAN_DAY-(3600*23); //Constants.JULIAN_DAY;

    // définition de la Terre
    public static Frame frame = FramesFactory.getEME2000();
    public static double mu = Constants.EIGEN5C_EARTH_MU;
    public static BodyShape earth = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,Constants.WGS84_EARTH_FLATTENING,FramesFactory.getITRF(IERSConventions.IERS_2010, true));
    
    // Ground Station elevation limit
    public static double elevation =  FastMath.toRadians(40.0);

    // Definiton parametres orbitaux
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
    //Defintion manoeuvre
    private int type_moteur;
    private double start_manoeuvre;
    private double duration_manoeuvre;
    public Manoeuvre manoeuvre;
    // Parametres satellite
    private double area;    // m^2
    private double cd ;
    private double srpCrossSection;   // m²
    private double srpCoeff;
        
    //Seul de detection altitude
    public Double Detectionaltitude =Constants.WGS84_EARTH_EQUATORIAL_RADIUS +100000e3;
       
    //Inital state of the satellite
    public SpacecraftState s_initialState; 

  
    private Parametres(Builder builder) {
        this.nom_sat = builder.nom_sat;
        this.mass = builder.mass;
        this.semi_axis = builder.semi_axis;
        this.eccentricity = builder.eccentricity;
        this.inclinaison = builder.inclinaison;
        this.long_noeud_ascendant = builder.long_noeud_ascendant;
        this.arg_periastre = builder.arg_periastre;
        this.anomalie = builder.anomalie;
        this.type_anomalie = builder.type_anomalie;
        this.type_moteur = builder.type_moteur;
        this.start_manoeuvre = builder.start_manoeuvre;
        this.duration_manoeuvre = builder.duration_manoeuvre;
        this.area = builder.area;
        this.cd = builder.cd;
        this.srpCrossSection = builder.srpCrossSection;
        this.srpCoeff = builder.srpCoeff;
        this.Detectionaltitude = builder.Detectionaltitude;
        orbit_kepl = new KeplerianOrbit(this.semi_axis, this.eccentricity, this.inclinaison,  this.long_noeud_ascendant,  this.arg_periastre, this.anomalie, this.type_anomalie, frame, date_orekit, mu);
        orbit_cart =new CartesianOrbit(orbit_kepl);
        s_initialState = new SpacecraftState(orbit_kepl).withMass(this.mass);
        this.manoeuvre=new Manoeuvre.Builder().firingDate(date_orekit.shiftedBy(this.start_manoeuvre)).duration(this.duration_manoeuvre).build();
    }

    // Builder class
    public static class Builder {
        private String nom_sat ="Placeholder";
        private double mass = 2500;
        private double semi_axis = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 700e3;
        private double eccentricity = 0.001;
        private double inclinaison = Math.toRadians(45);
        private double long_noeud_ascendant = Math.toRadians(30);
        private double arg_periastre = Math.toRadians(45);
        private double anomalie = Math.toRadians(60);
        private PositionAngleType type_anomalie = PositionAngleType.MEAN;
        private int type_moteur = 1;
        private double start_manoeuvre = 300;
        private double duration_manoeuvre = 360;
        private double area = 1.0;
        private double cd = 2.2;
        private double srpCrossSection = 2;
        private double srpCoeff = 1.30;
        public Double Detectionaltitude = Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 100000e3;
        
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
        public Builder type_moteur(int t) { this.type_moteur = t; return this; }
        public Builder start_manoeuvre(double s) { this.start_manoeuvre = s; return this; }
        public Builder duration_manoeuvre(double d) { this.duration_manoeuvre = d; return this; }
        public Builder area(double a) { this.area = a; return this; }
        public Builder cd(double c) { this.cd = c; return this; }
        public Builder srpCrossSection(double s) { this.srpCrossSection = s; return this; }
        public Builder srpCoeff(double s) { this.srpCoeff = s; return this; }
        public Builder Detectionaltitude(Double d) { this.Detectionaltitude = d; return this; }

        public Parametres build() { return new Parametres(this); }
    }

    public Orbit get_keplerian_Orbit(){
        return orbit_kepl;
    }
    public Orbit get_Cartesian_Orbit(){
        return orbit_cart;
    }
    public AbsoluteDate get_Date(){
        return date_orekit;
    }
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
    public int get_Type_moteur(){
        return type_moteur;
    }
    public double get_de(){
        return start_manoeuvre;
    }
    public double get_duration_manoeuvre(){
        return duration_manoeuvre;
    }
    public double get_area(){
        return area;
    }
    public double get_cd(){
        return cd;
    }
    public double get_srpCrossSection(){
        return srpCrossSection;
    }
    public double get_srpCoeff(){
        return srpCoeff;
    }
}
